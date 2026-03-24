package com.ricelab.cairclient.maritime_experiment

import com.ricelab.cairclient.R

object MaritimeExperimentResolver {

    fun fromId(id: Int): MaritimeExperimentConfig {
        return when (id) {
            1 -> MaritimeExperimentConfig(1, false, MaritimeTone.NEUTRAL, R.drawable.qr_code1)
            2 -> MaritimeExperimentConfig(2, false, MaritimeTone.WITTY, R.drawable.qr_code2)
            3 -> MaritimeExperimentConfig(3, false, MaritimeTone.ASSERTIVE, R.drawable.qr_code3)
            4 -> MaritimeExperimentConfig(4, true, MaritimeTone.NEUTRAL, R.drawable.qr_code4)
            5 -> MaritimeExperimentConfig(5, true, MaritimeTone.WITTY, R.drawable.qr_code5)
            6 -> MaritimeExperimentConfig(6, true, MaritimeTone.ASSERTIVE, R.drawable.qr_code6)
            else -> MaritimeExperimentConfig(1, false, MaritimeTone.NEUTRAL, R.drawable.qr_code1)
        }
    }
}