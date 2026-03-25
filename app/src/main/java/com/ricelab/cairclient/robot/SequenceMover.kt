package com.ricelab.cairclient.robot

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

    fun start(resetIndex: Boolean = false) {
        if (resetIndex) {
            idx = 0
            onLog("SeqMove -> start(resetIndex=true), idx=0")
        }

        if (job?.isActive == true) return

        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!enabled || steps.isEmpty() || !pepper.hasHomeFrame() || !canMoveNow()) {
                    delay(300)
                    continue
                }

                val currentIndex = idx % steps.size
                val s = steps[currentIndex]

                try {
                    onLog("SeqMove -> step ${currentIndex + 1}/${steps.size}: GoTo (${s.x}, ${s.y}, θ=${s.thetaRad})")

                    val ok = pepper.goToPoseInHome(
                        Pose2D(s.x, s.y, s.thetaRad),
                        speed,
                        s.mustReach,
                        timeoutMs = s.mustReachTimeoutMs
                    )

                    if (ok) {
                        idx++

                        val dwell = s.dwellMs.coerceAtLeast(0L)
                        onLog("SeqMove -> dwell ${dwell}ms")
                        delay(dwell)
                    } else {
                        onLog("SeqMove -> move failed or not started at step ${currentIndex + 1}")
                        delay(1000)
                    }

                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    onLog("SeqMove -> error at step ${currentIndex + 1}: ${t.message}")
                    delay(1000)
                }
            }
        }
    }

    fun stop(resetIndex: Boolean = false) {
        job?.cancel()
        job = null

        if (resetIndex) {
            idx = 0
            onLog("SeqMove -> stop(resetIndex=true), idx=0")
        }
    }

    fun resetIndex() {
        idx = 0
        onLog("SeqMove -> resetIndex(), idx=0")
    }
}