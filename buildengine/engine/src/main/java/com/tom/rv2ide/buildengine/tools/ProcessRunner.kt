package com.tom.rv2ide.buildengine.tools

import java.io.File
import java.util.concurrent.TimeUnit

data class ProcessOutcome(val exitCode: Int, val stdout: List<String>, val stderr: List<String>)

/**
 * Runs a command (either `java -cp tool.jar Main ...args` for JVM tools like
 * ecj/d8/apksigner, or a direct native executable like aapt2/zipalign) and
 * streams output back through [onLine]. Cancellable via [cancelFlag].
 */
object ProcessRunner {

  fun runJar(
    javaBin: File,
    jar: File,
    mainClassArgs: List<String>,
    extraJvmArgs: List<String> = emptyList(),
    workingDir: File,
    cancelFlag: () -> Boolean,
    onLine: (String) -> Unit
  ): ProcessOutcome {
    val cmd = buildList {
      add(javaBin.absolutePath)
      addAll(extraJvmArgs)
      add("-jar")
      add(jar.absolutePath)
      addAll(mainClassArgs)
    }
    return run(cmd, workingDir, cancelFlag, onLine)
  }

  fun runExecutable(
    executable: File,
    args: List<String>,
    workingDir: File,
    cancelFlag: () -> Boolean,
    onLine: (String) -> Unit
  ): ProcessOutcome {
    val cmd = listOf(executable.absolutePath) + args
    return run(cmd, workingDir, cancelFlag, onLine)
  }

  private fun run(
    cmd: List<String>,
    workingDir: File,
    cancelFlag: () -> Boolean,
    onLine: (String) -> Unit
  ): ProcessOutcome {
    val stdout = mutableListOf<String>()
    val stderr = mutableListOf<String>()

    val process = ProcessBuilder(cmd)
      .directory(workingDir)
      .redirectErrorStream(false)
      .start()

    val outThread = Thread {
      process.inputStream.bufferedReader().forEachLine {
        stdout.add(it)
        onLine(it)
      }
    }.apply { start() }

    val errThread = Thread {
      process.errorStream.bufferedReader().forEachLine {
        stderr.add(it)
        onLine(it)
      }
    }.apply { start() }

    // Poll so we can honor cancellation instead of blocking forever on waitFor().
    while (process.isAlive) {
      if (cancelFlag()) {
        process.destroyForcibly()
        break
      }
      process.waitFor(200, TimeUnit.MILLISECONDS)
    }

    outThread.join(2000)
    errThread.join(2000)

    return ProcessOutcome(
      exitCode = if (process.isAlive) -1 else process.exitValue(),
      stdout = stdout,
      stderr = stderr
    )
  }
}
