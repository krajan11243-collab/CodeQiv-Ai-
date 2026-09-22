plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  testImplementation("junit:junit:4.13.2")
}

// NOTE (Phase 3 wiring): core:app should add
//   implementation(project(":buildengine:engine"))
// and GradleBuildService/GradleBuildServiceConnnection get replaced by a thin
// wrapper around com.tom.rv2ide.buildengine.BuildManager. See buildengine/PLAN.md.
