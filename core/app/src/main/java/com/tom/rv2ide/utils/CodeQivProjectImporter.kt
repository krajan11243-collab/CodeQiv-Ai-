/*
 * CodeQiv project import helpers.
 *
 * This file is part of AndroidIDE and is distributed under GPL-3.0-or-later.
 */
package com.tom.rv2ide.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Copies a selected SAF folder or extracts a ZIP into a dedicated workspace directory.
 *
 * Imports preserve nested relative paths. ZIP entries are validated before writing to prevent
 * absolute-path and parent-directory traversal attacks. Limits protect mobile devices from
 * accidental or malicious archive bombs.
 */
object CodeQivProjectImporter {
  private const val MAX_ENTRIES = 20_000
  private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L
  private const val BUFFER_SIZE = 32 * 1024

  @JvmStatic
  @Throws(IOException::class)
  fun importFolder(context: Context, treeUri: Uri, destination: File): Int {
    val source = DocumentFile.fromTreeUri(context, treeUri)
      ?: throw IOException("The selected folder could not be opened.")
    if (!source.isDirectory) throw IOException("Select a project folder, not a file.")
    if (destination.exists() && (destination.listFiles()?.isNotEmpty() == true)) {
      throw IOException("The destination folder is not empty.")
    }
    if (!destination.exists() && !destination.mkdirs()) {
      throw IOException("Could not create the project workspace.")
    }
    return copyDocumentTree(context, source, destination, destination)
  }

  private fun copyDocumentTree(
    context: Context,
    source: DocumentFile,
    root: File,
    currentDestination: File,
  ): Int {
    var copied = 0
    for (child in source.listFiles()) {
      val name = child.name?.takeIf { it.isNotBlank() } ?: continue
      if (name == "." || name == ".." || name.contains(File.separatorChar)) continue
      val target = File(currentDestination, name).canonicalFile
      val canonicalRoot = root.canonicalFile
      if (target != canonicalRoot && !target.path.startsWith(canonicalRoot.path + File.separator)) {
        throw IOException("Invalid folder entry: $name")
      }
      if (child.isDirectory) {
        if (!target.exists() && !target.mkdirs()) throw IOException("Could not create $name")
        copied += copyDocumentTree(context, child, root, target)
      } else if (child.isFile) {
        val input = context.contentResolver.openInputStream(child.uri)
          ?: throw IOException("Could not read $name")
        input.use { stream ->
          target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create folder")
          }
          FileOutputStream(target).use { output -> stream.copyTo(output, BUFFER_SIZE) }
        }
        copied++
      }
    }
    return copied
  }

  @JvmStatic
  @Throws(IOException::class)
  fun importZip(context: Context, zipUri: Uri, destination: File): Int {
    if (destination.exists() && (destination.listFiles()?.isNotEmpty() == true)) {
      throw IOException("The destination folder is not empty.")
    }
    if (!destination.exists() && !destination.mkdirs()) {
      throw IOException("Could not create the project workspace.")
    }
    val root = destination.canonicalFile
    var entryCount = 0
    var totalBytes = 0L
    val input = context.contentResolver.openInputStream(zipUri)
      ?: throw IOException("Could not read the selected ZIP file.")
    ZipInputStream(BufferedInputStream(input)).use { zip ->
      var entry = zip.nextEntry
      val buffer = ByteArray(BUFFER_SIZE)
      while (entry != null) {
        entryCount++
        if (entryCount > MAX_ENTRIES) throw IOException("ZIP contains too many entries.")
        val rawName = entry.name.replace('\\', '/')
        if (rawName.startsWith("/") || rawName.split('/').any { it == ".." }) {
          throw IOException("Unsafe ZIP path rejected: $rawName")
        }
        val outputFile = File(root, rawName).canonicalFile
        if (outputFile != root && !outputFile.path.startsWith(root.path + File.separator)) {
          throw IOException("Unsafe ZIP path rejected: $rawName")
        }
        if (entry.isDirectory) {
          if (!outputFile.exists() && !outputFile.mkdirs()) throw IOException("Could not create folder")
        } else {
          outputFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create folder")
          }
          FileOutputStream(outputFile).use { output ->
            while (true) {
              val read = zip.read(buffer)
              if (read < 0) break
              totalBytes += read
              if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                throw IOException("ZIP expands beyond the 512 MB import limit.")
              }
              output.write(buffer, 0, read)
            }
          }
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }
    return entryCount
  }
}
