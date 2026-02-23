package com.example.recorderai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.ui.viewmodels.RoomGridViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellDetailScreen(
    viewModel: RoomGridViewModel,
    room: RoomEntity,
    cellId: Int,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val cellDataCounts by viewModel.cellDataCounts.collectAsState()
    val currentCellLinkableStatus by viewModel.currentCellLinkableStatus.collectAsState()
    val currentCellDisplayName by viewModel.currentCellDisplayName.collectAsState()
    val hasCellData by viewModel.hasCellData.collectAsState()
    val activeCells by viewModel.activeCells.collectAsState()
    
    val isRecording = activeCells.contains(cellId)
    val totalDataCount = cellDataCounts.values.sum()
    
    // Estado para el diálogo de configuración
    var showConfigDialog by remember { mutableStateOf(false) }
    var configIsLinkable by remember { mutableStateOf(false) }
    var configDisplayName by remember { mutableStateOf("") }

    // Permisos necesarios para la recolección
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
    }
    
    // Función para verificar si tiene todos los permisos
    val hasAllPermissions: () -> Boolean = {
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Launcher para solicitar permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Permisos concedidos, iniciar recolección
            scope.launch {
                viewModel.toggleCellRecording(cellId, context)
            }
        } else {
            Toast.makeText(
                context,
                "Se necesitan todos los permisos para iniciar la recolección",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Función para manejar el click en el botón
    val onStartCollectionClick: () -> Unit = {
        if (isRecording) {
            // Si ya está grabando, detener
            scope.launch {
                viewModel.toggleCellRecording(cellId, context)
            }
        } else {
            // Verificar permisos antes de iniciar
            if (hasAllPermissions()) {
                scope.launch {
                    viewModel.toggleCellRecording(cellId, context)
                }
            } else {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Celda $cellId", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Estado de la celda
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentCellLinkableStatus == null) {
                            Modifier.clickable {
                                showConfigDialog = true
                            }
                        } else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        currentCellLinkableStatus == true -> Color(0xff66BB6A)
                        currentCellLinkableStatus == false -> Color(0xff9E9E9E)
                        else -> Color(0xff90A4AE)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when (currentCellLinkableStatus) {
                            true -> "🔓 Abierta"
                            false -> "🔒 Cerrada"
                            else -> "? Sin configurar"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Estancia: ${room.name}",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    // Mostrar displayName si existe
                    currentCellDisplayName?.let { displayName ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Nombre: $displayName",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Resumen de datos
            Text(
                "Resumen de Datos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (hasCellData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // WiFi
                        DataCountRow(
                            icon = "📶",
                            label = "WiFi",
                            count = cellDataCounts["WIFI"] ?: 0,
                            color = Color(0xFF2196F3)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Bluetooth
                        DataCountRow(
                            icon = "📱",
                            label = "Bluetooth",
                            count = cellDataCounts["BLUETOOTH"] ?: 0,
                            color = Color(0xFF9C27B0)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Cell
                        DataCountRow(
                            icon = "📲",
                            label = "Celular",
                            count = cellDataCounts["CELL"] ?: 0,
                            color = Color(0xFF4CAF50)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Magnetometer
                        DataCountRow(
                            icon = "🧭",
                            label = "Magnetómetro",
                            count = cellDataCounts["MAGNETOMETER"] ?: 0,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "📭",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Sin datos registrados",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Inicia la recolección para comenzar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Total
            Text(
                "Total: $totalDataCount registros",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de acción
            Button(
                onClick = onStartCollectionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xffEF5350) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (isRecording) "⏹ DETENER RECOLECCIÓN" else "▶ INICIAR RECOLECCIÓN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info text
            Text(
                if (isRecording) "Grabando datos en tiempo real..." else "Toca el botón para comenzar a recopilar datos",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    // Diálogo de configuración
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configurar Celda $cellId") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Configura las propiedades de esta celda:")

                    // Checkbox para enlazable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { configIsLinkable = !configIsLinkable },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = configIsLinkable,
                            onCheckedChange = { configIsLinkable = it },
                            modifier = Modifier.testTag("linkableCheckbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Habilitar como celda enlazable a otras estancias")
                    }

                    // Campo de texto para nombre descriptivo
                    OutlinedTextField(
                        value = configDisplayName,
                        onValueChange = { configDisplayName = it },
                        label = { Text("Nombre descriptivo (opcional)") },
                        placeholder = { Text("Ej: Puerta principal, Ventana...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.configureCellAttribute(
                                roomId = room.id,
                                cellId = cellId,
                                isLinkable = configIsLinkable,
                                displayName = configDisplayName.takeIf { it.isNotBlank() }
                            )
                            showConfigDialog = false
                            // Recargar datos de la celda
                            viewModel.loadCellDataCounts(room.id, cellId)
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DataCountRow(
    icon: String,
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                count.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
