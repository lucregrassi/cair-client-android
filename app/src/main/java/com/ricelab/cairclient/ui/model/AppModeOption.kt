package com.ricelab.cairclient.ui.model

import com.ricelab.cairclient.config.AppMode

data class AppModeOption(
    val mode: AppMode,
    val label: String
) {
    override fun toString(): String = label
}