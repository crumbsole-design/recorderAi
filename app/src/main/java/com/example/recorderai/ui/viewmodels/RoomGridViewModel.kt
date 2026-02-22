package com.example.recorderai.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recorderai.DataCollectionService
import com.example.recorderai.data.CellAttributeEntity
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.data.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomGridViewModel(private val repository: ScanRepository) : ViewModel() {

    // Room management
    private val _rooms = MutableStateFlow<List<RoomEntity>>(emptyList())
    val rooms: StateFlow<List<RoomEntity>> = _rooms.asStateFlow()

    private val _selectedRoom = MutableStateFlow<RoomEntity?>(null)
    val selectedRoom: StateFlow<RoomEntity?> = _selectedRoom.asStateFlow()

    // Cell state management
    private val _activeCells = MutableStateFlow<Set<Int>>(emptySet())
    val activeCells: StateFlow<Set<Int>> = _activeCells.asStateFlow()

    private val _cellLinkableStatus = MutableStateFlow<Map<Int, Boolean?>>(emptyMap())
    val cellLinkableStatus: StateFlow<Map<Int, Boolean?>> = _cellLinkableStatus.asStateFlow()

    // Session tracking
    private val _currentSessionId = MutableStateFlow<Long>(-1L)
    val currentSessionId: StateFlow<Long> = _currentSessionId.asStateFlow()

    private val _currentCellId = MutableStateFlow<Int>(-1)
    val currentCellId: StateFlow<Int> = _currentCellId.asStateFlow()

    init {
        loadRooms()
    }

    private fun loadRooms() {
        viewModelScope.launch {
            repository.getAllRooms().collect { roomList ->
                _rooms.value = roomList
            }
        }
    }

    fun selectRoom(roomId: Long) {
        viewModelScope.launch {
            val room = _rooms.value.find { it.id == roomId }
            if (room != null) {
                _selectedRoom.value = room
                loadCellAttributes(roomId)
                // Clear active cells when changing rooms
                _activeCells.value = emptySet()
                _currentSessionId.value = -1L
                _currentCellId.value = -1
            }
        }
    }

    fun createRoom(name: String) {
        viewModelScope.launch {
            repository.createRoom(name)
            loadRooms()
        }
    }

    private fun loadCellAttributes(roomId: Long) {
        val room = _selectedRoom.value
        if (room?.id == roomId) {
            val statusMap = mutableMapOf<Int, Boolean?>()
            // Initialize all 15 cells (0-14 for 3x5 grid)
            for (i in 0 until 15) {
                viewModelScope.launch {
                    val attr = repository.getCellAttribute(roomId, i)
                    statusMap[i] = attr?.isLinkable
                    _cellLinkableStatus.value = statusMap.toMap()
                }
            }
        }
    }

    suspend fun toggleCellRecording(cellId: Int, context: Context, isLinkableChoice: Boolean? = null) {
        val roomId = _selectedRoom.value?.id ?: return
        
        // If this is the first time configuring the cell (isLinkable is null)
        if (isLinkableChoice != null) {
            repository.updateCellLinkableStatus(roomId, cellId, isLinkableChoice)
            val statusMap = _cellLinkableStatus.value.toMutableMap()
            statusMap[cellId] = isLinkableChoice
            _cellLinkableStatus.value = statusMap
        }

        // Check if we should start or stop recording for this cell
        if (_activeCells.value.contains(cellId)) {
            // Stop recording for this cell
            stopCellRecording(cellId, context)
        } else {
            // Start recording for this cell
            startCellRecording(roomId, cellId, context)
        }
    }

    private suspend fun startCellRecording(roomId: Long, cellId: Int, context: Context) {
        // Get or create session for this cell
        var sessionId = repository.getSessionByRoomAndCell(roomId, cellId)?.id
        if (sessionId == null) {
            sessionId = repository.createSession(roomId, cellId)
        }

        _currentSessionId.value = sessionId
        _currentCellId.value = cellId
        _activeCells.value = _activeCells.value + cellId

        // Tell the service to capture to this session
        val intent = Intent(context, DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_SET_SESSION
            putExtra(DataCollectionService.EXTRA_SESSION_ID, sessionId)
            putExtra(DataCollectionService.EXTRA_ROOM_ID, roomId)
            putExtra(DataCollectionService.EXTRA_CELL_ID, cellId)
        }
        context.startService(intent)
    }

    private fun stopCellRecording(cellId: Int, context: Context) {
        if (_activeCells.value.contains(cellId)) {
            _activeCells.value = _activeCells.value - cellId
            if (_currentCellId.value == cellId) {
                _currentSessionId.value = -1L
                _currentCellId.value = -1

                // Tell the service to stop capturing
                val intent = Intent(context, DataCollectionService::class.java).apply {
                    action = DataCollectionService.ACTION_STOP_SESSION
                }
                context.startService(intent)
            }
        }
    }

    suspend fun regenerateCell(cellId: Int, context: Context) {
        val roomId = _selectedRoom.value?.id ?: return
        repository.regenerateCell(roomId, cellId)
        
        // Stop recording if this cell was active
        stopCellRecording(cellId, context)
        
        // Update UI state
        val statusMap = _cellLinkableStatus.value.toMutableMap()
        statusMap[cellId] = null
        _cellLinkableStatus.value = statusMap
    }

    fun getCellLinkableStatus(cellId: Int): Boolean? {
        return _cellLinkableStatus.value[cellId]
    }

    fun isCellRecording(cellId: Int): Boolean {
        return _activeCells.value.contains(cellId)
    }
}
