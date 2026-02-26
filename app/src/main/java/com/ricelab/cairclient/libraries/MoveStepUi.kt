package com.ricelab.cairclient.libraries

data class MoveStepUi(
    val x: Double,
    val y: Double,
    val thetaDeg: Double,
    val dwellMs: Long,
    val mustReach: Boolean = false,
    val mustReachTimeoutSec: Long? = null
) {
    fun isValid(): Boolean {
        if (dwellMs <= 0L) return false
        if (x == 0.0 && y == 0.0 && thetaDeg == 0.0) return false

        // se mustReach è ON allora il timeout deve esserci ed essere > 0
        if (mustReach) {
            val t = mustReachTimeoutSec ?: return false
            if (t <= 0L) return false
        }
        return true
    }

    fun toRuntime(): MoveStep {
        val thetaRad = Math.toRadians(thetaDeg)
        return MoveStep(
            x = x,
            y = y,
            thetaRad = thetaRad,
            dwellMs = dwellMs,
            mustReach = mustReach,
            mustReachTimeoutMs = if (mustReach) (mustReachTimeoutSec!! * 1000L) else null
        )
    }
}