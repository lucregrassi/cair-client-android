package com.ricelab.cairclient.libraries

import java.util.Random

data class DialogueNuances (
    val flags: Map<String, List<Int>> = emptyMap(),
    val values: Map<String, List<String>> = emptyMap()
) {
    override fun toString(): String {
        return """
            Nuances(
                flags=${flags.entries.joinToString { "${it.key}=${it.value}" }},
                values=${values.entries.joinToString { "${it.key}=${it.value}" }}
            )
        """.trimIndent()
    }
}
