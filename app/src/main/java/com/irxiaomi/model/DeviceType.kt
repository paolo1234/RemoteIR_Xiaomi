package com.irxiaomi.model

/**
 * Tipi di dispositivi supportati
 */
enum class DeviceType(
    val displayName: String,
    val icon: String,    // Nome icona Material Icons
    val isComplex: Boolean = false  // True per AC, che richiedono UI speciale
) {
    TV("TV", "tv"),
    AC("Condizionatore", "ac_unit", isComplex = true),
    SET_TOP_BOX("Decoder", "settop_component"),
    AUDIO("Audio/Soundbar", "speaker"),
    FAN("Ventilatore", "air"),
    LIGHT("Luce", "lightbulb"),
    PROJECTOR("Proiettore", "projector"),
    DVD("DVD/Blu-ray", "disc_full"),
    STREAMER("Streamer", "stream"),
    IPTV("IPTV", "live_tv"),
    RECEIVER("Ricevitore", "surround_sound"),
    AMPLIFIER("Amplificatore", "volume_up"),
    AIR_PURIFIER("Purificatore", "air_purifier"),
    DEHUMIDIFIER("Deumidificatore", "water_drop"),
    HEATER("Stufa", "fireplace"),
    FAN_HEATER("Ventilconvettore", "heating"),
    CURTAIN("Tenda/Cancello", "curtains"),
    CAMERA("Telecamera", "videocam"),
    OTHER("Altro", "devices_other");

    companion object {
        fun fromString(name: String): DeviceType {
            return entries.find { 
                it.name.equals(name, ignoreCase = true) || 
                it.displayName.equals(name, ignoreCase = true) 
            } ?: OTHER
        }
    }
}
