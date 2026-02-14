package com.vibecoder.pebblecode.data

enum class AppState {
    MENU,
    SESSION,
    QUESTION
}

data class PromptData(
    val options: List<PromptOption> = emptyList(),
    val isAskUser: Boolean = false,
    val question: String = ""
)

data class PromptOption(
    val num: Int,
    val label: String
)
