package com.ricelab.cairclient.maritime_experiment

import com.ricelab.cairclient.conversation.DialogueNuances

object MaritimeNuanceOverrideHelper {

    fun applyTone(baseNuances: DialogueNuances, tone: MaritimeTone): DialogueNuances {
        val toneValue = when (tone) {
            MaritimeTone.NEUTRAL -> "neutral"
            MaritimeTone.WITTY -> "witty"
            MaritimeTone.ASSERTIVE -> "assertive"
        }

        val updatedValues = baseNuances.values.toMutableMap()
        updatedValues["tone"] = List(9) { toneValue }

        return baseNuances.copy(values = updatedValues)
    }
}