package com.kejing.towerwood.domain.usecase

import kotlin.math.abs
class StackRulesUseCase {
    fun canGrab(layer: Int, highestCompleteLayer: Int, selected: Int?, candidate: Int): Boolean =
        if (selected != null) selected == candidate else layer < highestCompleteLayer
    fun isSupported(x: Float, y: Float, z: Float, targetY: Float, tilt: Float): Boolean =
        abs(x) < .038f && abs(z) < .038f && abs(y - targetY) < .004f && abs(tilt) < 12f
    fun hasFallen(y: Float, baseY: Float, initialLayer: Int, tilt: Float): Boolean =
        y < baseY - .008f || (initialLayer > 0 && abs(tilt) > 45f)
}
