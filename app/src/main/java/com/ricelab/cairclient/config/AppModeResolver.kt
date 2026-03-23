package com.ricelab.cairclient.config

object AppModeResolver {

    fun fromPort(port: Int): AppMode {
        return when (port) {
            12345 -> AppMode.DEFAULT
            12346 -> AppMode.PARAPLEGIA
            12347 -> AppMode.MARITIME_STATION
            12349 -> AppMode.DELIRIUM
            12351 -> AppMode.APATHY
            else -> AppMode.DEFAULT
        }
    }
}