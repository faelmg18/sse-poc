package com.example.ssepoc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ssepoc.data.SseEvent
import com.example.ssepoc.data.SseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class SseUiState(
    val events: List<SseEvent> = emptyList(),
    val isConnected: Boolean = false,
    val error: String? = null,
)

class SseViewModel(
    private val repository: SseRepository = SseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SseUiState())
    val uiState: StateFlow<SseUiState> = _uiState

    private var streamJob: Job? = null

    fun connect() {
        if (streamJob?.isActive == true) return

        _uiState.value = SseUiState(isConnected = true)

        streamJob = viewModelScope.launch {
            repository.observeEvents()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isConnected = false,
                        error = e.message ?: "Erro desconhecido"
                    )
                }
                .collect { event ->
                    _uiState.value = _uiState.value.copy(
                        events = _uiState.value.events + event
                    )
                }

            // Flow encerrado normalmente (servidor fechou o stream)
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        _uiState.value = _uiState.value.copy(isConnected = false)
    }

    fun clearEvents() {
        _uiState.value = _uiState.value.copy(events = emptyList(), error = null)
    }
}
