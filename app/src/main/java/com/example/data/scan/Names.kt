package com.example.data.scan

import java.text.Normalizer

object Names {
    private val suffixes = listOf(
        Regex("""\s*\(\d+\)$"""),
        Regex("""\s*\[\d+]$"""),
        Regex("""\s*[-_ ]\s*copy(\s*\(\d+\)|\s*\d+)?$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(copy(\s*\d+)?\)$""", RegexOption.IGNORE_CASE),
        Regex("""\s*[-_]\s*\d{1,2}$""")
    )

    fun base(fileName: String): Pair<String, Boolean> { // (normalizedKey, hadCopySuffix)
        var s = fileName.substringBeforeLast('.')
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")

        var changed = false
        do {
            var hit = false
            for (r in suffixes) {
                val n = r.replace(s, "")
                if (n != s && n.isNotBlank()) {
                    s = n.trim()
                    hit = true
                    changed = true
                }
            }
        } while (hit)

        return s to changed
    }

    fun tag(s: String?): String? = s?.let {
        Normalizer.normalize(it, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\u0600-\\u06FF ]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { null }
    }
}
