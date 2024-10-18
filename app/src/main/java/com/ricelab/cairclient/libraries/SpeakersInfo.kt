package com.ricelab.cairclient.libraries

data class SpeakersInfo(
    var speakers: MutableMap<String, Map<String, Any?>> = mutableMapOf()
) {
    // Convert to map for serialization
    fun toMap(): Map<String, Map<String, Any?>> {
        return speakers
    }

    companion object {
        // Factory method to initialize from a Map
        fun fromMap(speakersInfoMap: Map<String, Map<String, Any?>>): SpeakersInfo {
            return SpeakersInfo(speakersInfoMap.toMutableMap())
        }
    }
}