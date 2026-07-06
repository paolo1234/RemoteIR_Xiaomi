package com.irxiaomi.sync

import android.util.Log
import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import com.irxiaomi.ir.PatternGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Importa codici IR dal database FLIRC.
 *
 * FLIRC (https://flirc.tv) è un ricevitore/trasmettitore IR USB.
 * Il database FLIRC contiene migliaia di codici per TV, AC, audio, ecc.
 * Il formato export di FLIRC è JSON.
 *
 * FLIRC Export formato JSON:
 * {
 *   "version": 2,
 *   "device": "Samsung TV",
 *   "brand": "Samsung",
 *   "protocol": "NEC",
 *   "codes": [
 *     { "name": "Power", "address": "0xE0E0", "command": "0x40BF" },
 *     ...
 *   ]
 * }
 *
 * Fonte: https://github.com/flirc/flirc-data
 */
class FlircImporter {

    companion object {
        private const val TAG = "FlircImporter"

        /** URL del repository FLIRC data (open source) */
        private const val FLIRC_DATA_URL = "https://raw.githubusercontent.com/flirc/flirc-data/master/"

        /** Nome file index per FLIRC */
        private const val FLIRC_INDEX = "index.json"
    }

    data class ImportProgress(
        val currentFile: String = "",
        val totalDevices: Int = 0,
        val imported: Int = 0,
        val errors: Int = 0,
        val isComplete: Boolean = false
    )

    /**
     * Importa dal formato JSON FLIRC.
     * Formato supportato:
     * {
     *   "version": 1,
     *   "device": "...",
     *   "codes": [ { "name": "...", "address": "...", "command": "...", ... } ]
     * }
     */
    suspend fun importFromJson(
        jsonContent: String,
        dao: IrCodeDao
    ): Int = withContext(Dispatchers.IO) {
        var count = 0

        try {
            val root = JSONObject(jsonContent)

            // FLIRC formato standard
            val device = root.optString("device", "")
            val brand = root.optString("brand", extractBrand(device))
            val protocol = root.optString("protocol", "NEC")
            val frequency = root.optInt("frequency", 38000)
            val version = root.optInt("version", 1)

            // Leggi codici
            val codesArr = root.optJSONArray("codes") ?: root.optJSONArray("data")
                ?: root.optJSONArray("remotes") ?: return@withContext 0

            for (i in 0 until codesArr.length()) {
                try {
                    val obj = codesArr.getJSONObject(i)
                    val entity = parseCode(obj, brand, device, protocol, frequency)
                    if (entity != null) {
                        dao.insert(entity)
                        count++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing FLIRC code $i", e)
                }
            }

            Log.i(TAG, "Importati $count codici FLIRC per $brand $device")

        } catch (e: Exception) {
            Log.e(TAG, "Error importing FLIRC JSON", e)
        }

        count
    }

    /**
     * Importa dal repository FLIRC ufficiale (GitHub).
     * Scarica index.json e poi tutti i file dei dispositivi.
     */
    suspend fun importFromRepository(
        dao: IrCodeDao,
        onProgress: (ImportProgress) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        var errors = 0

        try {
            // Per ora stub - in futuro si può scaricare da GitHub
            // val indexContent = URL(FLIRC_DATA_URL + FLIRC_INDEX).readText()
            // val index = JSONArray(indexContent)
            // for (i in 0 until index.length()) { ... }

            Log.i(TAG, "FLIRC repository import stub - usa importFromJson per file locali")
            onProgress(ImportProgress(isComplete = true))

        } catch (e: Exception) {
            Log.e(TAG, "Error importing FLIRC repository", e)
            errors++
        }

        count
    }

    /**
     * Converte un oggetto JSON FLIRC in IrCodeEntity.
     */
    private fun parseCode(
        obj: JSONObject,
        defaultBrand: String,
        defaultDevice: String,
        defaultProtocol: String,
        defaultFrequency: Int
    ): IrCodeEntity? {
        val name = obj.optString("name", obj.optString("function", ""))
            .ifEmpty { return null }

        val brand = obj.optString("brand", defaultBrand)
        val deviceType = obj.optString("device_type", obj.optString("type", defaultDevice))
            .let { normalizeDeviceType(it) }

        // Leggi address e command (possono essere "0xE0E0" o numero)
        val addressStr = obj.optString("address", obj.optString("pre_data", ""))
        val commandStr = obj.optString("command", obj.optString("data", obj.optString("code", "")))

        val address = addressStr.removePrefix("0x").toLongOrNull(16)
        val command = commandStr.removePrefix("0x").toLongOrNull(16)

        // Se non ci sono address/command, prova pattern raw
        val patternStr = obj.optString("pattern", obj.optString("raw", ""))

        val protocol = obj.optString("protocol", defaultProtocol)
        val frequency = obj.optInt("frequency", defaultFrequency)

        // Determina il device type dal nome se non specificato
        val finalDeviceType = if (deviceType == "OTHER" || deviceType == "UNKNOWN") {
            guessDeviceFromName(name, brand)
        } else deviceType

        // Genera pattern se abbiamo address+command ma non pattern
        val pattern = if (patternStr.isNotBlank()) {
            patternStr
        } else if (address != null && command != null) {
            try {
                val p = PatternGenerator.generate(protocol, address, command, frequency)
                if (p != null) IrCodeEntity.patternToString(p) else ""
            } catch (e: Exception) { "" }
        } else ""

        return IrCodeEntity(
            name = name,
            displayName = name.replace("_", " ").lowercase()
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            brand = brand,
            deviceType = finalDeviceType,
            protocol = protocol,
            frequency = frequency,
            pattern = pattern,
            address = address,
            command = command,
            category = "$brand-$finalDeviceType",
            tags = "$brand,$finalDeviceType,$protocol,flirc",
            source = "flirc",
            isVerified = obj.optBoolean("verified", obj.optBoolean("is_verified", false))
        )
    }

    private fun normalizeDeviceType(type: String): String {
        val upper = type.uppercase().trim()
        return when {
            upper.contains("TV") || upper.contains("TELEVISION") -> "TV"
            upper.contains("AC") || upper.contains("AIR") || upper.contains("CONDITIONER") -> "AC"
            upper.contains("AUDIO") || upper.contains("SOUND") || upper.contains("SPEAKER") -> "AUDIO"
            upper.contains("STB") || upper.contains("SET") || upper.contains("DECODER") || upper.contains("BOX") -> "SET_TOP_BOX"
            upper.contains("DVD") || upper.contains("BLU") || upper.contains("PLAYER") -> "DVD"
            upper.contains("FAN") -> "FAN"
            upper.contains("LIGHT") || upper.contains("LAMP") -> "LIGHT"
            upper.contains("PROJECTOR") -> "PROJECTOR"
            upper.contains("RECEIVER") || upper.contains("AMPLIFIER") -> "RECEIVER"
            else -> type.uppercase()
        }
    }

    private fun guessDeviceFromName(name: String, brand: String): String {
        val upper = "$brand $name".uppercase()
        return when {
            upper.contains("TV") -> "TV"
            upper.contains("AC") || upper.contains("AIR") -> "AC"
            upper.contains("SOUND") || upper.contains("AUDIO") || upper.contains("SPEAKER") -> "AUDIO"
            upper.contains("DVD") || upper.contains("PLAYER") -> "DVD"
            upper.contains("FAN") || upper.contains("VENTILATOR") -> "FAN"
            else -> "OTHER"
        }
    }

    private fun extractBrand(device: String): String {
        val knownBrands = listOf(
            "Samsung", "LG", "Sony", "Panasonic", "Philips", "Daikin",
            "Mitsubishi", "Sharp", "Toshiba", "Hitachi", "Hisense",
            "TCL", "Haier", "Gree", "Midea", "Bose", "Yamaha", "Denon",
            "Onkyo", "JVC", "Marantz", "Pioneer", "Kenwood", "NEC",
            "RCA", "Sanyo", "Funai", "Xiaomi", "Huawei", "Google"
        )

        for (brand in knownBrands) {
            if (device.contains(brand, ignoreCase = true)) return brand
        }

        // Prendi la prima parola
        return device.split(" ").firstOrNull()?.trim() ?: "Unknown"
    }
}
