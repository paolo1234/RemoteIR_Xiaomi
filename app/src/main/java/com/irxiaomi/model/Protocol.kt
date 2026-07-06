package com.irxiaomi.model

/**
 * Protocolli IR supportati
 */
enum class Protocol(
    val displayName: String,
    val defaultFrequency: Int,
    val minBits: Int,
    val maxBits: Int
) {
    NEC("NEC", 38000, 32, 64),
    NEC_EXT("NEC Extended", 38000, 32, 64),
    SAMSUNG("Samsung", 38000, 32, 32),
    SONY("Sony", 40000, 12, 20),
    RC5("RC5", 36000, 14, 14),
    RC6("RC6", 36000, 20, 64),
    PANASONIC("Panasonic", 37000, 48, 48),
    JVC("JVC", 38000, 16, 16),
    SHARP("Sharp", 38000, 15, 15),
    DENON("Denon", 38000, 15, 15),
    PRONTO("Pronto", 38000, 0, 0),       // Formato raw
    RAW("Raw", 38000, 0, 0),             // Pattern raw generico
    AC_RAW("AC Raw", 38000, 0, 0),       // Per condizionatori (pattern lunghi)
    UNKNOWN("Sconosciuto", 38000, 0, 0);

    companion object {
        fun fromString(name: String): Protocol {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
