package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

object AudioSynthesizer {

    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2 // Stereo

    // Scale frequencies (in Hz)
    private val NOTE_FREQS = mapOf(
        "C3" to 130.81, "D3" to 146.83, "E3" to 164.81, "F3" to 174.61, "G3" to 196.00, "A3" to 220.00, "B3" to 246.94,
        "C4" to 261.63, "D4" to 293.66, "E4" to 329.63, "F4" to 349.23, "G4" to 392.00, "A4" to 440.00, "B4" to 493.88,
        "C5" to 523.25, "D5" to 587.33, "E5" to 659.25, "F5" to 698.46, "G5" to 783.99, "A5" to 880.00
    )

    /**
     * Synthesizes a rich musical track into a stereo PCM 16-bit 44.1kHz .wav file.
     */
    suspend fun generateMusicWav(
        context: Context,
        fileName: String,
        durationSeconds: Int = 20,
        bpm: Int = 100,
        genre: String = "Lo-Fi Chill",
        musicalKey: String = "C Major"
    ): File = withContext(Dispatchers.IO) {
        val totalSamples = SAMPLE_RATE * durationSeconds
        val pcmData = ShortArray(totalSamples * CHANNELS)

        val beatDuration = 60.0 / bpm
        val beatSamples = (SAMPLE_RATE * beatDuration).toInt()

        // Choose chord progressions based on key / genre
        val chordProgressions = when (genre.lowercase()) {
            "cyberpunk synthwave" -> listOf(
                listOf("A3", "C4", "E4"),
                listOf("F3", "A3", "C4"),
                listOf("D3", "F3", "A3"),
                listOf("E3", "G3", "B3")
            )
            "acoustic sunrise" -> listOf(
                listOf("G3", "B3", "D4"),
                listOf("C4", "E4", "G4"),
                listOf("E3", "G3", "B3"),
                listOf("D3", "F3", "A3")
            )
            "8-bit chiptune" -> listOf(
                listOf("C4", "E4", "G4"),
                listOf("G3", "B3", "D4"),
                listOf("A3", "C4", "E4"),
                listOf("F3", "A3", "C4")
            )
            else -> listOf( // Lo-fi / ambient default
                listOf("C4", "E4", "G4", "B4"),
                listOf("A3", "C4", "E4", "G4"),
                listOf("F3", "A3", "C4", "E4"),
                listOf("G3", "B3", "D4", "F4")
            )
        }

        val leadScale = listOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val beatIndex = (i / beatSamples)
            val currentChord = chordProgressions[(beatIndex / 4) % chordProgressions.size]

            var leftSample = 0.0
            var rightSample = 0.0

            // 1. Chords (warm synth/piano pad with subtle detune)
            for ((noteIdx, noteName) in currentChord.withIndex()) {
                val freq = NOTE_FREQS[noteName] ?: 261.63
                val detune = (noteIdx % 2 == 0)
                val padWave = sin(2.0 * PI * freq * t) * 0.15 +
                        sin(2.0 * PI * (freq * 2.0) * t) * 0.05
                if (detune) {
                    leftSample += padWave * 1.1
                    rightSample += padWave * 0.9
                } else {
                    leftSample += padWave * 0.9
                    rightSample += padWave * 1.1
                }
            }

            // 2. Melody / Arpeggio
            val noteInBeat = ((i % beatSamples) / (beatSamples / 4))
            val melodyFreq = leadScale[(beatIndex * 2 + noteInBeat) % leadScale.size]
            val melodyEnvelope = 1.0 - ((i % (beatSamples / 4)).toDouble() / (beatSamples / 4))
            val melodyWave = when (genre.lowercase()) {
                "8-bit chiptune" -> if (sin(2.0 * PI * melodyFreq * t) > 0) 0.18 else -0.18
                else -> sin(2.0 * PI * melodyFreq * t) * 0.18 + sin(2.0 * PI * (melodyFreq * 0.5) * t) * 0.08
            }
            leftSample += melodyWave * melodyEnvelope
            rightSample += melodyWave * melodyEnvelope

            // 3. Bass / Sub-bass
            val rootNoteName = currentChord.first()
            val rootFreq = (NOTE_FREQS[rootNoteName] ?: 130.81) * 0.5
            val bassWave = sin(2.0 * PI * rootFreq * t) * 0.25
            leftSample += bassWave
            rightSample += bassWave

            // 4. Rhythm Drums (Kick & Snare / Hi-hat)
            val sampleInBeat = i % beatSamples
            if (sampleInBeat < SAMPLE_RATE * 0.15) { // Kick on beat
                val kickDecay = 1.0 - (sampleInBeat.toDouble() / (SAMPLE_RATE * 0.15))
                val kickPitch = 120.0 * kickDecay
                val kick = sin(2.0 * PI * kickPitch * (sampleInBeat.toDouble() / SAMPLE_RATE)) * 0.35 * kickDecay
                leftSample += kick
                rightSample += kick
            }
            if (beatIndex % 2 == 1 && sampleInBeat < SAMPLE_RATE * 0.18) { // Snare on 2 & 4
                val snareDecay = 1.0 - (sampleInBeat.toDouble() / (SAMPLE_RATE * 0.18))
                val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.18 * snareDecay
                leftSample += noise
                rightSample += noise
            }

            // Vinyl crackle / warm texture for Lo-Fi
            if (genre.contains("Lo-Fi", ignoreCase = true) && Random.nextDouble() < 0.003) {
                val pop = (Random.nextDouble() * 2.0 - 1.0) * 0.08
                leftSample += pop
                rightSample += pop
            }

            // Normalize and write to PCM buffer
            val clampedL = (leftSample.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
            val clampedR = (rightSample.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()

            pcmData[i * 2] = clampedL
            pcmData[i * 2 + 1] = clampedR
        }

        // Write WAV header and PCM data to file
        val outputDir = File(context.cacheDir, "ai_music").apply { mkdirs() }
        val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalFile = File(outputDir, if (sanitizedName.endsWith(".wav")) sanitizedName else "$sanitizedName.wav")

        FileOutputStream(finalFile).use { fos ->
            writeWavHeader(fos, totalSamples, CHANNELS, SAMPLE_RATE)
            val buffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                buffer.putShort(s)
            }
            fos.write(buffer.array())
        }

        finalFile
    }

    /**
     * Exports the synthesized file into the device's public Music folder using MediaStore.
     */
    suspend fun exportToDeviceMusicFolder(context: Context, wavFile: File, title: String, artist: String): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, "AI Studio Creations")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AI Studio")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                wavFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeWavHeader(out: OutputStream, totalAudioSamples: Int, channels: Int, sampleRate: Int) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = totalAudioSamples * channels * bitsPerSample / 8
        val chunkSize = 36 + dataSize

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray())
        buf.putInt(chunkSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1) // AudioFormat 1 = PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(dataSize)

        out.write(header)
    }
}
