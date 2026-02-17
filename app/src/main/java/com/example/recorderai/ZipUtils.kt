package com.example.recorderai

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ZipResult {
    data class Success(val file: File) : ZipResult()
    data class Error(val message: String, val exception: Throwable? = null) : ZipResult()
}

object ZipUtils {
    private const val TAG = "ZipUtils"
    private const val BUFFER_SIZE = 8 * 1024

    /**
     * Compresses files directly under [sourceFolder] into [zipFile].
     * - Skips directories and existing .zip files inside sourceFolder (prevents zipping the zip).
     * - Returns [ZipResult.Success] on success or [ZipResult.Error] with details on failure.
     */
    fun zipFolder(sourceFolder: File, zipFile: File): ZipResult {
        if (!sourceFolder.exists() || !sourceFolder.isDirectory) {
            val msg = "Invalid source folder: ${sourceFolder.path}"
            Log.w(TAG, msg)
            return ZipResult.Error(msg)
        }

        // Ensure destination parent exists (if present)
        try {
            val parent = zipFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                val msg = "Unable to create parent directories for zip: ${parent.path}"
                Log.e(TAG, msg)
                return ZipResult.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare zip destination", e)
            return ZipResult.Error("Failed to prepare zip destination", e)
        }

        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                val files = sourceFolder.listFiles() ?: run {
                    val msg = "Could not list files in ${sourceFolder.path}"
                    Log.w(TAG, msg)
                    return@zipFolder ZipResult.Error(msg)
                }

                for (file in files) {
                    if (!file.isFile) continue
                    if (file.extension.equals("zip", ignoreCase = true)) continue

                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val entry = ZipEntry(file.name)
                            out.putNextEntry(entry)
                            origin.copyTo(out, BUFFER_SIZE)
                            out.closeEntry()
                        }
                    }
                }
            }
            ZipResult.Success(zipFile)
        } catch (e: IOException) {
            Log.e(TAG, "I/O error while zipping", e)
            ZipResult.Error("I/O error while zipping", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while zipping", e)
            ZipResult.Error("Unexpected error while zipping", e)
        }
    }
}