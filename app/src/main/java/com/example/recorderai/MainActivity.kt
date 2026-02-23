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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.recorderai.data.AppDatabase
import com.example.recorderai.data.ScanRepository
import com.example.recorderai.ui.screens.CellDetailScreen
import com.example.recorderai.ui.screens.RoomGridScreen
import com.example.recorderai.ui.screens.RoomListScreen
import com.example.recorderai.ui.theme.RecorderAiTheme
import com.example.recorderai.ui.viewmodels.RoomGridViewModel
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
                    MainNavigation()
                }
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Initialize ViewModel and Repository with SQLite-based DAO
    val dao = AppDatabase.getInstance(context)
    val repository = ScanRepository(dao)
    val viewModel = remember { RoomGridViewModel(repository) }

    NavHost(navController = navController, startDestination = "room_list") {
        composable("room_list") {
            RoomListScreenWithControls(
                viewModel = viewModel,
                onRoomSelected = { roomId ->
                    navController.navigate("room_grid/$roomId")
                }
            )
        }
        composable("room_grid/{roomId}") { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")?.toLongOrNull() ?: return@composable
            val room = viewModel.rooms.collectAsState().value.find { it.id == roomId }
            if (room != null) {
                // ARREGLO: Llamar selectRoom para cargar los atributos de celda
                LaunchedEffect(roomId) {
                    viewModel.selectRoom(roomId)
                }
                RoomGridScreen(
                    viewModel = viewModel,
                    room = room,
                    onCellClick = { cellId ->
                        navController.navigate("cell_detail/$roomId/$cellId")
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
        composable("cell_detail/{roomId}/{cellId}") { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")?.toLongOrNull() ?: return@composable
            val cellId = backStackEntry.arguments?.getString("cellId")?.toIntOrNull() ?: return@composable
            val room = viewModel.rooms.collectAsState().value.find { it.id == roomId }
            if (room != null) {
                // Load counts FIRST (before selectRoom can clear activeCells)
                LaunchedEffect(roomId, cellId) {
                    viewModel.loadCellDataCounts(roomId, cellId)
                }
                // selectRoom loads cell attributes (does NOT clear activeCells for same room)
                LaunchedEffect(roomId) {
                    viewModel.selectRoom(roomId)
                }
                // Stop polling when leaving this screen
                DisposableEffect(roomId, cellId) {
                    onDispose {
                        viewModel.stopPolling()
                    }
                }
                CellDetailScreen(
                    viewModel = viewModel,
                    room = room,
                    cellId = cellId,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun RoomListScreenWithControls(
    viewModel: RoomGridViewModel,
    onRoomSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Poll real service state every second so the button always reflects reality,
    // even when the service is started/stopped from CellDetailScreen.
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            isServiceRunning = isServiceRunning(context)
            kotlinx.coroutines.delay(1000L)
        }
    }
    var isExporting by remember { mutableStateOf(false) }
    var lastCaptureTime by remember { mutableStateOf("Esperando datos...") }
    var wifiCount by remember { mutableStateOf(0) }
    
    // Estado para el diálogo de crear nueva estancia
    var showCreateDialog by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }

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
        if (perms.values.all { it }) {
            startWardrivingService(context)
            isServiceRunning = true
        } else {
            Toast.makeText(context, "Se necesitan todos los permisos", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RoomListScreen(
            viewModel = viewModel,
            onRoomSelected = onRoomSelected
        )

        // Control Panel Overlay (bottom-right corner)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(if (isServiceRunning) Color(0xffEF5350) else Color.Gray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isServiceRunning) "ON" else "OFF",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status text
            Text(
                lastCaptureTime,
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "WiFi: $wifiCount",
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth()
            )

            // Nueva Estancia button
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(
                    "NUEVA ESTANCIA",
                    fontSize = 12.sp
                )
            }

            // Start/Stop button
            Button(
                onClick = {
                    if (isServiceRunning) {
                        stopWardrivingService(context)
                        isServiceRunning = false
                    } else {
                        if (hasAllPermissions(context, permissionsToRequest)) {
                            startWardrivingService(context)
                            isServiceRunning = true
                        } else {
                            permissionLauncher.launch(permissionsToRequest)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Color.DarkGray else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(
                    if (isServiceRunning) "DETENER" else "INICIAR",
                    fontSize = 12.sp
                )
            }

            // Export button
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(
                    if (isExporting) "EXPORTANDO..." else "EXPORTAR",
                    fontSize = 12.sp
                )
            }
        }
    }
    
    // Diálogo para crear nueva estancia
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nueva Estancia") },
            text = {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Nombre de la estancia") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomName.isNotBlank()) {
                            viewModel.createRoom(roomName)
                            roomName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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