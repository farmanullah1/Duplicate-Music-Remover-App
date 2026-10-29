package com.example.ui.nav

sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object Home : Route("home")
    data object Duplicates : Route("duplicates")
    data object Junk : Route("junk")
    data object Storage : Route("storage")
    data object Settings : Route("settings")
    data object RecycleBin : Route("recycle_bin")
    data object AiMusicStudio : Route("ai_music_studio")
    data class Compare(val groupId: Long = -1) : Route("compare/{groupId}") {
        companion object {
            fun go(id: Long) = "compare/$id"
        }
    }
}
