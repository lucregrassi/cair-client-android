package com.ricelab.cairclient.libraries

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
import com.aldebaran.qi.sdk.`object`.holder.Holder

private const val TAG = "PepperInterface"
private var holder: Holder? = null

class PepperInterface(
    private var qiContext: QiContext? = null
) {
    private var goToFuture: Future<Void>? = null

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

                    val say = SayBuilder.with(qiContext)
                        .withText(text)
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
                targetFrame.update(robotFrame, transform, System.currentTimeMillis())

                val goTo = GoToBuilder.with(qiContext)
                    .withFrame(targetFrame.frame())
                    .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
                    .withMaxSpeed(0.2F) //0.72km/h
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

    fun holdBaseMovement() {
        holder = HolderBuilder.with(qiContext)
            .withDegreesOfFreedom(DegreeOfFreedom.ROBOT_FRAME_ROTATION)
            .build()
        holder?.async()?.hold()
    }

    fun releaseBaseMovement() {
        holder?.async()?.release()
    }
}