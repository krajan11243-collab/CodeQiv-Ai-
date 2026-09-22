package com.tom.rv2ide.buildengine.tools

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Requirement #12: don't force a huge SDK download on first app open.
 * Instead of bundling ecj.jar/d8.jar/aapt2/zipalign/apksigner.jar/android.jar
 * inside the APK (which would bloat every install even for users who never
 * build a project), fetch them lazily as one small "core tools" package
 * (~5-15MB) the first time the user actually starts a build. Kotlin support
 * stays a further separate, optional package (see [ToolPackage.KOTLIN]) so a
 * pure-Java project never pulls in the much larger kotlinc distribution.
 *
 * Distinct from Gradle's own dependency/plugin downloads: this fetches a
 * handful of fixed, versioned, checksummed static binaries once — no build
 * scripts are evaluated, no arbitrary remote code runs, and the user's
 * project source never leaves the device (requirement #13).
 */
class ToolsProvisioner(
  private val rootDir: File,
  private val locator: BuildToolsLocator
) {

  enum class ToolPackage(val id: String, val relativeDestDir: String) {
    /** ecj.jar, d8.jar, aapt2, zipalign, apksigner.jar, android.jar */
    CORE("core", "build-tools"),
    /** kotlinc distribution — only needed once a project has .kt files */
    KOTLIN("kotlin", "build-tools/kotlin")
  }

  /**
   * Where each package is fetched from + its expected SHA-256, so a
   * corrupted or tampered download is rejected before extraction. These
   * must point at binaries you host yourself (e.g. attached to a GitHub
   * Release of this project) — Claude cannot host or pick a URL for you.
   */
  data class PackageManifestEntry(val downloadUrl: String, val sha256: String, val approxSizeMb: Int)

  interface ManifestProvider {
    fun manifestFor(pkg: ToolPackage): PackageManifestEntry
  }

  sealed class ProvisionEvent {
    data class Started(val pkg: ToolPackage, val approxSizeMb: Int) : ProvisionEvent()
    data class Progress(val pkg: ToolPackage, val bytesRead: Long, val totalBytes: Long) : ProvisionEvent()
    data class Extracting(val pkg: ToolPackage) : ProvisionEvent()
    data class Finished(val pkg: ToolPackage, val success: Boolean, val error: String? = null) : ProvisionEvent()
    object Cancelled : ProvisionEvent()
  }

  fun isProvisioned(pkg: ToolPackage): Boolean = when (pkg) {
    ToolPackage.CORE -> locator.coreToolsPresent()
    ToolPackage.KOTLIN -> locator.kotlinToolsPresent()
  }

  /**
   * Downloads + verifies + extracts [pkg]. Safe to call repeatedly — if
   * already provisioned it's a no-op. Runs on the calling thread; callers
   * should invoke from a background dispatcher (see Phase 3 wiring notes).
   */
  fun provision(
    pkg: ToolPackage,
    manifestProvider: ManifestProvider,
    cancelFlag: () -> Boolean,
    onEvent: (ProvisionEvent) -> Unit
  ) {
    if (isProvisioned(pkg)) {
      onEvent(ProvisionEvent.Finished(pkg, true))
      return
    }

    val entry = manifestProvider.manifestFor(pkg)
    onEvent(ProvisionEvent.Started(pkg, entry.approxSizeMb))

    val downloadTmp = File(rootDir, "download-${pkg.id}.zip.part")
    try {
      downloadTo(entry.downloadUrl, downloadTmp, cancelFlag) { read, total ->
        onEvent(ProvisionEvent.Progress(pkg, read, total))
      }
      if (cancelFlag()) {
        downloadTmp.delete()
        onEvent(ProvisionEvent.Cancelled)
        return
      }

      val actualSha256 = sha256Of(downloadTmp)
      if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
        downloadTmp.delete()
        onEvent(ProvisionEvent.Finished(pkg, false, "Checksum mismatch — download corrupted or tampered, aborted."))
        return
      }

      onEvent(ProvisionEvent.Extracting(pkg))
      val destDir = File(rootDir, pkg.relativeDestDir).apply { mkdirs() }
      extractZip(downloadTmp, destDir)
      locator.ensureExecutable()

      downloadTmp.delete()

      if (isProvisioned(pkg)) {
        onEvent(ProvisionEvent.Finished(pkg, true))
      } else {
        onEvent(ProvisionEvent.Finished(pkg, false, "Extraction finished but expected files are still missing."))
      }
    } catch (e: Exception) {
      downloadTmp.delete()
      onEvent(ProvisionEvent.Finished(pkg, false, e.message ?: "Unknown error"))
    }
  }

  private fun downloadTo(url: String, dest: File, cancelFlag: () -> Boolean, onProgress: (Long, Long) -> Unit) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 15_000
      instanceFollowRedirects = true
    }
    connection.connect()
    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
      "Download failed: HTTP ${connection.responseCode}"
    }
    val total = connection.contentLengthLong
    var readTotal = 0L
    connection.inputStream.use { input ->
      dest.outputStream().buffered().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          if (cancelFlag()) return
          val n = input.read(buffer)
          if (n < 0) break
          output.write(buffer, 0, n)
          readTotal += n
          onProgress(readTotal, total)
        }
      }
    }
  }

  private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun extractZip(zip: File, destDir: File) {
    ZipInputStream(zip.inputStream().buffered()).use { zis ->
      var entry: ZipEntry? = zis.nextEntry
      while (entry != null) {
        val outFile = File(destDir, entry.name)
        // Zip-slip guard: refuse entries that would escape destDir.
        check(outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator) || outFile.canonicalPath == destDir.canonicalPath) {
          "Unsafe zip entry path: ${entry.name}"
        }
        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          outFile.parentFile?.mkdirs()
          outFile.outputStream().use { output -> zis.copyTo(output) }
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
  }
}
