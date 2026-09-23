package com.kejing.towerwood
import com.kejing.towerwood.domain.usecase.StackRulesUseCase
import org.junit.Assert.*
import org.junit.Test
class StackRulesUseCaseTest {
    private val rules=StackRulesUseCase()
    @Test fun highestCompleteLayerCannotBeTaken() { assertFalse(rules.canGrab(17,17,null,51)); assertTrue(rules.canGrab(16,17,null,48)) }
    @Test fun extractedBlockLocksTurn() { assertFalse(rules.canGrab(2,17,8,6)); assertTrue(rules.canGrab(2,17,8,8)) }
    @Test fun supportMustBeAtTopAndWithinFootprint() { assertTrue(rules.isSupported(0f,.3f,0f,.3f,0f));assertFalse(rules.isSupported(.08f,.3f,0f,.3f,0f));assertFalse(rules.isSupported(0f,.15f,0f,.3f,0f)) }
    @Test fun tiltedPlacementCannotScore() { assertFalse(rules.isSupported(0f,.3f,0f,.3f,30f)) }
    @Test fun initialBaseContactIsNotFailure() { assertFalse(rules.hasFallen(.02f,0f,0,0f));assertTrue(rules.hasFallen(-.02f,0f,0,0f)) }
    @Test fun upperLayerTippingIsFailure() { assertTrue(rules.hasFallen(.2f,0f,5,65f)) }
}
