package com.example.recorderai

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun zipFolder(sourceFolder: File, zipFile: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                val files = sourceFolder.listFiles() ?: return false
                val buffer = ByteArray(1024)

                for (file in files) {
                    // Evitamos comprimir el propio zip si se crea dentro (por seguridad)
                    if (file.isDirectory || file.extension == "zip") continue

                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val entry = ZipEntry(file.name)
                            out.putNextEntry(entry)

                            var count: Int
                            while (origin.read(buffer, 0, 1024).also { count = it } != -1) {
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("ZipUtils", "Error al comprimir: ${e.message}")
            false
        }
    }
}