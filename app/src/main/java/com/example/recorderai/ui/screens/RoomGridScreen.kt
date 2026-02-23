package com.example.recorderai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.ui.viewmodels.RoomGridViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomGridScreen(
    viewModel: RoomGridViewModel,
    room: RoomEntity,
    onCellClick: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeCells by viewModel.activeCells.collectAsState()
    val cellLinkableStatus by viewModel.cellLinkableStatus.collectAsState()
    var showLinkableDialog by remember { mutableStateOf<Int?>(null) }
    var showRegenerateDialog by remember { mutableStateOf<Int?>(null) }
    var pendingCellForRecording by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

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

    // Función para verificar permisos
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
            pendingCellForRecording?.let { (cellId, linkable) ->
                scope.launch {
                    viewModel.toggleCellRecording(cellId, context, linkable)
                }
            }
        } else {
            Toast.makeText(
                context,
                "Se necesitan todos los permisos para iniciar la recolección",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingCellForRecording = null
    }

    // Función helper para iniciar grabación con verificación de permisos
    fun startRecordingWithPermissions(cellId: Int, linkable: Boolean) {
        if (hasAllPermissions()) {
            scope.launch {
                viewModel.toggleCellRecording(cellId, context, linkable)
            }
        } else {
            pendingCellForRecording = cellId to linkable
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room.name, fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Cuadrícula 3x5",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 3x5 Grid (3 columns, 5 rows = 15 cells: 0-14)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                repeat(5) { rowIndex ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(3) { colIndex ->
                            val cellId = rowIndex * 3 + colIndex
                            GridCell(
                                cellId = cellId,
                                isRecording = activeCells.contains(cellId),
                                linkableStatus = cellLinkableStatus[cellId],
                                onCellClick = {
                                    // Navegar a la pantalla de detalle de la celda
                                    onCellClick(cellId)
                                },
                                onLongClick = {
                                    showRegenerateDialog = cellId
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog for configuring linkable status
    if (showLinkableDialog != null) {
        val cellId = showLinkableDialog!!
        AlertDialog(
            onDismissRequest = { showLinkableDialog = null },
            title = { Text("Configurar Celda $cellId") },
            text = { Text("¿Deseas abrir esta celda para vincular con otras estancias?") },
            confirmButton = {
                Button(
                    onClick = {
                        startRecordingWithPermissions(cellId, true)
                        showLinkableDialog = null
                    }
                ) {
                    Text("Abrir")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        startRecordingWithPermissions(cellId, false)
                        showLinkableDialog = null
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Dialog for regenerating cell
    if (showRegenerateDialog != null) {
        val cellId = showRegenerateDialog!!
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = null },
            title = { Text("Regenerar Celda $cellId") },
            text = { Text("Esto borrará todos los datos de esta celda y permitirá reconfigurar el estado linkable. ¿Continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.regenerateCell(cellId, context)
                            showRegenerateDialog = null
                        }
                    }
                ) {
                    Text("Regenerar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun GridCell(
    cellId: Int,
    isRecording: Boolean,
    linkableStatus: Boolean?,
    onCellClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isRecording -> Color(0xffEF5350) // Red for recording
        linkableStatus == true -> Color(0xff66BB6A) // Green for linkable/open
        linkableStatus == false -> Color(0xff9E9E9E) // Gray for closed
        else -> Color(0xff90A4AE) // Light gray for unconfigured
    }

    val statusIcon = when {
        isRecording -> "⏹"
        linkableStatus == true -> "🔓"
        linkableStatus == false -> "🔒"
        else -> "?"
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onCellClick() },
                    onLongPress = { onLongClick() }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    cellId.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    statusIcon,
                    fontSize = 24.sp
                )
            }
        }
    }
}
