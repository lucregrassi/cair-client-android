package com.ricelab.cairclient.libraries

import kotlinx.coroutines.*

data class MoveStep(
    val x: Double,
    val y: Double,
    val thetaRad: Double,
    val dwellMs: Long,
    val mustReach: Boolean = false,
    val mustReachTimeoutMs: Long? = null
)

class SequenceMover(
    private val scope: CoroutineScope,
    private val pepper: PepperInterface,
    private val canMoveNow: () -> Boolean,
    private val onLog: (String) -> Unit = {}
) {
    private var job: Job? = null
    private var idx = 0

    @Volatile var enabled: Boolean = true
    @Volatile var speed: Float = 0.2f

    var steps: List<MoveStep> = emptyList()

    fun start() {
        if (job?.isActive == true) return

        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!enabled || steps.isEmpty() || !pepper.hasHomeFrame() || !canMoveNow()) {
                    delay(300)
                    continue
                }

                val s = steps[idx % steps.size]
                idx++

                try {
                    pepper.releaseBaseMovement()

                    onLog("SeqMove -> GoTo (${s.x}, ${s.y}, θ=${s.thetaRad})")
                    pepper.goToPoseInHome(
                        Pose2D(s.x, s.y, s.thetaRad),
                        speed,
                        s.mustReach,
                        timeoutMs = s.mustReachTimeoutMs
                    )

                    pepper.holdBaseMovement()

                    val dwell = s.dwellMs.coerceAtLeast(0L)
                    onLog("SeqMove -> dwell ${dwell}ms")
                    delay(dwell)

                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    onLog("SeqMove -> error ${t.message}")
                    try { pepper.holdBaseMovement() } catch (_: Throwable) {}
                    delay(1000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun resetIndex() { idx = 0 }
}