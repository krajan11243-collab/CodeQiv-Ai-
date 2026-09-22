package com.tom.rv2ide.buildengine.steps

import com.tom.rv2ide.buildengine.model.BuildError
import com.tom.rv2ide.buildengine.tools.BuildToolsLocator
import com.tom.rv2ide.buildengine.tools.ProcessRunner
import java.io.File

/**
 * Two-phase aapt2 usage:
 *  1. `aapt2 compile` each res/ dir into intermediate .flat files.
 *  2. `aapt2 link` those .flat files + AndroidManifest.xml + android.jar
 *     into resources.apk (which already contains resources.arsc, the
 *     manifest, and R.java — the base APK shell before dex is added).
 */
class Aapt2Step(private val tools: BuildToolsLocator) {

  data class Result(val success: Boolean, val errors: List<BuildError>, val resApk: File?, val rJavaDir: File?)

  fun run(
    resDirs: List<File>,
    manifest: File,
    androidJar: File,
    extraPackagesFromAars: List<String>,
    intermediatesDir: File,
    outApk: File,
    cancelFlag: () -> Boolean,
    onOutputLine: (String) -> Unit
  ): Result {
    val flatDir = File(intermediatesDir, "flat").apply { mkdirs() }
    val rJavaDir = File(intermediatesDir, "r-java").apply { mkdirs() }
    val errors = mutableListOf<BuildError>()

    // Phase 1: compile
    for (resDir in resDirs) {
      if (!resDir.exists()) continue
      val args = listOf("compile", "--dir", resDir.absolutePath, "-o", flatDir.absolutePath)
      val outcome = ProcessRunner.runExecutable(tools.aapt2, args, intermediatesDir, cancelFlag) { line ->
        onOutputLine(line)
        parseAapt2Line(line)?.let { errors.add(it) }
      }
      if (outcome.exitCode != 0) {
        return Result(false, errors.ifEmpty {
          listOf(BuildError(resDir, null, null, "aapt2 compile failed for $resDir", BuildError.Severity.ERROR, "aapt2"))
        }, null, null)
      }
    }

    // Phase 2: link
    val flatFiles = flatDir.listFiles { f -> f.extension == "flat" }?.toList() ?: emptyList()
    val linkArgs = buildList {
      add("link")
      add("-o"); add(outApk.absolutePath)
      add("--manifest"); add(manifest.absolutePath)
      add("-I"); add(androidJar.absolutePath)
      add("--java"); add(rJavaDir.absolutePath)
      add("--auto-add-overlay")
      extraPackagesFromAars.forEach { add("--extra-packages"); add(it) }
      flatFiles.forEach { add(it.absolutePath) }
    }

    val outcome = ProcessRunner.runExecutable(tools.aapt2, linkArgs, intermediatesDir, cancelFlag) { line ->
      onOutputLine(line)
      parseAapt2Line(line)?.let { errors.add(it) }
    }

    return Result(
      success = outcome.exitCode == 0 && outApk.exists(),
      errors = errors,
      resApk = outApk.takeIf { it.exists() },
      rJavaDir = rJavaDir
    )
  }

  // aapt2 diagnostic format: path/res/values/strings.xml:12: error: ...
  private val lineRegex = Regex("""^(.+?):(\d+):\s+(error|warning):\s+(.*)$""")

  private fun parseAapt2Line(line: String): BuildError? {
    val m = lineRegex.find(line) ?: return null
    val (file, ln, sev, msg) = m.destructured
    return BuildError(
      file = File(file),
      line = ln.toIntOrNull(),
      column = null,
      message = msg,
      severity = if (sev == "error") BuildError.Severity.ERROR else BuildError.Severity.WARNING,
      source = "aapt2"
    )
  }
}
