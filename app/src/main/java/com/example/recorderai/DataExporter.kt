package com.example.recorderai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.recorderai.data.ExportData
import com.example.recorderai.data.ScanRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles exporting database data to JSON format and sharing via intent.
 */
object DataExporter {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    /**
     * Export database data to a JSON file and share it.
     * @param context Android context
     * @param repository ScanRepository instance
     * @param roomId Optional room ID to export only that room's data. If null, exports all data.
     * @return Result indicating success or failure
     */
    suspend fun exportAndShare(
        context: Context,
        repository: ScanRepository,
        roomId: Long? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            // Get export data
            val exportData = repository.exportAllData(roomId)

            // Check if there's any data to export
            if (exportData.scanData.isEmpty()) {
                return@withContext ExportResult.Error("No hay datos para exportar")
            }

            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = if (roomId != null) {
                "recorderai_room_${roomId}_$timestamp.json"
            } else {
                "recorderai_export_$timestamp.json"
            }

            // Get export directory
            val exportDir = getExportDirectory(context)
            if (exportDir == null) {
                return@withContext ExportResult.Error("No se puede acceder al directorio de exportación")
            }

            // Create export file
            val exportFile = File(exportDir, fileName)

            // Write JSON to file
            val json = gson.toJson(exportData)
            FileOutputStream(exportFile).use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            }

            // Share the file
            withContext(Dispatchers.Main) {
                shareFile(context, exportFile)
            }

            ExportResult.Success(exportFile)
        } catch (e: Exception) {
            ExportResult.Error("Error al exportar: ${e.message}")
        }
    }

    /**
     * Export database data to a JSON file only (without sharing).
     * @param context Android context
     * @param repository ScanRepository instance
     * @param roomId Optional room ID to export only that room's data
     * @return Result with the created file or error
     */
    suspend fun exportToFile(
        context: Context,
        repository: ScanRepository,
        roomId: Long? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = repository.exportAllData(roomId)

            if (exportData.scanData.isEmpty()) {
                return@withContext ExportResult.Error("No hay datos para exportar")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = if (roomId != null) {
                "recorderai_room_${roomId}_$timestamp.json"
            } else {
                "recorderai_export_$timestamp.json"
            }

            val exportDir = getExportDirectory(context)
                ?: return@withContext ExportResult.Error("No se puede acceder al directorio de exportación")

            val exportFile = File(exportDir, fileName)
            val json = gson.toJson(exportData)

            FileOutputStream(exportFile).use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            }

            ExportResult.Success(exportFile)
        } catch (e: Exception) {
            ExportResult.Error("Error al exportar: ${e.message}")
        }
    }

    /**
     * Get the export directory, creating it if necessary.
     */
    private fun getExportDirectory(context: Context): File? {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "RecorderAI/exports"
        )
        return if (dir.exists() || dir.mkdirs()) dir else null
    }

    /**
     * Share a file using Android's share intent.
     */
    private fun shareFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Archivo no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exportar Datos RecorderAI"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Result of an export operation.
 */
sealed class ExportResult {
    data class Success(val file: File) : ExportResult()
    data class Error(val message: String) : ExportResult()
}