package com.ricelab.cairclient.robot

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.builder.*
import com.ricelab.cairclient.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aldebaran.qi.sdk.builder.HolderBuilder
import com.aldebaran.qi.sdk.`object`.autonomousabilities.DegreeOfFreedom
import com.aldebaran.qi.sdk.`object`.conversation.Phrase
import com.aldebaran.qi.sdk.`object`.holder.Holder

import com.aldebaran.qi.sdk.`object`.actuation.FreeFrame
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.builder.TransformBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withTimeoutOrNull

data class Pose2D(val x: Double, val y: Double, val yawRad: Double)

private const val TAG = "PepperInterface"
private var holder: Holder? = null

class PepperInterface(
    private var qiContext: QiContext? = null,
    private var voiceSpeed: Int = 100,
    private var voicePitch: Int = 100
) {
    private var goToFuture: Future<Void>? = null
    private var homeFreeFrame: FreeFrame? = null

    fun initHomeFrame() {
        val ctx = qiContext ?: return
        val mapping = ctx.mapping
        val robotFrame = ctx.actuation.robotFrame()
        homeFreeFrame = mapping.makeFreeFrame().apply {
            // home = posa attuale del robot
            update(robotFrame, TransformBuilder.create().from2DTransform(0.0, 0.0, 0.0), 0L)
        }
        Log.i(TAG, "Home frame initialized")
    }

    fun hasHomeFrame(): Boolean = homeFreeFrame != null

    suspend fun goToPoseInHome(
        pose: Pose2D,
        speed: Float = 0.2f,
        mustReach: Boolean = false,
        timeoutMs: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val ctx = qiContext ?: return@withContext false
        val home = homeFreeFrame ?: return@withContext false

        val mapping = ctx.mapping
        val target = mapping.makeFreeFrame()
        val tr = TransformBuilder.create().from2DTransform(pose.x, pose.y, pose.yawRad)
        target.update(home.frame(), tr, 0L)

        val policy = if (mustReach) PathPlanningPolicy.GET_AROUND_OBSTACLES
        else PathPlanningPolicy.STRAIGHT_LINES_ONLY

        val goTo = GoToBuilder.with(ctx)
            .withFrame(target.frame())
            .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
            .withMaxSpeed(speed)
            .withPathPlanningPolicy(policy)
            .build()

        goToFuture = goTo.async().run()

        try {
            val fut = goToFuture ?: return@withContext false

            val ok = if (mustReach && timeoutMs != null && timeoutMs > 0L) {
                // il blocco ritorna true se fut.awaitCancellable() termina prima del timeout,
                // in caso di timeout withTimeoutOrNull ritorna null -> il risultato finale è false
                withTimeoutOrNull(timeoutMs) {
                    fut.awaitCancellableVoid()
                    true
                } ?: false
            } else {
                fut.awaitCancellableVoid()
                true
            }

            if (!ok) {
                Log.w(TAG, "goToPoseInHome timeout after ${timeoutMs}ms -> cancelling")
                try { fut.requestCancellation() } catch (_: Throwable) {}
            }

            ok
        } catch (t: Throwable) {
            Log.e(TAG, "goToPoseInHome error", t)
            false
        } finally {
            goToFuture = null
        }
    }

    fun setContext(qiContext: QiContext?) {
        this.qiContext = qiContext
    }

    suspend fun sayMessage(text: String, lang: String) {
        if (qiContext != null) {
            try {
                withContext(Dispatchers.IO) {
                    val locale = if (lang == "en-US") {
                        Locale(Language.ENGLISH, Region.UNITED_STATES)
                    } else {
                        Locale(Language.ITALIAN, Region.ITALY)
                    }

                    // Create a phrase.
                    val phrase = Phrase("\\rspd=$voiceSpeed\\\\\\vct=$voicePitch\\\\$text")

                    val say = SayBuilder.with(qiContext)
                        .withPhrase(phrase)
                        .withLocale(locale)
                        .build()
                    say.run()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Say: ${e.message}")
            }
        } else {
            Log.e(TAG, "QiContext is not initialized. Cannot perform Say.")
        }
    }

    // Function to perform animations
    suspend fun performAnimation(action: String) {
        if (qiContext == null) {
            Log.e(TAG, "QiContext is not initialized.")
            return
        }

        var animationRes: Int
        when (action) {
            "hello" -> {
                animationRes = R.raw.hello
            }

            "attention" -> {
                animationRes = R.raw.hello
            }

            "hug" -> {
                animationRes = R.raw.hug
            }

            "handshake" -> {
                animationRes = R.raw.handshake
            }

            else -> {
                Log.e(TAG, "Action not found")
                return
            }
        }

        try {
            withContext(Dispatchers.IO) {
                val animation: Animation = AnimationBuilder.with(qiContext)
                    .withResources(animationRes)
                    .build()

                val animate: Animate = AnimateBuilder.with(qiContext)
                    .withAnimation(animation)
                    .build()

                Log.i(TAG, "Starting animation")
                animate.run()
                Log.i(TAG, "Animation completed successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during animation: ${e.message}", e)
        }
    }

    // Function to move the robot
    fun moveRobot(x: Double, y: Double, theta: Double) {
        if (qiContext == null) {
            Log.e(TAG, "QiContext is not initialized.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val actuation: Actuation = qiContext!!.actuation
                val robotFrame: Frame = actuation.robotFrame()
                val transform: Transform = TransformBuilder.create().from2DTransform(x, y, theta)
                val mapping: Mapping = qiContext!!.mapping

                val targetFrame: FreeFrame = mapping.makeFreeFrame()
                targetFrame.update(robotFrame, transform, 0L)

                val goTo = GoToBuilder.with(qiContext)
                    .withFrame(targetFrame.frame())
                    .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
                    .withMaxSpeed(0.2F)
                    .withPathPlanningPolicy(PathPlanningPolicy.STRAIGHT_LINES_ONLY)
                    .build()

                goToFuture = goTo.async().run()
                goToFuture?.thenConsume { future ->
                    if (future.isSuccess) {
                        Log.i(TAG, "GoTo action finished successfully.")
                    } else if (future.hasError()) {
                        Log.e(TAG, "GoTo action finished with error.", future.error)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during robot movement: ${e.message}", e)
            }
        }
    }

    // Function to stop the robot
    fun stopRobot() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                goToFuture?.requestCancellation()
                goToFuture = null
                Log.i(TAG, "Movement stopped.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during stopping robot: ${e.message}", e)
            }
        }
    }

    fun holdAutonomousBaseRotation() {
        val ctx = qiContext ?: return
        if (holder == null) {
            holder = HolderBuilder.with(ctx)
                .withDegreesOfFreedom(DegreeOfFreedom.ROBOT_FRAME_ROTATION)
                .build()
        }
        holder?.async()?.hold()
    }

    fun releaseAutonomousBaseRotation() {
        holder?.async()?.release()
    }

    fun setVoiceSpeed(speed: Int) {
        voiceSpeed = speed
    }

    fun setVoicePitch(pitch: Int) {
        voicePitch = pitch
    }

    private suspend fun Future<Void>.awaitCancellableVoid(): Unit =
        suspendCancellableCoroutine { cont ->
            this.thenConsume { f ->
                if (cont.isCancelled) return@thenConsume
                if (f.isSuccess) cont.resume(Unit)
                else cont.resumeWithException(f.error)
            }
            cont.invokeOnCancellation {
                try { this.requestCancellation() } catch (_: Throwable) {}
            }
        }
}