package com.tom.rv2ide.buildengine.classpath

import java.io.File
import java.util.zip.ZipFile

/**
 * Requirement #6: automatically collect project dependencies (jar/aar) and
 * build a correct compile/dex classpath — without Gradle dependency
 * resolution. This does NOT resolve transitive Maven coordinates (that
 * needs a real resolver in Phase 2); it handles the common local case:
 * libs/*.jar and libs/*.aar sitting inside the project, which covers most
 * Sketchware-Pro-style / offline projects.
 */
class ClasspathCollector(private val explodedAarCacheDir: File) {

  data class Collected(
    val compileJars: List<File>, // classes.jar from AARs + plain jars, for ecj/kotlinc
    val dexJars: List<File>      // same jars, fed to d8
  )

  fun collect(moduleDir: File, androidJar: File): Collected {
    val libsDir = File(moduleDir, "libs")
    val plainJars = libsDir.listFiles { f -> f.extension == "jar" }?.toList() ?: emptyList()
    val aars = libsDir.listFiles { f -> f.extension == "aar" }?.toList() ?: emptyList()

    val explodedClassesJars = aars.map { explodeAar(it) }

    val all = (plainJars + explodedClassesJars).distinct()
    return Collected(
      compileJars = listOf(androidJar) + all,
      dexJars = all // android.jar must NOT be dexed — it's provided by the platform
    )
  }

  /** Unpacks an AAR's classes.jar (and merges its res/ later, handled by the
   *  resource step) into a per-AAR cache dir keyed by content hash so repeat
   *  builds don't re-explode unchanged AARs. */
  private fun explodeAar(aar: File): File {
    val destDir = File(explodedAarCacheDir, "${aar.nameWithoutExtension}-${aar.length()}-${aar.lastModified()}")
    val classesJar = File(destDir, "classes.jar")
    if (classesJar.exists()) return classesJar

    destDir.mkdirs()
    ZipFile(aar).use { zip ->
      val entry = zip.getEntry("classes.jar") ?: error("Malformed AAR (no classes.jar): ${aar.name}")
      zip.getInputStream(entry).use { input ->
        classesJar.outputStream().use { output -> input.copyTo(output) }
      }
      // res/ and AndroidManifest.xml from the AAR are extracted separately by
      // the resource-processing step so aapt2 can merge them.
    }
    return classesJar
  }

  /** Extracts an AAR's res/ dir + AndroidManifest.xml, for aapt2 --extra-packages / -R. */
  fun explodeAarResources(aar: File, into: File) {
    ZipFile(aar).use { zip ->
      zip.entries().asSequence()
        .filter { it.name.startsWith("res/") || it.name == "AndroidManifest.xml" }
        .forEach { entry ->
          val outFile = File(into, entry.name)
          if (entry.isDirectory) {
            outFile.mkdirs()
          } else {
            outFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
              outFile.outputStream().use { output -> input.copyTo(output) }
            }
          }
        }
    }
  }
}
