package com.tom.rv2ide.buildengine.steps

import com.tom.rv2ide.buildengine.model.BuildError
import com.tom.rv2ide.buildengine.tools.BuildToolsLocator
import com.tom.rv2ide.buildengine.tools.ProcessRunner
import java.io.File

/**
 * Compiles Java sources with ecj.jar (org.eclipse.jdt.internal.compiler.batch.Main)
 * to .class files. ECJ is chosen over javac because it runs standalone on a
 * plain JVM without needing the full JDK's internal compiler modules —
 * important on Android/Termux-style local JVMs.
 */
class EcjCompileStep(private val tools: BuildToolsLocator, private val javaBin: File) {

  data class Result(val success: Boolean, val errors: List<BuildError>, val warnings: List<BuildError>)

  fun compile(
    sources: List<File>,
    classpath: List<File>,
    outDir: File,
    cancelFlag: () -> Boolean,
    onOutputLine: (String) -> Unit
  ): Result {
    if (sources.isEmpty()) return Result(true, emptyList(), emptyList())
    outDir.mkdirs()

    val sourceListFile = File.createTempFile("ecj-sources", ".txt").apply {
      writeText(sources.joinToString("\n") { it.absolutePath })
      deleteOnExit()
    }

    val args = listOf(
      "-nowarn:[deprecation]",
      "-1.8",
      "-proc:none",
      "-classpath", classpath.joinToString(File.pathSeparator) { it.absolutePath },
      "-d", outDir.absolutePath,
      "@${sourceListFile.absolutePath}"
    )

    val diagnostics = mutableListOf<BuildError>()
    val outcome = ProcessRunner.runJar(
      javaBin = javaBin,
      jar = tools.ecjJar,
      mainClassArgs = args,
      workingDir = outDir,
      cancelFlag = cancelFlag,
      onLine = { line ->
        onOutputLine(line)
        parseEcjLine(line)?.let { diagnostics.add(it) }
      }
    )

    val errors = diagnostics.filter { it.severity == BuildError.Severity.ERROR }
    val warnings = diagnostics.filter { it.severity == BuildError.Severity.WARNING }
    return Result(success = outcome.exitCode == 0 && errors.isEmpty(), errors = errors, warnings = warnings)
  }

  // ECJ diagnostic format:
  // 1. ERROR in /path/Foo.java (at line 12)
  //     someCode();
  //     ^^^^^^^^
  // The method someCode() is undefined for the type Foo
  private val headerRegex = Regex("""^\d+\.\s+(ERROR|WARNING)\s+in\s+(.+?)\s+\(at line\s+(\d+)\)""")
  private var pendingFile: File? = null
  private var pendingLine: Int? = null
  private var pendingSeverity: BuildError.Severity? = null

  private fun parseEcjLine(line: String): BuildError? {
    headerRegex.find(line)?.let { m ->
      pendingFile = File(m.groupValues[2])
      pendingLine = m.groupValues[3].toIntOrNull()
      pendingSeverity = if (m.groupValues[1] == "ERROR") BuildError.Severity.ERROR else BuildError.Severity.WARNING
      return null
    }
    // The message is the next non-caret, non-blank, non-"----" line after the header+snippet+carets.
    val file = pendingFile
    val ln = pendingLine
    val sev = pendingSeverity
    if (file != null && ln != null && sev != null &&
      line.isNotBlank() && !line.trimStart().startsWith("^") && !line.startsWith("----")
    ) {
      pendingFile = null; pendingLine = null; pendingSeverity = null
      return BuildError(file, ln, null, line.trim(), sev, "ecj")
    }
    return null
  }
}
