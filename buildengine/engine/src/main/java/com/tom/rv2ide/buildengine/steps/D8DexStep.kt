package com.tom.rv2ide.buildengine.steps

import com.tom.rv2ide.buildengine.model.BuildError
import com.tom.rv2ide.buildengine.tools.BuildToolsLocator
import com.tom.rv2ide.buildengine.tools.ProcessRunner
import java.io.File

/** Runs d8.jar (com.android.tools.r8.D8) to turn .class files + library jars into classes.dex. */
class D8DexStep(private val tools: BuildToolsLocator, private val javaBin: File) {

  data class Result(val success: Boolean, val errors: List<BuildError>)

  fun dex(
    classesDir: File,
    libraryJars: List<File>,
    minSdk: Int,
    outDir: File,
    cancelFlag: () -> Boolean,
    onOutputLine: (String) -> Unit
  ): Result {
    outDir.mkdirs()

    val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    if (classFiles.isEmpty()) {
      return Result(false, listOf(BuildError(null, null, null, "No compiled classes to dex.", BuildError.Severity.ERROR, "d8")))
    }

    val args = buildList {
      add("--min-api"); add(minSdk.toString())
      add("--output"); add(outDir.absolutePath)
      libraryJars.forEach { add("--lib"); add(it.absolutePath) }
      addAll(classFiles.map { it.absolutePath })
    }

    val errors = mutableListOf<BuildError>()
    val outcome = ProcessRunner.runJar(
      javaBin = javaBin,
      jar = tools.d8Jar,
      mainClassArgs = args,
      workingDir = outDir,
      cancelFlag = cancelFlag,
      onLine = { line ->
        onOutputLine(line)
        if (line.contains("Error", ignoreCase = false)) {
          errors.add(BuildError(null, null, null, line.trim(), BuildError.Severity.ERROR, "d8"))
        }
      }
    )

    return Result(outcome.exitCode == 0 && errors.isEmpty(), errors)
  }
}
