package com.eric.pucklauncher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eric.pucklauncher.data.Game
import com.eric.pucklauncher.data.ScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class UiState(
    val games: List<Game> = emptyList(),
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
    val updatedAt: Instant? = null,
    val nhlOk: Boolean = true,
)

class ScheduleViewModel : ViewModel() {
    private val repo = ScheduleRepository()
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var lastAttempt: Instant? = null

    fun refresh() {
        if (job?.isActive == true) return
        lastAttempt = Instant.now()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val s = repo.load()
                _state.update {
                    it.copy(games = s.games, loading = false, loadedOnce = true, error = null, updatedAt = s.fetchedAt, nhlOk = s.nhlOk)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadedOnce = true, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** Called on a ticker while the app is visible: refresh every [maxAge] (also after a failed attempt). */
    fun refreshIfStale(maxAge: Duration) {
        val last = lastAttempt
        if (last == null || Duration.between(last, Instant.now()) >= maxAge) refresh()
    }
}
