/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.projects.internal

import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectType as AgpProjectType
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.tom.rv2ide.builder.model.DefaultAndroidGradlePluginProjectFlags
import com.tom.rv2ide.builder.model.DefaultJavaCompileOptions
import com.tom.rv2ide.builder.model.DefaultLibrary
import com.tom.rv2ide.builder.model.DefaultLibraryInfo
import com.tom.rv2ide.builder.model.DefaultSourceProvider
import com.tom.rv2ide.builder.model.DefaultSourceSetContainer
import com.tom.rv2ide.builder.model.DefaultViewBindingOptions
import com.tom.rv2ide.projects.GradleProject
import com.tom.rv2ide.projects.android.AndroidModule
import com.tom.rv2ide.tooling.api.IProject
import com.tom.rv2ide.tooling.api.models.GradleTask
import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileReader
import java.util.zip.ZipFile

/**
 * Requirement #1/#9/#10 (Phase 2): builds the same [WorkspaceImpl] shape that
 * [WorkspaceModelBuilder] produces from a Gradle sync, but by scanning the
 * project's folder layout directly — no Gradle daemon, no Tooling API
 * server process, no network.
 *
 * Deliberately reuses [GradleProject]/[AndroidModule]/the `tooling:builder-
 * model-impl` data classes rather than inventing new ones: those classes
 * are plain, mutable data holders around real AGP builder-model
 * *interfaces* (no Gradle runtime dependency), so every existing consumer
 * of the project model — Java/Kotlin completion, indexing, XML resource
 * resolution — keeps working unmodified. Only the *source* of the data
 * changes (filesystem scan vs. Gradle sync).
 *
 * Current scope: a single Android application module at the project root
 * (`AndroidManifest.xml` + `src/main/{java,kotlin,res,assets}` + `libs/`),
 * which is what Android Code Studio / Sketchware-Pro-style projects use.
 * Multi-module (`:app`, `:core`, ...) local projects and full transitive
 * Maven dependency resolution are follow-ups — see buildengine/PLAN.md.
 */
internal object LocalWorkspaceModelBuilder {

  private val log = LoggerFactory.getLogger(LocalWorkspaceModelBuilder::class.java)

  private const val ANDROID_JAR_PLACEHOLDER = "build-tools/android.jar"

  fun build(projectDir: File, buildToolsRoot: File): WorkspaceImpl? {
    return try {
      val moduleDir = resolveAppModuleDir(projectDir)
      val manifest = File(moduleDir, "src/main/AndroidManifest.xml").takeIf { it.exists() }
        ?: File(moduleDir, "AndroidManifest.xml").takeIf { it.exists() }
        ?: run {
          log.warn("No AndroidManifest.xml found under {}", moduleDir)
          return null
        }

      val namespace = readManifestPackage(manifest)
      val androidJar = File(buildToolsRoot, "android.jar")

      val root = GradleProject(
        name = projectDir.name,
        description = "",
        path = ":",
        projectDir = projectDir,
        buildDir = File(projectDir, "build"),
        buildScript = File(projectDir, "build.gradle.kts").takeIf { it.exists() }
          ?: File(projectDir, "build.gradle"),
        tasks = emptyList<GradleTask>() // No arbitrary Gradle tasks anymore; RunTasksViewModel
        // is repointed to fixed Build/Clean/Rebuild actions in Phase 3.
      )

      val appModule = buildAndroidModule(moduleDir, manifest, namespace, androidJar)

      WorkspaceImpl(
        projectDir,
        root,
        java.util.concurrent.CopyOnWriteArrayList(listOf<GradleProject>(appModule)),
        EmptyProjectSyncIssues
      )
    } catch (error: Throwable) {
      log.error("Unable to build local (Gradle-free) project model", error)
      null
    }
  }

  /** For now: the project root itself, or its `app/` subfolder if that's
   *  where the manifest actually lives (both layouts are common). */
  private fun resolveAppModuleDir(projectDir: File): File {
    val appSub = File(projectDir, "app")
    val appHasManifest = File(appSub, "src/main/AndroidManifest.xml").exists()
    return if (appHasManifest) appSub else projectDir
  }

  private fun buildAndroidModule(
    moduleDir: File,
    manifest: File,
    namespace: String?,
    androidJar: File
  ): AndroidModule {
    val srcMain = File(moduleDir, "src/main")
    val javaDir = File(srcMain, "java")
    val kotlinDir = File(srcMain, "kotlin")
    val resDir = File(srcMain, "res")
    val assetsDir = File(srcMain, "assets")

    val sourceProvider = DefaultSourceProvider().apply {
      javaDirectories = listOfNotNull(javaDir.takeIf { it.exists() })
      kotlinDirectories = listOfNotNull(kotlinDir.takeIf { it.exists() })
      resDirectories = listOfNotNull(resDir.takeIf { it.exists() })
      resourcesDirectories = emptyList()
      assetsDirectories = listOfNotNull(assetsDir.takeIf { it.exists() })
      manifestFile = manifest
      name = "main"
    }
    val sourceSetContainer = DefaultSourceSetContainer().apply {
      sourceProvider = sourceProvider
    }

    val (libraryMap, libraryKeys) = collectLibraries(moduleDir)

    return AndroidModule(
      name = moduleDir.name,
      description = "",
      path = ":${moduleDir.name}",
      projectDir = moduleDir,
      buildDir = File(moduleDir, "build"),
      buildScript = File(moduleDir, "build.gradle.kts").takeIf { it.exists() }
        ?: File(moduleDir, "build.gradle"),
      tasks = emptyList(),
      resourcePrefix = null,
      namespace = namespace,
      androidTestNamespace = null,
      testFixtureNamespace = null,
      projectType = AgpProjectType.APPLICATION,
      mainSourceSet = sourceSetContainer,
      flags = DefaultAndroidGradlePluginProjectFlags(emptyMap()),
      compilerSettings = DefaultJavaCompileOptions().apply {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        encoding = "UTF-8"
      },
      viewBindingOptions = DefaultViewBindingOptions(),
      bootClassPaths = listOf(androidJar),
      libraries = libraryKeys,
      libraryMap = libraryMap,
      lintCheckJars = emptyList(),
      variants = emptyList(),
      configuredVariant = null,
      classesJar = null
    )
  }

  /** Mirrors ClasspathCollector's libs/*.jar + libs/*.aar convention
   *  (buildengine module), duplicated here in a Gradle-model shape so
   *  IntelliSense sees the same jars the build engine compiles against. */
  private fun collectLibraries(moduleDir: File): Pair<Map<String, DefaultLibrary>, Set<String>> {
    val libsDir = File(moduleDir, "libs")
    val jars = libsDir.listFiles { f -> f.extension == "jar" }?.toList() ?: emptyList()
    val aars = libsDir.listFiles { f -> f.extension == "aar" }?.toList() ?: emptyList()

    val map = mutableMapOf<String, DefaultLibrary>()
    for (jar in jars) {
      val key = "local:jar:${jar.name}"
      map[key] = DefaultLibrary().apply {
        this.key = key
        artifact = jar
        type = LibraryType.JAVA_LIBRARY
        libraryInfo = DefaultLibraryInfo(
          attributes = emptyMap(), buildType = null, capabilities = emptyList(),
          isTestFixtures = false, productFlavors = emptyMap(),
          group = "local", name = jar.nameWithoutExtension, version = "unspecified"
        )
      }
    }
    for (aar in aars) {
      val key = "local:aar:${aar.name}"
      map[key] = DefaultLibrary().apply {
        this.key = key
        artifact = aar
        type = LibraryType.ANDROID_LIBRARY
        libraryInfo = DefaultLibraryInfo(
          attributes = emptyMap(), buildType = null, capabilities = emptyList(),
          isTestFixtures = false, productFlavors = emptyMap(),
          group = "local", name = aar.nameWithoutExtension, version = "unspecified"
        )
      }
    }
    return map to map.keys
  }

  /** Reads the legacy `package` attribute from AndroidManifest.xml.
   *  (Local, manifest-based projects — like Sketchware Pro's — don't
   *  require a `namespace {}` build.gradle entry the way modern AGP does.) */
  private fun readManifestPackage(manifest: File): String? {
    return try {
      val parser = XmlPullParserFactory.newInstance().newPullParser()
      parser.setInput(FileReader(manifest))
      var event = parser.eventType
      while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "manifest") {
          return parser.getAttributeValue(null, "package")
        }
        event = parser.next()
      }
      null
    } catch (e: Exception) {
      log.warn("Failed to read package name from {}", manifest, e)
      null
    }
  }

  /** No sync issues are possible without a Gradle sync step.
   *  NOTE: verify this matches the real AGP `ProjectSyncIssues` interface
   *  shape at compile time (offline sandbox couldn't fetch builder-model.jar
   *  to confirm the exact method signature — should be a single
   *  `getSyncIssues(): Collection<SyncIssue>`). */
  private object EmptyProjectSyncIssues : ProjectSyncIssues {
    override fun getSyncIssues(): Collection<com.android.builder.model.v2.ide.SyncIssue> = emptyList()
  }
}
