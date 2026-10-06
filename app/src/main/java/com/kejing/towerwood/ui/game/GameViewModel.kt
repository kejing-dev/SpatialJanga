package com.kejing.towerwood.ui.game

import androidx.lifecycle.ViewModel
import com.kejing.towerwood.data.repository.GameRepository
import com.kejing.towerwood.scene.TowerScene
import com.kejing.towerwood.ui.game.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GameViewModel(private val repository: GameRepository) : ViewModel() {
    private val mutable = MutableStateFlow(GameUiState(woodTheme=WoodTheme.fromSaved(repository.theme()),best = repository.best(),soundEnabled=repository.sound()))
    val uiState: StateFlow<GameUiState> = mutable
    private var previous = Phase.PLAYING
    private var restartPrevious = Phase.PAUSED
    private var helpPrevious = Phase.HOME
    fun ready() { mutable.value = mutable.value.copy(phase = Phase.HOME, message = "慢慢抽出，稳稳叠高。") }
    fun error() { mutable.value = mutable.value.copy(phase = Phase.ERROR, message = "木塔加载失败，请重新打开应用。") }
    fun event(event: GameEvent) {
        val s = mutable.value
        when (event) {
            GameEvent.Start, GameEvent.Restart -> { previous=Phase.PLAYING; mutable.value = GameUiState(Phase.PLAYING, woodTheme=s.woodTheme, best = repository.best(), soundEnabled=repository.sound(), guidesEnabled=s.guidesEnabled, message = "捏住最高完整层下方的一块木块") }
            GameEvent.Pause -> if (s.phase in listOf(Phase.PLAYING, Phase.HOLDING, Phase.SETTLING)) { previous = s.phase; mutable.value = s.copy(phase = Phase.PAUSED, message = "局面已冻结，准备好后继续") }
            GameEvent.Resume -> mutable.value = s.copy(phase = if (s.selected != null) Phase.PLAYING else previous, message = if (s.selected != null) "重新捏住刚才的木块" else "慢慢来，找到平衡")
            GameEvent.Help -> { if (s.phase != Phase.HELP) helpPrevious = s.phase; mutable.value = s.copy(phase = Phase.HELP) }
            GameEvent.RestartPrompt -> { restartPrevious=s.phase; mutable.value = s.copy(phase = Phase.RESTART) }
            GameEvent.TrackingLost -> { previous = Phase.PLAYING; mutable.value = s.copy(phase = Phase.TRACKING_LOST, message = "抓取中断，当前局面已冻结") }
        }
    }
    fun selected(id: Int) { mutable.value = mutable.value.copy(phase = Phase.HOLDING, selected = id, message = "轻轻抽出，再移到塔顶") }
    fun released() { mutable.value = mutable.value.copy(phase = Phase.SETTLING, message = "等待木块稳定…") }
    fun cancelled() { mutable.value = mutable.value.copy(phase = Phase.PLAYING, selected = null, message = "已放回原位，选择另一块试试") }
    fun invalid() { mutable.value = mutable.value.copy(phase = Phase.PLAYING, message = "尚未在塔顶放稳，请继续调整同一块") }
    fun scored(snapshot: String) {
        if(mutable.value.selected == null || mutable.value.phase == Phase.RESULT)return
        val s = mutable.value; val score = s.score + 1
        if (!s.recovered) repository.saveBest(score)
        repository.saveRound("$score|${s.seconds}|$snapshot")
        mutable.value = s.copy(phase = Phase.PLAYING, score = score, best = repository.best(), selected = null, message = "稳稳放好！继续挑战")
    }
    fun tick(seconds:Int=1) { val s = mutable.value; if (s.phase in listOf(Phase.PLAYING, Phase.HOLDING, Phase.SETTLING)) mutable.value = s.copy(seconds = s.seconds + seconds.coerceAtLeast(0)) }
    fun setTheme(theme:WoodTheme) { repository.saveTheme(theme.name);mutable.value=mutable.value.copy(woodTheme=theme) }
    fun settings() { helpPrevious=mutable.value.phase;mutable.value=mutable.value.copy(phase=Phase.SETTINGS) }
    fun setSound(enabled:Boolean) { repository.saveSound(enabled);mutable.value=mutable.value.copy(soundEnabled=enabled) }
    fun setGuides(enabled:Boolean) { mutable.value=mutable.value.copy(guidesEnabled=enabled) }
    fun hasSavedRound() = repository.round()?.split("|")?.getOrNull(2) == TowerScene.SNAPSHOT_TAG
    fun savedRound() = repository.round().orEmpty()
    fun restored(score: Int, seconds: Int) { mutable.value = mutable.value.copy(phase=Phase.PAUSED,score=score,seconds=seconds,selected=null,recovered=true,message="已恢复上次完成的回合，本局为恢复练习，不更新纪录");previous=Phase.PLAYING }
    fun saveInitial(snapshot: String) { repository.saveRound("0|0|$snapshot") }
    fun cancelRestart() { mutable.value=mutable.value.copy(phase=restartPrevious) }
    fun undoAvailable(available:Boolean) { mutable.value=mutable.value.copy(canUndo=available) }
    fun undoRestored(available:Boolean) { previous=Phase.PLAYING; mutable.value=mutable.value.copy(phase=Phase.PLAYING,selected=null,canUndo=available,message="已撤销，恢复到抓取前的局面（练习）") }
    fun closeHelp() { mutable.value=mutable.value.copy(phase=helpPrevious) }
    fun finished(reason: String) { val s = mutable.value; if(s.phase != Phase.RESULT) { repository.saveRound(""); mutable.value = s.copy(phase = Phase.RESULT, message = reason, selected = null) } }
}
