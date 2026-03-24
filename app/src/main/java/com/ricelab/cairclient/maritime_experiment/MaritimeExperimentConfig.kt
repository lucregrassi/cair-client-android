package com.ricelab.cairclient.maritime_experiment

data class MaritimeExperimentConfig(
    val id: Int,
    val movementEnabled: Boolean,
    val tone: MaritimeTone,
    val qrDrawableRes: Int
)