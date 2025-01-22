package com.ricelab.cairclient.libraries

data class SpeakersInfo(
    var speakers: MutableMap<String, MutableMap<String, Any?>> = mutableMapOf()
) {
    // Convert to map for serialization
    fun toMap(): Map<String, Map<String, Any?>> {
        return speakers
    }

    companion object {
        // Factory method to initialize from a Map
        fun fromMap(speakersInfoMap: Map<String, Map<String, Any?>>): SpeakersInfo {
            // Convert the inner maps to MutableMap when initializing
            val mutableSpeakers = speakersInfoMap.mapValues { it.value.toMutableMap() }.toMutableMap()
            return SpeakersInfo(mutableSpeakers)
        }
    }
}