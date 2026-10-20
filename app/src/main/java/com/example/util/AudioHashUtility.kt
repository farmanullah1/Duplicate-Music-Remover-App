package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Utility class to calculate SHA-256 hashes of audio files for exact duplicate detection.
 * All file reading and hashing operations run asynchronously on Dispatchers.IO to ensure
 * the main thread remains completely unblocked during file scanner execution.
 */
object AudioHashUtility {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer for high-throughput I/O
    private const val DEFAULT_FAST_HASH_LIMIT = 2 * 1024 * 1024L // 2 MB fast hash sample

    /**
     * Calculates the complete SHA-256 hash of an audio file.
     *
     * @param file The audio file to hash.
     * @return Hexadecimal SHA-256 string, or empty string if file is unreadable.
     */
    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) return@withContext ""
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Calculates full SHA-256 hash for a given file path string.
     */
    suspend fun calculateSha256(filePath: String): String {
        return calculateSha256(File(filePath))
    }

    /**
     * Calculates a fast partial SHA-256 hash reading up to [maxBytes] (default 2 MB).
     * Ideal for rapid preliminary exact duplicate checks across large media libraries.
     *
     * @param file The target audio file.
     * @param maxBytes Maximum bytes to read into hash digest.
     * @return Hexadecimal SHA-256 representation of the initial audio file segment.
     */
    suspend fun calculatePartialSha256(
        file: File,
        maxBytes: Long = DEFAULT_FAST_HASH_LIMIT
    ): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) return@withContext ""
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesRead = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1 && totalBytesRead < maxBytes) {
                    val toProcess = minOf(bytesRead.toLong(), maxBytes - totalBytesRead).toInt()
                    digest.update(buffer, 0, toProcess)
                    totalBytesRead += toProcess
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Calculates SHA-256 hashes for a batch of files asynchronously on IO dispatcher.
     * Invokes [onProgress] callback as each file completes processing.
     *
     * @param files List of audio files to process.
     * @param isPartial Whether to calculate partial (2MB sample) or full SHA-256 hash.
     * @param onProgress Callback receiving completed count and total file count.
     * @return Map of file path to calculated SHA-256 string.
     */
    suspend fun calculateBatchSha256(
        files: List<File>,
        isPartial: Boolean = true,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val resultMap = mutableMapOf<String, String>()
        val total = files.size
        files.forEachIndexed { index, file ->
            val hash = if (isPartial) {
                calculatePartialSha256(file)
            } else {
                calculateSha256(file)
            }
            resultMap[file.absolutePath] = hash
            onProgress(index + 1, total)
        }
        resultMap
    }
}
