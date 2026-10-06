package com.kejing.towerwood.domain.usecase

import kotlin.math.abs
/** Placement rules; every length tolerance scales with the block size (1 = the original 75 × 25 × 15 mm block). */
class StackRulesUseCase(private val scale: Float = 1f) {
    fun canGrab(layer: Int, highestCompleteLayer: Int, selected: Int?, candidate: Int): Boolean =
        if (selected != null) selected == candidate else layer < highestCompleteLayer
    fun isSupported(x: Float, y: Float, z: Float, targetY: Float, tilt: Float): Boolean =
        abs(x) < .038f * scale && abs(z) < .038f * scale && abs(y - targetY) < .004f * scale && abs(tilt) < 12f
    fun hasFallen(y: Float, baseY: Float, initialLayer: Int, tilt: Float): Boolean =
        y < baseY - .008f * scale || (initialLayer > 0 && abs(tilt) > 45f)
}
