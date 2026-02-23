package com.example.recorderai.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recorderai.DataCollectionService
import com.example.recorderai.data.CellAttributeEntity
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.data.ScanRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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

    // Cell detail data
    private val _cellDataCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cellDataCounts: StateFlow<Map<String, Int>> = _cellDataCounts.asStateFlow()

    private val _currentCellLinkableStatus = MutableStateFlow<Boolean?>(null)
    val currentCellLinkableStatus: StateFlow<Boolean?> = _currentCellLinkableStatus.asStateFlow()

    private val _currentCellDisplayName = MutableStateFlow<String?>(null)
    val currentCellDisplayName: StateFlow<String?> = _currentCellDisplayName.asStateFlow()

    private val _hasCellData = MutableStateFlow(false)
    val hasCellData: StateFlow<Boolean> = _hasCellData.asStateFlow()

    // Session tracking
    private val _currentSessionId = MutableStateFlow<Long>(-1L)
    val currentSessionId: StateFlow<Long> = _currentSessionId.asStateFlow()

    private val _currentCellId = MutableStateFlow<Int>(-1)
    val currentCellId: StateFlow<Int> = _currentCellId.asStateFlow()

    // Polling job to keep data counts updated while recording
    private var dataCountPollingJob: Job? = null
    private var pollingRoomId: Long = -1L
    private var pollingCellId: Int = -1

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
                // Only clear recording state when switching to a DIFFERENT room
                if (_selectedRoom.value?.id != null && _selectedRoom.value?.id != roomId) {
                    _activeCells.value = emptySet()
                    _currentSessionId.value = -1L
                    _currentCellId.value = -1
                    stopDataCountPolling()
                }
                _selectedRoom.value = room
                loadCellAttributes(roomId)
            }
        }
    }

    fun createRoom(name: String) {
        viewModelScope.launch {
            repository.createRoom(name)
            loadRooms()
        }
    }

    fun deleteRoom(roomId: Long) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
            loadRooms()
            // Clear selected room if it was deleted
            if (_selectedRoom.value?.id == roomId) {
                _selectedRoom.value = null
                _activeCells.value = emptySet()
                _currentSessionId.value = -1L
                _currentCellId.value = -1
            }
        }
    }

    private fun loadCellAttributes(roomId: Long) {
        val room = _selectedRoom.value
        if (room?.id == roomId) {
            viewModelScope.launch {
                val statusMap = mutableMapOf<Int, Boolean?>()
                // Initialize all 15 cells (0-14 for 3x5 grid) and load each one
                for (i in 0 until 15) {
                    val attr = repository.getCellAttribute(roomId, i)
                    statusMap[i] = attr?.isLinkable
                }
                _cellLinkableStatus.value = statusMap.toMap()
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

        // Step 1: Ensure the foreground service is running.
        // startForegroundService is safe to call even if the service is already running —
        // onStartCommand will call ensureServiceStarted() which is guarded by !isRecording.
        val startIntent = Intent(context, DataCollectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }

        // Step 2: Tell the service which session to write data to.
        val sessionIntent = Intent(context, DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_SET_SESSION
            putExtra(DataCollectionService.EXTRA_SESSION_ID, sessionId)
            putExtra(DataCollectionService.EXTRA_ROOM_ID, roomId)
            putExtra(DataCollectionService.EXTRA_CELL_ID, cellId)
        }
        context.startService(sessionIntent)

        // Always start polling so the counter updates as data arrives.
        startDataCountPolling(roomId, cellId)
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

            // Stop live polling and do a final count refresh
            stopDataCountPolling()
            if (pollingCellId == cellId) {
                viewModelScope.launch {
                    repository.getScanDataCountsByType(pollingRoomId, cellId).collect { counts ->
                        _cellDataCounts.value = counts
                        _hasCellData.value = counts.values.any { it > 0 }
                    }
                }
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

    // Load data counts for a specific cell and start live polling if recording
    fun loadCellDataCounts(roomId: Long, cellId: Int) {
        // Register which cell is currently being viewed
        pollingRoomId = roomId
        pollingCellId = cellId

        viewModelScope.launch {
            // Load cell attribute (linkable status and display name)
            val attr = repository.getCellAttribute(roomId, cellId)
            _currentCellLinkableStatus.value = attr?.isLinkable
            _currentCellDisplayName.value = attr?.displayName

            // Immediate first fetch of data counts
            repository.getScanDataCountsByType(roomId, cellId).collect { counts ->
                _cellDataCounts.value = counts
                _hasCellData.value = counts.values.any { it > 0 }
            }
        }

        // Start polling if this cell is currently recording
        // Check both activeCells and currentCellId to survive navigation re-entries
        val isActiveCell = _activeCells.value.contains(cellId)
        val isCurrentSession = _currentCellId.value == cellId && _currentSessionId.value != -1L
        if (isActiveCell || isCurrentSession) {
            startDataCountPolling(roomId, cellId)
        }
    }

    /** Called from UI when leaving CellDetailScreen to stop background polling */
    fun stopPolling() {
        stopDataCountPolling()
    }

    private fun startDataCountPolling(roomId: Long, cellId: Int) {
        dataCountPollingJob?.cancel()
        dataCountPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000L)
                repository.getScanDataCountsByType(roomId, cellId).collect { counts ->
                    _cellDataCounts.value = counts
                    _hasCellData.value = counts.values.any { it > 0 }
                }
            }
        }
    }

    private fun stopDataCountPolling() {
        dataCountPollingJob?.cancel()
        dataCountPollingJob = null
    }

    fun updateRoomName(roomId: Long, newName: String) {
        viewModelScope.launch {
            repository.updateRoomName(roomId, newName)
            loadRooms()
        }
    }

    suspend fun configureCellAttribute(roomId: Long, cellId: Int, isLinkable: Boolean?, displayName: String?) {
        // Get existing attribute or create new one
        val existingAttr = repository.getCellAttribute(roomId, cellId)

        val newAttr = if (existingAttr != null) {
            existingAttr.copy(isLinkable = isLinkable, displayName = displayName)
        } else {
            CellAttributeEntity(
                roomId = roomId,
                cellId = cellId,
                isLinkable = isLinkable,
                displayName = displayName
            )
        }

        repository.setCellAttribute(newAttr)

        // Update UI state
        val statusMap = _cellLinkableStatus.value.toMutableMap()
        statusMap[cellId] = isLinkable
        _cellLinkableStatus.value = statusMap

        _currentCellLinkableStatus.value = isLinkable
        _currentCellDisplayName.value = displayName
    }
}
