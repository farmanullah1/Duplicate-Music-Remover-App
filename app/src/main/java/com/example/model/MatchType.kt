package com.example.model

enum class MatchType(val label: String, val description: String) {
    EXACT_HASH(
        label = "Exact Content Match",
        description = "Identical audio data (SHA-256 hash match), regardless of file name or tags."
    ),
    FILENAME_VARIATION(
        label = "Filename Variation",
        description = "Similar file base name or numbered suffixes (e.g., song.mp3 vs song (1).mp3)."
    ),
    METADATA_MATCH(
        label = "Metadata Match",
        description = "Matching ID3 tags (Title, Artist, Album & Duration) despite different filenames."
    )
}
