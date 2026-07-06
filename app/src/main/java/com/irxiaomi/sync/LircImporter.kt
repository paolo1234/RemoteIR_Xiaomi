package com.irxiaomi.sync

import android.content.Context
import android.util.Log
import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import com.irxiaomi.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Importa codici IR dal database LIRC (Linux Infrared Remote Control).
 * LIRC è il più grande database open-source di codici IR (~100.000+ dispositivi).
 *
 * Formato lircd.conf:
 * ```
 * begin remote
 *   name  SAMSUNG_BN59-00941A
 *   bits            16
 *   flags SPACE_ENC|CONST_LENGTH
 *   eps             30
 *   aeps          100
 *   header       4500 4500
 *   one           560 1690
 *   zero          560 560
 *   pre_data_bits   16
 *   pre_data       0xE0E0
 *   post_data_bits  16
 *   post_data      0xFFFF
 *   gap          45000
 *   repeat_bit     0
 *   frequency      38000
 *   begin codes
 *       KEY_POWER       0x40BF
 *       KEY_VOLUMEUP    0x609F
 *       ...
 *   end codes
 * end remote
 * ```
 */
class LircImporter(private val context: Context) {

    companion object {
        private const val TAG = "LircImporter"
        private const val ASSETS_PATH = "lirc"
        private const val PRELOADED_DB = "databases/preloaded.db"
    }

    data class ImportProgress(
        val totalFiles: Int = 0,
        val processedFiles: Int = 0,
        val totalCodes: Int = 0,
        val importedCodes: Int = 0,
        val errors: Int = 0,
        val currentFile: String = "",
        val isComplete: Boolean = false
    )

    /** Importa tutti i file LIRC dalla directory assets/lirc */
    suspend fun importFromAssets(
        dao: IrCodeDao,
        onProgress: (ImportProgress) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var progress = ImportProgress()
        var totalImported = 0

        try {
            val files = context.assets.list(ASSETS_PATH)
            if (files.isNullOrEmpty()) {
                Log.w(TAG, "No LIRC files found in assets/$ASSETS_PATH")
                // Prova a creare DB preloadato se non esiste
                generatePreloadedDb(dao)
                return@withContext 0
            }

            progress = progress.copy(
                totalFiles = files.size,
                currentFile = "Inizializzazione..."
            )

            for ((index, file) in files.withIndex()) {
                if (!file.endsWith(".conf") && !file.endsWith(".lircd")) continue

                progress = progress.copy(
                    processedFiles = index + 1,
                    currentFile = file
                )

                try {
                    val content = context.assets
                        .open("$ASSETS_PATH/$file")
                        .bufferedReader()
                        .use { it.readText() }

                    val codes = parseLircConf(content, file)
                    if (codes.isNotEmpty()) {
                        dao.insertAll(codes)
                        totalImported += codes.size
                        progress = progress.copy(
                            importedCodes = totalImported,
                            totalCodes = totalImported
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing LIRC file: $file", e)
                    progress = progress.copy(errors = progress.errors + 1)
                }

                onProgress(progress)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error importing LIRC", e)
            // Fallback: genera DB preloadato
            generatePreloadedDb(dao)
        }

        progress = progress.copy(isComplete = true, currentFile = "Completato!")
        onProgress(progress)

        totalImported
    }

    /** Parsing di un file lircd.conf */
    fun parseLircConf(content: String, filename: String): List<IrCodeEntity> {
        val codes = mutableListOf<IrCodeEntity>()

        // Estrai sezioni begin remote ... end remote
        val remoteSections = extractRemoteSections(content)

        for (remote in remoteSections) {
            val name = extractValue(remote, "name") ?: filename.removeSuffix(".conf")
            val bits = extractValue(remote, "bits")?.toIntOrNull() ?: 16
            val freq = extractValue(remote, "frequency")?.toIntOrNull() ?: 38000
            val protocol = detectProtocol(remote, name)

            // Timing
            val header = extractTimingPair(remote, "header")
            val one = extractTimingPair(remote, "one")
            val zero = extractTimingPair(remote, "zero")
            val preDataBits = extractValue(remote, "pre_data_bits")?.toIntOrNull() ?: 0
            val preData = extractValue(remote, "pre_data")?.removePrefix("0x")?.toLongOrNull(16) ?: 0L
            val postDataBits = extractValue(remote, "post_data_bits")?.toIntOrNull() ?: 0
            val postData = extractValue(remote, "post_data")?.removePrefix("0x")?.toLongOrNull(16) ?: 0L

            // Estrai codici
            val codesSection = extractCodesSection(remote)
            val brand = extractBrandFromName(name)

            for ((key, hexValue) in codesSection) {
                val value = hexValue.removePrefix("0x").toLongOrNull(16) ?: continue
                val commandName = normalizeCommandName(key)

                // Genera pattern dal protocollo
                val pattern = when {
                    protocol == "NEC" || protocol == "SAMSUNG" -> {
                        generatePatternFromCodes(
                            protocol, header, one, zero,
                            preDataBits, preData, postDataBits, postData,
                            value, bits, freq
                        )
                    }
                    else -> intArrayOf()  // RAW se non supportato
                }

                // Usa pattern generato o lascia vuoto
                val code = IrCodeEntity(
                    name = commandName,
                    displayName = commandName,
                    brand = brand,
                    deviceType = guessDeviceType(name, commandName),
                    protocol = protocol,
                    frequency = freq,
                    pattern = if (pattern.isNotEmpty()) IrCodeEntity.patternToString(pattern) else "",
                    address = preData,
                    command = value and ((1L shl bits) - 1),
                    category = "$brand-${guessDeviceType(name, commandName)}",
                    tags = "$brand,${protocol},lirc",
                    source = "lirc",
                    isVerified = true
                )

                codes.add(code)
            }
        }

        return codes
    }

    private fun extractRemoteSections(content: String): List<String> {
        val sections = mutableListOf<String>()
        var current = StringBuilder()
        var inRemote = false

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("begin remote")) {
                current = StringBuilder()
                inRemote = true
            }
            if (inRemote) {
                current.appendLine(line)
            }
            if (trimmed.startsWith("end remote")) {
                sections.add(current.toString())
                inRemote = false
            }
        }

        return sections
    }

    private fun extractValue(section: String, key: String): String? {
        val regex = Regex("""^\s*$key\s+(\S+)\s*$""", RegexOption.MULTILINE)
        return regex.find(section)?.groupValues?.get(1)
    }

    private fun extractTimingPair(section: String, key: String): Pair<Int, Int>? {
        val regex = Regex("""^\s*$key\s+(\d+)\s+(\d+)\s*$""", RegexOption.MULTILINE)
        val match = regex.find(section) ?: return null
        val a = match.groupValues[1].toIntOrNull() ?: return null
        val b = match.groupValues[2].toIntOrNull() ?: return null
        return Pair(a, b)
    }

    private fun extractCodesSection(section: String): List<Pair<String, String>> {
        val codes = mutableListOf<Pair<String, String>>()
        var inCodes = false

        for (line in section.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("begin codes")) {
                inCodes = true
                continue
            }
            if (trimmed.startsWith("end codes")) {
                inCodes = false
                continue
            }
            if (inCodes) {
                // Formato: KEY_NAME        0x1234
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 2 && parts[0].startsWith("KEY_")) {
                    codes.add(Pair(parts[0], parts[1]))
                }
            }
        }

        return codes
    }

    private fun detectProtocol(section: String, name: String): String {
        val content = section.lowercase()
        return when {
            name.contains("NEC", ignoreCase = true) || content.contains("nec") -> "NEC"
            name.contains("SAMSUNG", ignoreCase = true) || content.contains("samsung") -> "SAMSUNG"
            name.contains("SONY", ignoreCase = true) || content.contains("sony") -> "SONY"
            name.contains("RC5", ignoreCase = true) || content.contains("rc5") -> "RC5"
            name.contains("RC6", ignoreCase = true) || content.contains("rc6") -> "RC6"
            name.contains("PANASONIC", ignoreCase = true) || content.contains("panasonic") -> "PANASONIC"
            name.contains("JVC", ignoreCase = true) || content.contains("jvc") -> "JVC"
            name.contains("SHARP", ignoreCase = true) || content.contains("sharp") -> "SHARP"
            else -> "NEC"  // Default
        }
    }

    private fun extractBrandFromName(name: String): String {
        // Prova a estrarre marca dal nome del telecomando
        val knownBrands = listOf(
            "SAMSUNG", "LG", "SONY", "PANASONIC", "PHILIPS", "DAIKIN",
            "MITSUBISHI", "SHARP", "TOSHIBA", "HITACHI", "XIAOMI",
            "HISENSE", "TCL", "HAIER", "GREE", "MIDEA", "ELECTROLUX",
            "WHIRLPOOL", "BOSE", "YAMAHA", "DENON", "ONKYO", "HARMAN",
            "APPLE", "GOOGLE", "AMAZON", "ROKU", "ZTE", "HUAWEI",
            "LENOVO", "ACER", "BENQ", "EPSON", "VIZIO", "SKY", "BT"
        )

        val upper = name.uppercase()
        for (brand in knownBrands) {
            if (upper.contains(brand)) return brand.capitalize()
        }

        // Prendi la prima parte del nome
        return name.split("_").firstOrNull()?.capitalize() ?: name
    }

    private fun normalizeCommandName(key: String): String {
        return key
            .removePrefix("KEY_")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun guessDeviceType(name: String, commandName: String): String {
        val upper = name.uppercase()
        val cmdUpper = commandName.uppercase()

        return when {
            upper.contains("TV") || upper.contains("TELEVISION") -> "TV"
            upper.contains("AC") || upper.contains("AIR") || upper.contains("CONDITION") -> "AC"
            upper.contains("SET") || upper.contains("STB") || upper.contains("BOX") || upper.contains("DECODER") -> "SET_TOP_BOX"
            upper.contains("AUDIO") || upper.contains("SOUND") || upper.contains("SPEAKER") || upper.contains("BAR") -> "AUDIO"
            upper.contains("DVD") || upper.contains("BLU") || upper.contains("PLAYER") -> "DVD"
            upper.contains("FAN") || upper.contains("VENTIL") -> "FAN"
            upper.contains("LIGHT") || upper.contains("LAMP") -> "LIGHT"
            upper.contains("PROJECTOR") || upper.contains("PROJ") -> "PROJECTOR"
            upper.contains("RECEIVER") || upper.contains("AMPLIFIER") || upper.contains("AMP") -> "RECEIVER"
            else -> "OTHER"
        }
    }

    /** Genera pattern raw da parametri decodificati */
    private fun generatePatternFromCodes(
        protocol: String,
        header: Pair<Int, Int>?,
        one: Pair<Int, Int>?,
        zero: Pair<Int, Int>?,
        preDataBits: Int,
        preData: Long,
        postDataBits: Int,
        postData: Long,
        value: Long,
        bits: Int,
        freq: Int
    ): IntArray {
        if (one == null || zero == null) return intArrayOf()

        val pattern = mutableListOf<Int>()

        // Header (leader)
        if (header != null) {
            pattern.add(header.first)
            pattern.add(header.second)
        }

        // Pre-data
        if (preDataBits > 0) {
            for (i in preDataBits - 1 downTo 0) {
                val bit = ((preData shr i) and 1L).toInt()
                pattern.add(one.first)
                pattern.add(if (bit == 1) one.second else zero.second)
            }
        }

        // Data
        for (i in bits - 1 downTo 0) {
            val bit = ((value shr i) and 1L).toInt()
            pattern.add(one.first)
            pattern.add(if (bit == 1) one.second else zero.second)
        }

        // Post-data
        if (postDataBits > 0) {
            for (i in postDataBits - 1 downTo 0) {
                val bit = ((postData shr i) and 1L).toInt()
                pattern.add(one.first)
                pattern.add(if (bit == 1) one.second else zero.second)
            }
        }

        // Stop bit
        pattern.add(one.first)

        return pattern.toIntArray()
    }

    /** Se non ci sono file LIRC, genera un DB preloadato di esempio */
    private suspend fun generatePreloadedDb(dao: IrCodeDao) {
        val sampleCodes = listOf(
            // Samsung TV (indirizzo 0xE0E0)
            generateSampleNec("Samsung", 0xE0E0),
            // LG TV (indirizzo 0x20DF)
            generateSampleNec("LG", 0x20DF),
            // Sony TV
            generateSampleSony(),
            // Philips RC5
            generateSampleRc5(),
        )

        for (codes in sampleCodes) {
            dao.insertAll(codes)
        }
    }

    private fun generateSampleNec(brand: String, address: Int): List<IrCodeEntity> {
        val commands = listOf(
            "Power" to 0x40BF,
            "Volume Up" to 0x609F,
            "Volume Down" to 0xA05F,
            "Channel Up" to 0x20DF,
            "Channel Down" to 0xE01F,
            "Mute" to 0x906F,
            "Input" to 0xD02F,
            "Menu" to 0x50AF,
            "OK" to 0x10EF,
            "Up" to 0x8877,
            "Down" to 0x9867,
            "Left" to 0xA857,
            "Right" to 0x48B7,
            "Back" to 0x28D7,
            "Home" to 0x7A85,
        )

        return commands.map { (name, cmd) ->
            val pattern = PatternGenerator.generateNec(address.toLong(), cmd.toLong())
            IrCodeEntity(
                name = name,
                displayName = name,
                brand = brand,
                deviceType = "TV",
                protocol = "NEC",
                frequency = 38000,
                pattern = pattern.pattern,
                address = address.toLong(),
                command = cmd.toLong(),
                category = "$brand-TV",
                tags = "$brand,TV,NEC",
                source = "lirc",
                isVerified = true
            )
        }
    }

    private fun generateSampleSony(): List<IrCodeEntity> {
        // Usa l'oggetto PatternGenerator per Sony (simulato)
        return listOf(
            IrCodeEntity(
                name = "Power",
                displayName = "Power",
                brand = "Sony",
                deviceType = "TV",
                protocol = "SONY",
                frequency = 40000,
                pattern = "2400,600,600,600,600,600,600,1200,600,600,600,1200,600,600,600,1200,600,1200,600,1200,600,600,600,600,600,600,600",
                address = 1,
                command = 1,
                category = "Sony-TV",
                tags = "Sony,TV,SONY",
                source = "lirc",
                isVerified = true
            )
        )
    }

    private fun generateSampleRc5(): List<IrCodeEntity> {
        return listOf(
            IrCodeEntity(
                name = "Power",
                displayName = "Power",
                brand = "Philips",
                deviceType = "TV",
                protocol = "RC5",
                frequency = 36000,
                pattern = "889,889,889,889,889,889,1778,889,889,889,889,889,889,889,889,889,889,889,889,889,1778,889,889,889,889,889,889,889",
                address = 0,
                command = 12,
                category = "Philips-TV",
                tags = "Philips,TV,RC5",
                source = "lirc",
                isVerified = true
            )
        )
    }

    /** Helper per generare pattern NEC */
    private object PatternGenerator {
        fun generateNec(address: Long, command: Long): IrCodeEntity {
            // Genera pattern NEC semplificato
            val p = mutableListOf<Int>()
            p.add(9000); p.add(4500) // leader
            val addr = address.toInt() and 0xFFFF
            val cmd = command.toInt() and 0xFF
            for (i in 0 until 16) {
                val bit = (addr shr (15 - i)) and 1
                p.add(560); p.add(if (bit == 1) 1690 else 560)
            }
            for (i in 0 until 8) {
                val bit = (cmd shr (7 - i)) and 1
                p.add(560); p.add(if (bit == 1) 1690 else 560)
            }
            val invCmd = cmd.inv() and 0xFF
            for (i in 0 until 8) {
                val bit = (invCmd shr (7 - i)) and 1
                p.add(560); p.add(if (bit == 1) 1690 else 560)
            }
            p.add(560)
            return IrCodeEntity(
                pattern = IrCodeEntity.patternToString(p.toIntArray())
            )
        }

        fun generateSamsung(address: Long, command: Long): IrCodeEntity {
            val p = mutableListOf<Int>()
            p.add(4500); p.add(4500)
            val addr = address.toInt() and 0xFFFF
            val cmd = command.toInt() and 0xFF
            for (i in 0 until 16) {
                val bit = (addr shr (15 - i)) and 1
                p.add(560); p.add(if (bit == 1) 1690 else 560)
            }
            for (i in 0 until 8) {
                val bit = (cmd shr (7 - i)) and 1
                p.add(560); p.add(if (bit == 1) 1690 else 560)
            }
            p.add(560)
            return IrCodeEntity(
                pattern = IrCodeEntity.patternToString(p.toIntArray())
            )
        }
    }
}

/** Helper per capitalizzare prima lettera */
private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
