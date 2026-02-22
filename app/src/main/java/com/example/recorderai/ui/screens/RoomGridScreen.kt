package com.example.recorderai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.ui.viewmodels.RoomGridViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomGridScreen(
    viewModel: RoomGridViewModel,
    room: RoomEntity,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeCells by viewModel.activeCells.collectAsState()
    val cellLinkableStatus by viewModel.cellLinkableStatus.collectAsState()
    var showLinkableDialog by remember { mutableStateOf<Int?>(null) }
    var showRegenerateDialog by remember { mutableStateOf<Int?>(null) }

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
                                    val status = cellLinkableStatus[cellId]
                                    if (status == null) {
                                        // First time clicking this cell, show dialog
                                        showLinkableDialog = cellId
                                    } else {
                                        // Already configured, toggle recording
                                        scope.launch {
                                            viewModel.toggleCellRecording(cellId, context)
                                        }
                                    }
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
                        scope.launch {
                            viewModel.toggleCellRecording(cellId, context, true)
                            showLinkableDialog = null
                        }
                    }
                ) {
                    Text("Abrir")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.toggleCellRecording(cellId, context, false)
                            showLinkableDialog = null
                        }
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
            .clickable { onCellClick() }
            .customLongPress { onLongClick() },
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

// Extension function for long press detection
fun Modifier.customLongPress(onLongPress: () -> Unit): Modifier {
    return this.pointerInput(Unit) {
        detectTapGestures(
            onLongPress = { onLongPress() }
        )
    }
}
