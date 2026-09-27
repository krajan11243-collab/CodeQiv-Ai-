/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("UnstableApiUsage")

import com.tom.rv2ide.build.config.BuildConfig
import com.tom.rv2ide.desugaring.utils.JavaIOReplacements.applyJavaIOReplacements
import com.tom.rv2ide.plugins.AndroidIDEAssetsPlugin
import java.util.Properties

plugins {
  id("com.tom.rv2ide.core-app")
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("kotlinx-serialization")
  id("kotlin-parcelize")
  id("androidx.navigation.safeargs.kotlin")
  id("com.tom.rv2ide.desugaring")
}

apply { plugin(AndroidIDEAssetsPlugin::class.java) }

buildscript {
  dependencies {
    classpath(libs.logging.logback.core)
    classpath(libs.composite.desugaringCore)
  }
}

// AGP's file-dependency desugaring task (`desugar<Variant>FileDependencies`) is disabled
// in this project; CI pre-creates its output directory instead
// (see the "Generating missing dirs" step in .github/workflows/asm_build.yml).
//
// The match below is deliberately narrow. AGP also has tasks named
// `l8DexDesugarLib<Variant>`, which contain "Desugar" as well, and those must keep
// running: they produce the `desugar_jdk_libs` runtime dex that PackageAndroidArtifact
// packs into the APK whenever core library desugaring is enabled. With them disabled the
// APK contains no `j$` classes at all, so every desugared JDK call throws at runtime,
// e.g. JGit's `java.util.stream.Collectors.toUnmodifiableList()` (used by
// `RefDirectory.getRefsByPrefix(varargs)`, i.e. `ListMode.ALL` branch listing) fails with
// `NoClassDefFoundError: j$.util.stream.DesugarCollectors`.
tasks.configureEach {
    if (name.startsWith("desugar") && name.endsWith("FileDependencies")) {
        enabled = false
    }
}

configurations.all {
  resolutionStrategy {
    force("com.google.guava:guava:32.1.3-android")
    eachDependency {
      if (requested.group == "com.google.guava" && requested.name == "guava") {
        if (requested.version?.contains("jre") == true) {
          useVersion("32.1.3-android")
          because("Force Android version to avoid synthetic lambda conflicts")
        }
      }
    }
  }
}

android {
  namespace = BuildConfig.packageName

  defaultConfig {
    applicationId = BuildConfig.packageName
    vectorDrawables.useSupportLibrary = true
  }
  
  experimentalProperties["android.experimental.enableGlobalSynthetics"] = true
  

  signingConfigs {
      // One stable, repo-committed Android debug keystore, used by CI and by local builds
      // alike. AGP otherwise defaults to ~/.android/debug.keystore (see AGP's
      // SigningConfigFactory, which applies DefaultSigningConfig.DebugSigningConfig for the
      // "debug" name). That file does not exist on a GitHub Actions runner, so AGP
      // generated a brand-new random key on every run: each Debug APK then carried a
      // different signer certificate (verified on runs 36273739542 vs 35924946644) and
      // Android refused to install the new build over the previous one, forcing an
      // uninstall for every update.
      // The credentials below are the stock Android debug ones (androiddebugkey/android),
      // so Debug builds still need no developer secrets.
      getByName("debug") {
          storeFile = file("${rootProject.projectDir}/signing/debug.keystore")
          storePassword = "android"
          keyAlias = "androiddebugkey"
          keyPassword = "android"
      }

      create("custom") {
          val keyStorePath = "${rootProject.projectDir}/signing/signing-key.jks"
          val keyStoreFile = file(keyStorePath)
          
          val signing_storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
          val signing_keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
          
          storeFile = keyStoreFile
          storePassword = signing_storePassword
          keyAlias = "androidcs"
          keyPassword = signing_keyPassword
      }
  }

  androidResources { generateLocaleConfig = true }

  buildFeatures {
    aidl = true
    dataBinding = true
  }

  buildTypes {
    debug {
      // Standard Android debug keystore (signing/debug.keystore, committed with the repo so
      // CI and local builds keep one stable signing identity across installs).
      // Debug builds must not require the release keystore credentials,
      // so CI forks without signing secrets can still assembleDebug.
      signingConfig = signingConfigs.getByName("debug")
    }

    release {
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("custom")
    }
  }
  
  lint {
    abortOnError = false
    disable.addAll(arrayOf("VectorPath", "NestedWeights", "ContentDescription", "SmallSp"))
  }

  packaging {
    resources {
      pickFirsts += "kotlin/**.kotlin_builtins"
      pickFirsts += "THIRD-PARTY"
      pickFirsts += "LICENSE"
    }
  }

  applicationVariants.all {
    val variant = this
    variant.outputs.all {
      val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl

      val versionName = variant.versionName ?: "unknown"
      val versionCode = variant.versionCode
      val buildType = variant.buildType.name
      val filters = output.filters
      val abiFilter = filters.find { it.filterType == "ABI" }
      val archSuffix =
          abiFilter?.identifier
              ?: run {
                val variantName = variant.name.lowercase()
                when {
                  variantName.contains("arm64") -> "arm64-v8a"
                  variantName.contains("armeabi") || variantName.contains("arm7") -> "armeabi-v7a"
                  else -> {
                    // This should not happen with our configuration
                    throw IllegalStateException(
                        "Could not determine ABI for variant: $variantName. Expected arm64-v8a or armeabi-v7a."
                    )
                  }
                }
              }

      if (archSuffix !in listOf("arm64-v8a", "armeabi-v7a")) {
        throw IllegalStateException(
            "Unsupported architecture: $archSuffix. Only arm64-v8a and armeabi-v7a are supported."
        )
      }

      val appName = "android-code-studio"
      val fileName =
          if (buildType == "release") {
            "${appName}-${archSuffix}-${versionName}.apk"
          } else {
            "${appName}-${archSuffix}-${buildType}-${versionName}.apk"
          }

      output.outputFileName = fileName

      println(
          "Generated APK: $fileName for variant: ${variant.name}, arch: $archSuffix, versionCode: $versionCode"
      )
    }
  }
}

kapt { arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex") } }

desugaring {
  replacements {
    includePackage(
        "org.eclipse.jgit",
    )

    applyJavaIOReplacements()
  }
}


dependencies {
  // debugImplementation(libs.common.leakcanary)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
  implementation("org.tukaani:xz:1.9")
  implementation("org.apache.commons:commons-compress:1.21")

  // external deps here
  implementation("com.github.Dimezis:BlurView:version-3.2.0")
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation(projects.external.acsprovider)
  implementation(projects.external.atc) 
  implementation(libs.external.customizable.cardview)
  implementation(projects.external.logwire)
	implementation(libs.external.seasonal.effects)
  
  // Annotation processors
  kapt(libs.common.glide.ap)
  kapt(libs.google.auto.service)
  kapt(projects.annotation.processors)

  implementation(libs.common.editor)
  implementation(libs.common.utilcode)
  implementation(libs.common.glide)
  implementation(libs.common.jsoup)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.common.retrofit)
  implementation(libs.common.retrofit.gson)
  implementation(libs.common.charts)
  implementation(libs.common.hiddenApiBypass)
  implementation(libs.aapt2.common)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.gson)
  implementation(libs.google.guava)

  implementation("com.google.ai.client.generativeai:generativeai:0.9.0") {
    exclude(group = "org.slf4j", module = "slf4j-api")
    exclude(group = "org.slf4j", module = "slf4j-simple")
    exclude(group = "org.slf4j", module = "slf4j-nop")
  }
  
  // TODO: remove this
  implementation("com.github.MiyazKaori:SilentInstaller:1.0.0-alpha")

  // Git
  implementation(libs.git.jgit)

  // AndroidX
  implementation(libs.androidx.splashscreen)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.cardview)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.coordinatorlayout)
  implementation(libs.androidx.drawer)
  implementation(libs.androidx.grid)
  implementation(libs.androidx.nav.fragment)
  implementation(libs.androidx.nav.ui)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.transition)
  implementation(libs.androidx.vectors)
  implementation(libs.androidx.animated.vectors)
  implementation(libs.androidx.work)
  implementation(libs.androidx.work.ktx)
  implementation(libs.google.material)
  implementation(libs.google.flexbox)

  // Kotlin
  implementation(libs.androidx.core.ktx)
  implementation(libs.common.kotlin)

  // Dependencies in composite build
  implementation(libs.composite.appintro)
  implementation(libs.composite.desugaringCore)
  // implementation(libs.composite.javapoet)
  implementation(files(rootProject.file("composite-builds/build-deps/libs/javapoet.jar")))

  // Local projects here
  implementation(projects.core.projectdata)
  implementation(projects.ideconfigurations)
  implementation(projects.core.actions)
  implementation(projects.core.common)
  implementation(projects.core.indexingApi)
  implementation(projects.core.indexingCore)
  implementation(projects.core.lspApi)
  implementation(projects.core.projects)
  implementation(projects.core.resources)
  implementation(projects.editor.impl)
  implementation(projects.editor.lexers)
  implementation(projects.event.eventbus)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.java.javacServices)
  implementation(projects.java.lspSetup)
  implementation(projects.java.lsp)
  implementation(projects.logging.idestats)
  implementation(projects.logging.logsender)
  implementation(projects.termux.application)
  implementation(projects.termux.view)
  implementation(projects.termux.emulator)
  implementation(projects.termux.shared)
  implementation(projects.tooling.api)
  implementation(projects.tooling.pluginConfig)
  implementation(projects.utilities.buildInfo)
  implementation(projects.utilities.lookup)
  implementation(projects.utilities.preferences)
  implementation(projects.utilities.templatesApi)
  implementation(projects.utilities.templatesImpl)
  implementation(projects.utilities.treeview)
  implementation(projects.utilities.uidesigner)
  implementation(projects.utilities.xmlInflater)
  implementation(projects.xml.aaptcompiler)
  implementation(projects.xml.lsp)
  implementation(projects.xml.utils)

  // This is to build the tooling-api-impl project before the app is built
  // So we always copy the latest JAR file to assets
  compileOnly(projects.tooling.impl)
  
}

// SigningConfigPlugin (applied to all Android modules via the root build script's
// afterEvaluate hook) assigns its own signing config to every build type, which would
// override the debug assignment above. Re-assert the standard debug keystore here:
// this block runs after the root script's afterEvaluate hook.
afterEvaluate {
  extensions.getByType<com.android.build.gradle.AppExtension>().run {
    buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("debug")
  }
}
