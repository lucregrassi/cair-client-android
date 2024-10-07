package com.ricelab.cairclient.libraries

data class SpeakerInfo(
    var profileId: String? = null,
    var name: String? = null,
    var gender: String? = null,
    var age: Int? = null
) {
    // Secondary constructor to allow initialization from a map (like Python's "d" parameter)
    constructor(d: Map<String, Any>?) : this() {
        d?.let {
            profileId = it["profileId"] as? String
            name = it["name"] as? String
            gender = it["gender"] as? String
            age = (it["age"] as? Double)?.toInt()  // JSON parsing might give age as Double
        }
    }

    // Method to convert this object to a Map (similar to to_dict() in Python)
    fun toDict(): Map<String, Any?> {
        return mapOf(
            "profileId" to profileId,
            "name" to name,
            "gender" to gender,
            "age" to age
        )
    }
}