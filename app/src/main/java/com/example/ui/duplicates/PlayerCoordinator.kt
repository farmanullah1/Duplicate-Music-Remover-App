package com.example.ui.duplicates

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerCoordinator(context: Context) {
    private val player = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    data class UiState(
        val activeIndex: Int = 0,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var sources: List<Uri> = emptyList()
    private var job: Job? = null

    fun setSources(list: List<Uri>) {
        sources = list
        if (list.isNotEmpty()) {
            select(0, keepPosition = false)
        }
    }

    fun select(i: Int, keepPosition: Boolean = true) {
        if (i !in sources.indices) return
        val pos = if (keepPosition) player.currentPosition else 0L
        val wasPlaying = player.isPlaying
        player.setMediaItem(MediaItem.fromUri(sources[i]))
        player.prepare()
        player.seekTo(pos)
        player.playWhenReady = wasPlaying
        _state.update { it.copy(activeIndex = i) }
    }

    fun toggle() {
        player.playWhenReady = !player.playWhenReady
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun seek(ms: Long) {
        player.seekTo(ms)
    }

    fun startTicker(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        isPlaying = player.isPlaying,
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0)
                    )
                }
                delay(200)
            }
        }
    }

    fun release() {
        job?.cancel()
        player.release()
    }
}
