package com.example.recorderai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.ui.viewmodels.RoomGridViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    viewModel: RoomGridViewModel,
    onRoomSelected: (Long) -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estancias Escaneadas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        if (rooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        "No hay estancias",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Usa el botón 'NUEVA ESTANCIA' para crear una",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rooms) { room ->
                    RoomListItem(
                        room = room,
                        onSelect = {
                            viewModel.selectRoom(room.id)
                            onRoomSelected(room.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RoomListItem(
    room: RoomEntity,
    onSelect: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    val formattedDate = dateFormat.format(Date(room.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formattedDate,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Button(
                onClick = { onSelect() },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text("Seleccionar")
            }
        }
    }
}