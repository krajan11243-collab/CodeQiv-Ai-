package com.tom.rv2ide.buildengine.model

import java.io.File

/**
 * A single compiler/build-tool diagnostic, normalized so the editor can
 * jump straight to [file]:[line] when the user taps on it.
 */
data class BuildError(
  val file: File?,
  val line: Int?,
  val column: Int?,
  val message: String,
  val severity: Severity,
  val source: String // e.g. "ecj", "kotlinc", "aapt2", "d8"
) {
  enum class Severity { ERROR, WARNING, INFO }
}

/** Which stage of the pipeline is currently running / failed. */
enum class BuildStage {
  CLEAN,
  COLLECT_SOURCES,
  COLLECT_DEPENDENCIES,
  COMPILE_JAVA,
  COMPILE_KOTLIN,
  DEX,
  PROCESS_RESOURCES, // aapt2 compile + link
  ASSEMBLE_APK,
  ZIPALIGN,
  SIGN_APK,
  DONE
}

data class BuildResult(
  val success: Boolean,
  val outputApk: File? = null,
  val errors: List<BuildError> = emptyList(),
  val warnings: List<BuildError> = emptyList(),
  val durationMs: Long = 0
)

/** Streamed to the UI (build log console) as the pipeline progresses. */
sealed class BuildLogEvent {
  data class StageStarted(val stage: BuildStage) : BuildLogEvent()
  data class StageFinished(val stage: BuildStage, val ok: Boolean) : BuildLogEvent()
  data class Output(val line: String) : BuildLogEvent()
  data class Diagnostic(val error: BuildError) : BuildLogEvent()
  data class Finished(val result: BuildResult) : BuildLogEvent()
  object Cancelled : BuildLogEvent()
}

/** Minimal, Gradle-free description of what to build — parsed from the
 *  project's local source layout, not from build.gradle. See Phase 2. */
data class BuildConfig(
  val projectDir: File,
  val moduleDir: File,
  val applicationId: String,
  val minSdk: Int,
  val targetSdk: Int,
  val compileSdkJar: File,       // build-tools/android.jar
  val manifestFile: File,
  val javaSourceDirs: List<File>,
  val kotlinSourceDirs: List<File>,
  val resDirs: List<File>,
  val assetsDirs: List<File>,
  val libJars: List<File>,       // collected from libs/*.jar + exploded AARs
  val libAars: List<File>,
  val outputDir: File,
  val debugKeystore: File,
  val isDebug: Boolean = true
)
