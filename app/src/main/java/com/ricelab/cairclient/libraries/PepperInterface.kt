package com.ricelab.cairclient.libraries

import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PepperInterface"

class PepperInterface(
    private var qiContext: QiContext? = null
) {

    fun setContext(qiContext: QiContext?) {
        this.qiContext = qiContext
    }

    suspend fun sayMessage(text: String) {
        // Build the Say action off the main thread
        withContext(Dispatchers.IO) {
            if (qiContext != null) {
                try {
                    Log.i("MainActivity", "Try sayMessage")
                    val say = SayBuilder.with(qiContext)
                        .withText(text)
                        .build()
                    say.run()
                } catch (e: Exception) {
                    Log.e(TAG, "Errore durante Say: ${e.message}")
                }
            } else {
                Log.e(TAG, "Il focus non è disponibile, Say non può essere eseguito.")
            }
        }
    }

    // Function to perform animations
    suspend fun performAnimation(animationRes: Int) {
        if (qiContext == null) {
            Log.e(TAG, "QiContext is not initialized.")
            return
        }

        try {
            // Build the animation off the main thread
            val myAnimation: Animation = withContext(Dispatchers.IO) {
                AnimationBuilder.with(qiContext)
                    .withResources(animationRes)
                    .build()
            }

            // Build the Animate action off the main thread
            val animate: Animate = withContext(Dispatchers.IO) {
                AnimateBuilder.with(qiContext)
                    .withAnimation(myAnimation)
                    .build()
            }

            Log.i(TAG, "About to start Animation")
            // Run the animation synchronously (blocks until the animation is complete)
            animate.run()

            // Once the animation completes successfully, you can proceed
            Log.i(TAG, "Animation completed successfully.")
        } catch (e: Exception) {
            // Handle errors in the animation process
            Log.e(TAG, "Error during animation: ${e.message}", e)
        }
    }
}