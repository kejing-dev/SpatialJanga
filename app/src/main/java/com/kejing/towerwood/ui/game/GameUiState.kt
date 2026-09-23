package com.kejing.towerwood.ui.game

enum class Phase { LOADING, HOME, SETTINGS, PLAYING, HOLDING, SETTLING, PAUSED, TRACKING_LOST, HELP, RESTART, RESULT, ERROR }
data class GameUiState(val phase: Phase = Phase.LOADING, val score: Int = 0, val best: Int = 0, val seconds: Int = 0, val message: String = "正在准备木塔…", val selected: Int? = null, val woodTheme: WoodTheme = WoodTheme.NATURAL, val canUndo: Boolean = false, val recovered: Boolean = false, val soundEnabled: Boolean = true, val guidesEnabled: Boolean = true)
sealed interface GameEvent {
    data object Start : GameEvent
    data object Pause : GameEvent
    data object Resume : GameEvent
    data object Help : GameEvent
    data object RestartPrompt : GameEvent
    data object Restart : GameEvent
    data object TrackingLost : GameEvent
}
