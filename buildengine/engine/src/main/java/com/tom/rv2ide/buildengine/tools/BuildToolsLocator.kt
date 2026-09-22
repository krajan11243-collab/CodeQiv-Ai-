package com.tom.rv2ide.buildengine.tools

import java.io.File

/**
 * Resolves paths to the bundled, offline build tools instead of relying on
 * a full Android SDK install. Tools live under:
 *
 *   <filesDir>/build-tools/
 *     ecj.jar
 *     d8.jar
 *     android.jar
 *     aapt2               (native executable, per-ABI)
 *     zipalign             (native executable, per-ABI)
 *     apksigner.jar
 *     kotlin/  (kotlinc-jvm distribution, only pulled in if a project has .kt files)
 *
 * Tools are shipped as a small "core" package (ecj + d8 + aapt2 + zipalign +
 * apksigner + android.jar, a few MB) inside the APK's assets and unpacked to
 * [rootDir] on first run. Kotlin support is a separate, optional
 * downloadable/unpackable component (kotlinc alone is 40-60MB) so a plain
 * Java project never has to fetch it — this satisfies requirement #12
 * (no forced huge SDK download).
 */
class BuildToolsLocator(private val rootDir: File) {

  private val buildToolsDir = File(rootDir, "build-tools")

  val ecjJar: File get() = File(buildToolsDir, "ecj.jar")
  val d8Jar: File get() = File(buildToolsDir, "d8.jar")
  val androidJar: File get() = File(buildToolsDir, "android.jar")
  val apksignerJar: File get() = File(buildToolsDir, "apksigner.jar")
  val aapt2: File get() = File(buildToolsDir, "aapt2")
  val zipalign: File get() = File(buildToolsDir, "zipalign")
  val kotlincHome: File get() = File(buildToolsDir, "kotlin")
  val kotlincScript: File get() = File(kotlincHome, "bin/kotlinc")

  fun coreToolsPresent(): Boolean =
    ecjJar.exists() && d8Jar.exists() && androidJar.exists() &&
      apksignerJar.exists() && aapt2.exists() && zipalign.exists()

  fun kotlinToolsPresent(): Boolean = kotlincScript.exists()

  fun missingCoreTools(): List<String> = buildList {
    if (!ecjJar.exists()) add("ecj.jar")
    if (!d8Jar.exists()) add("d8.jar")
    if (!androidJar.exists()) add("android.jar")
    if (!apksignerJar.exists()) add("apksigner.jar")
    if (!aapt2.exists()) add("aapt2")
    if (!zipalign.exists()) add("zipalign")
  }

  /** Marks native binaries executable after first unpack (assets are
   *  extracted without the exec bit). Call once after provisioning. */
  fun ensureExecutable() {
    aapt2.setExecutable(true)
    zipalign.setExecutable(true)
    kotlincScript.takeIf { it.exists() }?.setExecutable(true)
  }
}
