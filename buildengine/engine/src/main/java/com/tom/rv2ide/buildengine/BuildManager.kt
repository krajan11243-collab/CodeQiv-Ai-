package com.tom.rv2ide.buildengine

import com.tom.rv2ide.buildengine.classpath.ClasspathCollector
import com.tom.rv2ide.buildengine.model.*
import com.tom.rv2ide.buildengine.steps.*
import com.tom.rv2ide.buildengine.tools.BuildToolsLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local, offline replacement for GradleBuildService/GradleBuildServiceConnnection.
 * UI layer (RunTasksViewModel etc.) should depend on this interface only, so
 * swapping the engine or adding more build steps later doesn't touch the UI
 * (requirement #10 — modular, UI-independent).
 *
 * Wiring note for Phase 3: this class is designed to sit exactly where
 * GradleBuildService currently sits (same responsibilities: build/clean/
 * rebuild/cancel + a log/event stream), so EditorBuildEventListener and
 * RunTasksViewModel can be repointed here with minimal changes.
 */
class BuildManager(
  private val tools: BuildToolsLocator,
  private val javaBin: File,
  private val intermediatesRoot: File
) {

  private val cancelled = AtomicBoolean(false)
  private var currentJob: Job? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  fun cancel() {
    cancelled.set(true)
    currentJob?.cancel()
  }

  fun clean(config: BuildConfig): Flow<BuildLogEvent> = callbackFlow {
    trySend(BuildLogEvent.StageStarted(BuildStage.CLEAN))
    config.outputDir.deleteRecursively()
    File(intermediatesRoot, config.moduleDir.name).deleteRecursively()
    trySend(BuildLogEvent.StageFinished(BuildStage.CLEAN, true))
    close()
    awaitClose { }
  }

  fun build(config: BuildConfig, rebuild: Boolean = false): Flow<BuildLogEvent> = callbackFlow {
    cancelled.set(false)
    val startTime = System.currentTimeMillis()
    val intermediates = File(intermediatesRoot, config.moduleDir.name).apply { mkdirs() }
    if (rebuild) intermediates.deleteRecursively().also { intermediates.mkdirs() }

    val errors = mutableListOf<BuildError>()
    val warnings = mutableListOf<BuildError>()

    fun stage(s: BuildStage) = trySend(BuildLogEvent.StageStarted(s))
    fun stageDone(s: BuildStage, ok: Boolean) = trySend(BuildLogEvent.StageFinished(s, ok))
    fun log(line: String) = trySend(BuildLogEvent.Output(line))
    fun diag(e: BuildError) {
      trySend(BuildLogEvent.Diagnostic(e))
      if (e.severity == BuildError.Severity.ERROR) errors.add(e) else warnings.add(e)
    }
    fun cancelFlag() = cancelled.get()

    currentJob = scope.launch {
      try {
        if (!tools.coreToolsPresent()) {
          val missing = tools.missingCoreTools().joinToString(", ")
          diag(BuildError(null, null, null, "Missing build tools: $missing. Install the build-tools component first.", BuildError.Severity.ERROR, "engine"))
          trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
          close()
          return@launch
        }

        // 1. Dependencies
        stage(BuildStage.COLLECT_DEPENDENCIES)
        val classpathCollector = ClasspathCollector(File(intermediates, "exploded-aars"))
        val collected = classpathCollector.collect(config.moduleDir, config.compileSdkJar)
        stageDone(BuildStage.COLLECT_DEPENDENCIES, true)
        if (cancelFlag()) { trySend(BuildLogEvent.Cancelled); close(); return@launch }

        // 2. Java compile (ECJ)
        stage(BuildStage.COMPILE_JAVA)
        val classesDir = File(intermediates, "classes").apply { mkdirs() }
        val ecj = EcjCompileStep(tools, javaBin).compile(
          sources = config.javaSourceDirs.flatMap { it.walkTopDown().filter { f -> f.extension == "java" }.toList() },
          classpath = collected.compileJars + classesDir,
          outDir = classesDir,
          cancelFlag = ::cancelFlag,
          onOutputLine = ::log
        )
        ecj.errors.forEach(::diag); ecj.warnings.forEach(::diag)
        stageDone(BuildStage.COMPILE_JAVA, ecj.success)
        if (!ecj.success || cancelFlag()) {
          trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
          close(); return@launch
        }

        // 3. Kotlin compile (only if the module has .kt files)
        val ktSources = config.kotlinSourceDirs.flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        if (ktSources.isNotEmpty()) {
          stage(BuildStage.COMPILE_KOTLIN)
          val kt = KotlincCompileStep(tools).compile(
            sources = ktSources,
            classpath = collected.compileJars + classesDir,
            outDir = classesDir,
            cancelFlag = ::cancelFlag,
            onOutputLine = ::log
          )
          kt.errors.forEach(::diag); kt.warnings.forEach(::diag)
          stageDone(BuildStage.COMPILE_KOTLIN, kt.success)
          if (!kt.success || cancelFlag()) {
            trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
            close(); return@launch
          }
        }

        // 4. Resources (aapt2 compile + link)
        stage(BuildStage.PROCESS_RESOURCES)
        val resApk = File(intermediates, "resources.apk")
        val aapt2Result = Aapt2Step(tools).run(
          resDirs = config.resDirs,
          manifest = config.manifestFile,
          androidJar = config.compileSdkJar,
          extraPackagesFromAars = emptyList(), // Phase 2: read package name from each exploded AAR's manifest
          intermediatesDir = File(intermediates, "aapt2"),
          outApk = resApk,
          cancelFlag = ::cancelFlag,
          onOutputLine = ::log
        )
        aapt2Result.errors.forEach(::diag)
        stageDone(BuildStage.PROCESS_RESOURCES, aapt2Result.success)
        if (!aapt2Result.success || cancelFlag()) {
          trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
          close(); return@launch
        }

        // 5. Dex
        stage(BuildStage.DEX)
        val dexDir = File(intermediates, "dex").apply { mkdirs() }
        val d8 = D8DexStep(tools, javaBin).dex(
          classesDir = classesDir,
          libraryJars = collected.dexJars,
          minSdk = config.minSdk,
          outDir = dexDir,
          cancelFlag = ::cancelFlag,
          onOutputLine = ::log
        )
        d8.errors.forEach(::diag)
        stageDone(BuildStage.DEX, d8.success)
        if (!d8.success || cancelFlag()) {
          trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
          close(); return@launch
        }

        // 6. Assemble
        stage(BuildStage.ASSEMBLE_APK)
        val unsignedApk = File(intermediates, "unsigned.apk")
        val assembled = ApkAssembleStep().assemble(resApk, dexDir, config.assetsDirs, unsignedApk)
        stageDone(BuildStage.ASSEMBLE_APK, assembled)
        if (!assembled) {
          diag(BuildError(null, null, null, "Failed to assemble APK from resources.apk + classes.dex.", BuildError.Severity.ERROR, "engine"))
          trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
          close(); return@launch
        }

        // 7. zipalign
        stage(BuildStage.ZIPALIGN)
        val alignedApk = File(intermediates, "aligned.apk")
        val aligned = ZipAlignStep(tools).align(unsignedApk, alignedApk, ::cancelFlag, ::log)
        stageDone(BuildStage.ZIPALIGN, aligned)
        if (!aligned) {
          diag(BuildError(null, null, null, "zipalign failed.", BuildError.Severity.ERROR, "zipalign"))
          trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
          close(); return@launch
        }

        // 8. sign
        stage(BuildStage.SIGN_APK)
        config.outputDir.mkdirs()
        val finalApk = File(config.outputDir, "${config.applicationId}-${if (config.isDebug) "debug" else "release"}.apk")
        val signed = ApkSignStep(tools, javaBin).sign(
          inApk = alignedApk,
          outApk = finalApk,
          keystore = config.debugKeystore,
          keystorePass = "android",
          keyAlias = "androiddebugkey",
          keyPass = "android",
          cancelFlag = ::cancelFlag,
          onOutputLine = ::log
        )
        signed.errors.forEach(::diag)
        stageDone(BuildStage.SIGN_APK, signed.success)

        val result = BuildResult(
          success = signed.success,
          outputApk = finalApk.takeIf { signed.success },
          errors = errors,
          warnings = warnings,
          durationMs = System.currentTimeMillis() - startTime
        )
        trySend(BuildLogEvent.Finished(result))
      } catch (ce: CancellationException) {
        trySend(BuildLogEvent.Cancelled)
      } catch (t: Throwable) {
        diag(BuildError(null, null, null, "Build engine crashed: ${t.message}", BuildError.Severity.ERROR, "engine"))
        trySend(BuildLogEvent.Finished(BuildResult(false, errors = errors, warnings = warnings)))
      } finally {
        close()
      }
    }

    awaitClose { currentJob?.cancel() }
  }
}
