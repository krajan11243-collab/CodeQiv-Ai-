package com.tom.rv2ide.buildengine.steps

import com.tom.rv2ide.buildengine.model.BuildError
import com.tom.rv2ide.buildengine.tools.BuildToolsLocator
import com.tom.rv2ide.buildengine.tools.ProcessRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Copies aapt2's resources.apk to a working unsigned.apk and stuffs classes.dex + assets in. */
class ApkAssembleStep {
  fun assemble(resApk: File, dexDir: File, assetsDirs: List<File>, outUnsignedApk: File): Boolean {
    resApk.copyTo(outUnsignedApk, overwrite = true)

    val dexFiles = dexDir.listFiles { f -> f.extension == "dex" }?.sortedBy { it.name } ?: emptyList()
    if (dexFiles.isEmpty()) return false

    // Append dex + asset entries via a fresh zip pass (simplest correct approach;
    // Phase 2 can switch to streaming/incremental packaging for speed).
    val tmp = File(outUnsignedApk.parentFile, "${outUnsignedApk.name}.tmp")
    ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
      ZipFile(outUnsignedApk).use { zip ->
        zip.entries().asSequence().forEach { entry ->
          zos.putNextEntry(ZipEntry(entry.name))
          zip.getInputStream(entry).copyTo(zos)
          zos.closeEntry()
        }
      }
      dexFiles.forEach { dex ->
        zos.putNextEntry(ZipEntry(dex.name))
        dex.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
      }
      assetsDirs.forEach { assetsDir ->
        if (!assetsDir.exists()) return@forEach
        assetsDir.walkTopDown().filter { it.isFile }.forEach { asset ->
          val rel = "assets/" + asset.relativeTo(assetsDir).path.replace(File.separatorChar, '/')
          zos.putNextEntry(ZipEntry(rel))
          asset.inputStream().use { it.copyTo(zos) }
          zos.closeEntry()
        }
      }
    }
    tmp.copyTo(outUnsignedApk, overwrite = true)
    tmp.delete()
    return true
  }
}

/** `zipalign -f -p 4 unsigned.apk aligned.apk` */
class ZipAlignStep(private val tools: BuildToolsLocator) {
  fun align(inApk: File, outApk: File, cancelFlag: () -> Boolean, onOutputLine: (String) -> Unit): Boolean {
    val args = listOf("-f", "-p", "4", inApk.absolutePath, outApk.absolutePath)
    val outcome = ProcessRunner.runExecutable(tools.zipalign, args, inApk.parentFile!!, cancelFlag, onOutputLine)
    return outcome.exitCode == 0 && outApk.exists()
  }
}

/** `apksigner sign --ks debug.keystore --out signed.apk aligned.apk` */
class ApkSignStep(private val tools: BuildToolsLocator, private val javaBin: File) {
  data class Result(val success: Boolean, val errors: List<BuildError>)

  fun sign(
    inApk: File,
    outApk: File,
    keystore: File,
    keystorePass: String,
    keyAlias: String,
    keyPass: String,
    cancelFlag: () -> Boolean,
    onOutputLine: (String) -> Unit
  ): Result {
    val args = listOf(
      "sign",
      "--ks", keystore.absolutePath,
      "--ks-pass", "pass:$keystorePass",
      "--ks-key-alias", keyAlias,
      "--key-pass", "pass:$keyPass",
      "--out", outApk.absolutePath,
      inApk.absolutePath
    )
    val errors = mutableListOf<BuildError>()
    val outcome = ProcessRunner.runJar(
      javaBin = javaBin,
      jar = tools.apksignerJar,
      mainClassArgs = args,
      workingDir = inApk.parentFile!!,
      cancelFlag = cancelFlag,
      onLine = { line ->
        onOutputLine(line)
        if (line.contains("ERROR", ignoreCase = true)) {
          errors.add(BuildError(null, null, null, line.trim(), BuildError.Severity.ERROR, "apksigner"))
        }
      }
    )
    return Result(outcome.exitCode == 0 && outApk.exists(), errors)
  }
}
