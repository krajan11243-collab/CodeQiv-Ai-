package com.tom.rv2ide.buildengine.steps

import com.tom.rv2ide.buildengine.model.BuildError
import com.tom.rv2ide.buildengine.tools.BuildToolsLocator
import com.tom.rv2ide.buildengine.tools.ProcessRunner
import java.io.File

/**
 * Compiles Kotlin sources with the bundled kotlinc distribution. Kept as a
 * fully separate, optional step: a pure-Java project never touches this
 * (satisfies requirement #12 — no forced huge download for tools it doesn't
 * need, since kotlinc alone is far bigger than ecj+d8+aapt2 combined).
 *
 * Output .class files land in the same outDir as ECJ's, then both are fed
 * to D8 together (Java/Kotlin interop within one module).
 */
class KotlincCompileStep(private val tools: BuildToolsLocator) {

  data class Result(val success: Boolean, val errors: List<BuildError>, val warnings: List<BuildError>)

  fun compile(
    sources: List<File>,
    classpath: List<File>,
    outDir: File,
    cancelFlag: () -> Boolean,
    onOutputLine: (String) -> Unit
  ): Result {
    if (sources.isEmpty()) return Result(true, emptyList(), emptyList())
    if (!tools.kotlinToolsPresent()) {
      onOutputLine("Kotlin sources found but Kotlin build tools are not installed.")
      return Result(
        success = false,
        errors = listOf(
          BuildError(
            null, null, null,
            "Kotlin compiler not installed — install the Kotlin build-tools component to compile .kt files.",
            BuildError.Severity.ERROR, "kotlinc"
          )
        ),
        warnings = emptyList()
      )
    }
    outDir.mkdirs()

    val args = listOf(
      "-cp", classpath.joinToString(File.pathSeparator) { it.absolutePath },
      "-d", outDir.absolutePath,
      "-jvm-target", "1.8"
    ) + sources.map { it.absolutePath }

    val diagnostics = mutableListOf<BuildError>()
    val outcome = ProcessRunner.runExecutable(
      executable = tools.kotlincScript,
      args = args,
      workingDir = outDir,
      cancelFlag = cancelFlag,
      onLine = { line ->
        onOutputLine(line)
        parseKotlincLine(line)?.let { diagnostics.add(it) }
      }
    )

    val errors = diagnostics.filter { it.severity == BuildError.Severity.ERROR }
    val warnings = diagnostics.filter { it.severity == BuildError.Severity.WARNING }
    return Result(success = outcome.exitCode == 0 && errors.isEmpty(), errors = errors, warnings = warnings)
  }

  // kotlinc diagnostic format:
  // /path/Foo.kt:12:5: error: unresolved reference: someCode
  private val lineRegex = Regex("""^(.+\.kt):(\d+):(\d+):\s+(error|warning):\s+(.*)$""")

  private fun parseKotlincLine(line: String): BuildError? {
    val m = lineRegex.find(line) ?: return null
    val (file, ln, col, sev, msg) = m.destructured
    return BuildError(
      file = File(file),
      line = ln.toIntOrNull(),
      column = col.toIntOrNull(),
      message = msg,
      severity = if (sev == "error") BuildError.Severity.ERROR else BuildError.Severity.WARNING,
      source = "kotlinc"
    )
  }
}
