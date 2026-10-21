package com.example.util

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class GeneratedMusicTrack(
    val title: String,
    val artist: String,
    val genre: String,
    val description: String,
    val bpm: Int,
    val musicalKey: String,
    val wavFile: File,
    val uri: Uri,
    val durationSeconds: Int,
    val isSavedToDevice: Boolean = false
)

class GeminiMusicService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateTrack(
        prompt: String,
        genre: String,
        bpm: Int = 100,
        durationSeconds: Int = 15
    ): Result<GeneratedMusicTrack> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        var trackTitle = "$genre Melody"
        var trackArtist = "AI Studio Composer"
        var trackDescription = "AI-composed $genre instrumental featuring rich chords, melodic arpeggios, and rhythmic beats."
        var musicalKey = "C Major"
        var finalBpm = bpm

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val systemPrompt = """
                    You are an expert music producer and sound design architect. 
                    Given the user's music prompt and genre, compose a creative track outline.
                    Return ONLY valid JSON with keys:
                    - "title": a catchy, creative track title (2-4 words)
                    - "artist": creative artist or producer alias
                    - "description": 1-2 sentence atmospheric description of the sonic elements
                    - "musicalKey": e.g. "C Major", "A Minor", "F# Minor"
                    - "bpm": integer tempo (60-160)
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    val contentsArray = org.json.JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = org.json.JSONArray().apply {
                                val partObj = JSONObject().apply {
                                    put("text", "$systemPrompt\n\nPrompt: $prompt\nGenre: $genre\nTarget BPM: $bpm")
                                }
                                put(partObj)
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val root = JSONObject(responseBody)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        val cleanJson = text.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val parsed = JSONObject(cleanJson)
                        trackTitle = parsed.optString("title", trackTitle)
                        trackArtist = parsed.optString("artist", trackArtist)
                        trackDescription = parsed.optString("description", trackDescription)
                        musicalKey = parsed.optString("musicalKey", musicalKey)
                        finalBpm = parsed.optInt("bpm", bpm)
                    }
                }
            } catch (e: Exception) {
                // If API call fails or model busy, smoothly fall back to procedural creation
                e.printStackTrace()
            }
        } else {
            // Intelligent procedural title generator
            val adjectives = listOf("Midnight", "Cosmic", "Neon", "Velvet", "Golden", "Subtle", "Electric", "Atmospheric")
            val nouns = listOf("Echoes", "Pulse", "Drift", "Horizon", "Symphony", "Groove", "Waves", "Serenade")
            trackTitle = "${adjectives.random()} ${nouns.random()}"
            trackDescription = "Synthesized $genre track with harmonic chords, analog bass, and syncopated drums ($finalBpm BPM in $musicalKey)."
        }

        try {
            val wavFile = AudioSynthesizer.generateMusicWav(
                context = context,
                fileName = "${trackTitle.lowercase().replace(" ", "_")}_${System.currentTimeMillis() % 10000}",
                durationSeconds = durationSeconds,
                bpm = finalBpm,
                genre = genre,
                musicalKey = musicalKey
            )

            val uri = Uri.fromFile(wavFile)
            Result.success(
                GeneratedMusicTrack(
                    title = trackTitle,
                    artist = trackArtist,
                    genre = genre,
                    description = trackDescription,
                    bpm = finalBpm,
                    musicalKey = musicalKey,
                    wavFile = wavFile,
                    uri = uri,
                    durationSeconds = durationSeconds
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTrackToLibrary(track: GeneratedMusicTrack): Uri? {
        return AudioSynthesizer.exportToDeviceMusicFolder(
            context = context,
            wavFile = track.wavFile,
            title = track.title,
            artist = track.artist
        )
    }
}
