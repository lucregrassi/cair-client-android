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

    fun toPort(mode: AppMode): Int {
        return when (mode) {
            AppMode.DEFAULT -> 12345
            AppMode.PARAPLEGIA -> 12346
            AppMode.MARITIME_STATION -> 12347
            AppMode.DELIRIUM -> 12349
            AppMode.APATHY -> 12351
        }
    }

    fun displayName(mode: AppMode): String {
        return when (mode) {
            AppMode.DEFAULT -> "Default"
            AppMode.PARAPLEGIA -> "Paraplegia"
            AppMode.MARITIME_STATION -> "Stazione Marittima"
            AppMode.DELIRIUM -> "Delirium"
            AppMode.APATHY -> "Apatia"
        }
    }

    fun selectableModes(): List<AppMode> {
        return listOf(
            AppMode.DEFAULT,
            AppMode.PARAPLEGIA,
            AppMode.MARITIME_STATION,
            AppMode.DELIRIUM,
            AppMode.APATHY
        )
    }
}