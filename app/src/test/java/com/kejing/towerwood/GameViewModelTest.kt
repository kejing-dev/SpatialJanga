package com.kejing.towerwood
import com.kejing.towerwood.ui.game.*
import com.kejing.towerwood.data.repository.GameRepository
import org.junit.Assert.*
import org.junit.Test
class GameViewModelTest {
    private class Repo:GameRepository {var record=0;var saved="";var skin="NATURAL";override fun theme()=skin;override fun saveTheme(value:String){skin=value};override fun best()=record;override fun saveBest(score:Int){record=maxOf(record,score)};override fun saveRound(value:String){saved=value};override fun round()=saved}
    @Test fun loadedGameWaitsForStart(){val vm=GameViewModel(Repo());vm.ready();assertEquals(Phase.HOME,vm.uiState.value.phase)}
    @Test fun pauseStopsClock(){val vm=GameViewModel(Repo());vm.event(GameEvent.Start);vm.tick();vm.event(GameEvent.Pause);vm.tick();assertEquals(1,vm.uiState.value.seconds)}
    @Test fun interruptedGrabKeepsSameBlock(){val vm=GameViewModel(Repo());vm.event(GameEvent.Start);vm.selected(4);vm.event(GameEvent.TrackingLost);vm.event(GameEvent.Resume);assertEquals(4,vm.uiState.value.selected);assertEquals(Phase.PLAYING,vm.uiState.value.phase)}
    @Test fun successfulPlacementPersistsRecord(){val repo=Repo();val vm=GameViewModel(repo);vm.event(GameEvent.Start);vm.selected(1);vm.released();vm.scored("pose");assertEquals(1,repo.record);assertEquals("1|0|pose",repo.saved);assertNull(vm.uiState.value.selected)}
    @Test fun failureIsIdempotent(){val vm=GameViewModel(Repo());vm.finished("first");vm.finished("second");assertEquals("first",vm.uiState.value.message)}
    @Test fun duplicateScoreDoesNotCountTwice(){val vm=GameViewModel(Repo());vm.event(GameEvent.Start);vm.selected(1);vm.released();vm.scored("pose");vm.scored("pose");assertEquals(1,vm.uiState.value.score)}
    @Test fun recoveredRoundDoesNotChangeBest(){val repo=Repo();val vm=GameViewModel(repo);vm.restored(4,20);vm.event(GameEvent.Resume);vm.selected(2);vm.released();vm.scored("pose");assertEquals(0,repo.record)}
    @Test fun restartClearsRoundKeepsBest(){val repo=Repo();repo.record=7;val vm=GameViewModel(repo);vm.event(GameEvent.Start);assertEquals(0,vm.uiState.value.score);assertEquals(7,vm.uiState.value.best)}
    @Test fun restoreClearsPendingSelection(){val vm=GameViewModel(Repo());vm.event(GameEvent.Start);vm.selected(49);vm.restored(0,0);vm.undoRestored(false);assertNull(vm.uiState.value.selected);assertEquals(Phase.PLAYING,vm.uiState.value.phase)}
    @Test fun restartCancelReturnsToResult(){val vm=GameViewModel(Repo());vm.finished("drop");vm.event(GameEvent.RestartPrompt);vm.cancelRestart();assertEquals(Phase.RESULT,vm.uiState.value.phase)}
    @Test fun restartClearsUndoAvailability(){val vm=GameViewModel(Repo());vm.undoAvailable(true);vm.event(GameEvent.Start);assertFalse(vm.uiState.value.canUndo)}
    @Test fun themePersistsAndSurvivesRestart(){val repo=Repo();val vm=GameViewModel(repo);vm.setTheme(WoodTheme.PASTEL);vm.event(GameEvent.Start);assertEquals(WoodTheme.PASTEL,vm.uiState.value.woodTheme);assertEquals(WoodTheme.PASTEL,GameViewModel(repo).uiState.value.woodTheme)}
    @Test fun unknownThemeFallsBackToNatural(){assertEquals(WoodTheme.NATURAL,WoodTheme.fromSaved("missing"))}
}
