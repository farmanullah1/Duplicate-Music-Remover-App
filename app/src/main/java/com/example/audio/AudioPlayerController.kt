package com.example.audio

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class PlayerState(
    val playingFileId: String? = null,
    val activeCompareFileAId: String? = null,
    val activeCompareFileBId: String? = null,
    val activeCompareFileId: String? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val trackTitle: String = "",
    val trackArtist: String = ""
)

class AudioPlayerController(private val context: Context) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var synthTrack: AudioTrack? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var activeCompareFileA: AudioFile? = null
    private var activeCompareFileB: AudioFile? = null

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                stop()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            val currentFileId = _playerState.value.playingFileId
                            if (currentFileId != null) {
                                playDemoSynthesizedAudio(currentFileId, _playerState.value.durationMs)
                            } else {
                                stop()
                            }
                        }
                    })
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause(audioFile: AudioFile) {
        if (_playerState.value.playingFileId == audioFile.id && _playerState.value.isPlaying) {
            pause()
        } else if (_playerState.value.playingFileId == audioFile.id && !_playerState.value.isPlaying) {
            resume()
        } else {
            play(audioFile)
        }
    }

    fun play(audioFile: AudioFile) {
        stopSynthOnly()

        activeCompareFileA = null
        activeCompareFileB = null

        _playerState.value = PlayerState(
            playingFileId = audioFile.id,
            isPlaying = true,
            progressMs = 0L,
            durationMs = audioFile.durationMs,
            trackTitle = audioFile.displayName,
            trackArtist = audioFile.artist
        )

        val uri = audioFile.uri
        if (audioFile.isDemoFile || uri == null) {
            playDemoSynthesizedAudio(audioFile.id, audioFile.durationMs)
        } else {
            try {
                val player = exoPlayer ?: ExoPlayer.Builder(context).build().also { exoPlayer = it }
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                startProgressTimer(audioFile.durationMs)
            } catch (e: Exception) {
                playDemoSynthesizedAudio(audioFile.id, audioFile.durationMs)
            }
        }
    }

    /**
     * Synchronized Multi-Audio Preview for Side-by-Side Comparison of 2 to 10 files.
     * When toggling between files in a group, maintains exact current timestamp position.
     */
    fun playGroupSynchronized(groupFiles: List<AudioFile>, targetActiveFile: AudioFile) {
        val groupFileIds = groupFiles.map { it.id }.toSet()
        val isAlreadyInGroupCompare = _playerState.value.playingFileId in groupFileIds ||
                _playerState.value.activeCompareFileId in groupFileIds

        val currentPosMs = if (isAlreadyInGroupCompare) _playerState.value.progressMs else 0L
        val isCurrentlyPlaying = _playerState.value.isPlaying

        // If user taps the currently active playing file while it's playing, toggle pause
        if (_playerState.value.playingFileId == targetActiveFile.id && isCurrentlyPlaying) {
            pause()
            return
        }

        val maxDuration = groupFiles.maxOfOrNull { it.durationMs }?.coerceAtLeast(1000L) ?: 1000L

        stopSynthOnly()

        _playerState.value = PlayerState(
            playingFileId = targetActiveFile.id,
            activeCompareFileAId = groupFiles.getOrNull(0)?.id,
            activeCompareFileBId = groupFiles.getOrNull(1)?.id,
            activeCompareFileId = targetActiveFile.id,
            isPlaying = true,
            progressMs = currentPosMs,
            durationMs = maxDuration,
            trackTitle = targetActiveFile.displayName,
            trackArtist = targetActiveFile.artist
        )

        val uri = targetActiveFile.uri
        if (targetActiveFile.isDemoFile || uri == null) {
            playDemoSynthesizedAudio(targetActiveFile.id, maxDuration, startPositionMs = currentPosMs)
        } else {
            try {
                val player = exoPlayer ?: ExoPlayer.Builder(context).build().also { exoPlayer = it }
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                if (currentPosMs > 0) {
                    player.seekTo(currentPosMs)
                }
                player.playWhenReady = true
                startProgressTimer(maxDuration)
            } catch (e: Exception) {
                playDemoSynthesizedAudio(targetActiveFile.id, maxDuration, startPositionMs = currentPosMs)
            }
        }
    }

    /**
     * Synchronized Dual-Audio Preview for Side-by-Side Comparison.
     * Delegates to playGroupSynchronized.
     */
    fun playCompareDual(fileA: AudioFile, fileB: AudioFile, targetActiveFile: AudioFile) {
        playGroupSynchronized(listOf(fileA, fileB), targetActiveFile)
    }

    fun pause() {
        try {
            exoPlayer?.playWhenReady = false
        } catch (_: Exception) {}
        try {
            synthTrack?.pause()
        } catch (_: Exception) {}
        progressJob?.cancel()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun resume() {
        val playingId = _playerState.value.playingFileId ?: return
        _playerState.value = _playerState.value.copy(isPlaying = true)

        if (synthTrack != null) {
            try { synthTrack?.play() } catch (_: Exception) {}
        } else {
            try {
                exoPlayer?.playWhenReady = true
            } catch (_: Exception) {}
        }
        startProgressTimer(_playerState.value.durationMs)
    }

    fun stop() {
        progressJob?.cancel()
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stopSynthOnly()

        activeCompareFileA = null
        activeCompareFileB = null

        _playerState.value = PlayerState()
    }

    private fun stopSynthOnly() {
        try {
            synthTrack?.stop()
            synthTrack?.release()
            synthTrack = null
        } catch (_: Exception) {
            synthTrack = null
        }
    }

    fun seekTo(progressMs: Long) {
        val clampedProgress = progressMs.coerceIn(0L, _playerState.value.durationMs)
        _playerState.value = _playerState.value.copy(progressMs = clampedProgress)

        if (exoPlayer != null && exoPlayer?.playbackState != Player.STATE_IDLE) {
            try {
                exoPlayer?.seekTo(clampedProgress)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        stop()
        try {
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTimer(maxDurationMs: Long) {
        progressJob?.cancel()
        progressJob = scope.launch {
            val duration = if (maxDurationMs > 0) maxDurationMs else 180_000L
            while (isActive && _playerState.value.isPlaying) {
                val current = exoPlayer?.currentPosition ?: (_playerState.value.progressMs + 200L)
                if (current >= duration) {
                    stop()
                    break
                }
                _playerState.value = _playerState.value.copy(progressMs = current, durationMs = duration)
                delay(200)
            }
        }
    }

    private fun playDemoSynthesizedAudio(fileId: String, durationMs: Long, startPositionMs: Long = 0L) {
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val freq = 440.0
                val numSamples = sampleRate * 2
                val sample = DoubleArray(numSamples)
                val generatedSnd = ByteArray(2 * numSamples)

                for (i in 0 until numSamples) {
                    sample[i] = sin(2 * Math.PI * i / (sampleRate / freq))
                }

                var idx = 0
                for (dVal in sample) {
                    val valInt = (dVal * 32767).toInt().toShort()
                    generatedSnd[idx++] = (valInt.toInt() and 0x00ff).toByte()
                    generatedSnd[idx++] = (valInt.toInt() and 0xff00 ushr 8).toByte()
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                synthTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AndroidAudioAttributes.Builder()
                            .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                            .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                synthTrack?.run {
                    write(generatedSnd, 0, generatedSnd.size)
                    setLoopPoints(0, numSamples, -1)
                    play()
                }

                launch(Dispatchers.Main) {
                    _playerState.value = _playerState.value.copy(progressMs = startPositionMs)
                    startProgressTimer(durationMs)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    startProgressTimer(durationMs)
                }
            }
        }
    }
}
