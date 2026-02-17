package com.example.recorderai

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.recorderai.ui.theme.RecorderAiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecorderAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WardrivingScreen()
                }
            }
        }
    }
}

@Composable
fun WardrivingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Comprobamos estado inicial
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context)) }
    var isExporting by remember { mutableStateOf(false) }

    // Estado UI
    var lastCaptureTime by remember { mutableStateOf("Esperando datos...") }
    var wifiCount by remember { mutableStateOf(0) }

    // --- RECEPTOR DE SEÑAL ---
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DataCollectionService.ACTION_CAPTURE_UPDATE) {
                    lastCaptureTime = intent.getStringExtra(DataCollectionService.EXTRA_LAST_CAPTURE_TIME) ?: "..."
                    wifiCount = intent.getIntExtra(DataCollectionService.EXTRA_WIFI_COUNT, 0)
                }
            }
        }
        val filter = IntentFilter(DataCollectionService.ACTION_CAPTURE_UPDATE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- PERMISOS ---
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        // Si todos son true, iniciamos
        if (perms.values.all { it }) {
            startWardrivingService(context)
            isServiceRunning = true
        } else {
            Toast.makeText(context, "Se necesitan todos los permisos", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Indicador
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(if (isServiceRunning) Color.Red else Color.Gray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isServiceRunning) "ON" else "OFF", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ÚLTIMA CAPTURA:", style = MaterialTheme.typography.labelSmall)
                Text(lastCaptureTime, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Redes WiFi: $wifiCount", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Botón START / STOP
        Button(
            onClick = {
                if (isServiceRunning) {
                    stopWardrivingService(context)
                    isServiceRunning = false
                } else {
                    // AQUÍ ESTÁ LA CLAVE: Verificamos permisos reales
                    if (hasAllPermissions(context, permissionsToRequest)) {
                        startWardrivingService(context)
                        isServiceRunning = true
                    } else {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isServiceRunning) Color.DarkGray else MaterialTheme.colorScheme.primary)
        ) {
            Text(if (isServiceRunning) "DETENER" else "INICIAR ESCANEO")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón EXPORTAR
        Button(
            onClick = {
                if (isServiceRunning) {
                    Toast.makeText(context, "Detén la grabación primero", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        isExporting = true
                        exportLastSession(context)
                        isExporting = false
                    }
                }
            },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            if (isExporting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("EXPORTAR ZIP")
        }
    }
}

// --- LÓGICA DE EXPORTACIÓN Y UTILIDADES ---

suspend fun exportLastSession(context: Context) {
    withContext(Dispatchers.IO) {
        val rootDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (rootDir == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Almacenamiento externo no disponible", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val sessionsDir = File(rootDir, "RecorderAI")
        if (!sessionsDir.exists() && !sessionsDir.mkdirs()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No se puede acceder al directorio de sesiones", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val lastSession = sessionsDir.listFiles { f -> f.isDirectory }?.maxByOrNull { it.lastModified() }
        if (lastSession == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No hay sesiones", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val zipFile = File(sessionsDir, "${lastSession.name}.zip")
        val zipResult = ZipUtils.zipFolder(lastSession, zipFile)

        withContext(Dispatchers.Main) {
            when (zipResult) {
                is ZipResult.Success -> shareFile(context, zipResult.file)
                is ZipResult.Error -> Toast.makeText(context, "Error al comprimir: ${zipResult.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun shareFile(context: Context, file: File) {
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
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar Datos"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// --- FUNCIONES CORREGIDAS (YA NO SON STUBS) ---

fun startWardrivingService(context: Context) {
    val intent = Intent(context, DataCollectionService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    Toast.makeText(context, "Iniciando servicio...", Toast.LENGTH_SHORT).show()
}

fun stopWardrivingService(context: Context) {
    context.stopService(Intent(context, DataCollectionService::class.java))
    Toast.makeText(context, "Servicio detenido", Toast.LENGTH_SHORT).show()
}

// Verificación REAL de permisos
fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean =
    permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

// Verificación REAL de servicio
@Suppress("DEPRECATION")
fun isServiceRunning(context: Context): Boolean {
    return try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        manager.getRunningServices(Int.MAX_VALUE).any { it.service?.className == DataCollectionService::class.java.name }
    } catch (e: Exception) {
        false
    }
}