package com.irxiaomi.sync

import android.util.Log
import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Importa codici IR dal database IRDB (Global Caché).
 *
 * IRDB (https://irdb.globalcache.com/) è il più grande database commerciale
 * di codici IR, con oltre 1 milione di dispositivi.
 *
 * NOTA: L'API IRDB richiede una licenza commerciale per uso estensivo.
 * Questo importatore è fornito a scopo dimostrativo e didattico.
 * Per uso commerciale, contatta Global Caché.
 *
 * API IRDB:
 * - GET /irman/irman.html?loc=...  → Interfaccia web
 * - GET /export/...                → Export in vari formati
 * - API endpoint non pubblici richiedono accordo commerciale
 *
 * Alternativa: utilizzo del database IRDB scaricabile da siti come
 * Remote Central, Remote Codes, o tramite reverse engineering delle
 * app Broadlink / e-Control.
 */
class IrdbImporter {

    companion object {
        private const val TAG = "IrdbImporter"

        /** URL base IRDB (pubblico, porzione limitata) */
        private const val IRDB_BASE_URL = "https://irdb.globalcache.com"

        /** Timeout per richieste HTTP */
        private const val TIMEOUT_SECONDS = 30L

        /** User-Agent per sembrare un browser normale */
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

        /** Marche note con ID IRDB (parziale) */
        val KNOWN_BRANDS = mapOf(
            "Samsung" to "samsung",
            "LG" to "lg",
            "Sony" to "sony",
            "Panasonic" to "panasonic",
            "Philips" to "philips",
            "Sharp" to "sharp",
            "Toshiba" to "toshiba",
            "Daikin" to "daikin",
            "Mitsubishi" to "mitsubishi",
            "Hitachi" to "hitachi",
            "Denon" to "denon",
            "Yamaha" to "yamaha",
            "Bose" to "bose",
            "Harman/Kardon" to "harmankardon",
            "JVC" to "jvc",
            "Onkyo" to "onkyo",
            "Marantz" to "marantz",
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class ImportProgress(
        val stage: String = "",
        val progress: Float = 0f,
        val itemsFound: Int = 0,
        val imported: Int = 0,
        val errors: Int = 0,
        val isComplete: Boolean = false
    )

    /**
     * Cerca codici IRDB per marca/tipo dispositivo.
     * Questo è un esempio di come si potrebbe integrare IRDB.
     * L'implementazione reale richiederebbe API key.
     */
    suspend fun searchIrdb(
        brand: String,
        deviceType: String,
        dao: IrCodeDao? = null
    ): List<IrCodeEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<IrCodeEntity>()

        try {
            val irdbBrand = KNOWN_BRANDS[brand] ?: brand.lowercase()
            val url = "$IRDB_BASE_URL/search?q=${
                URLEncoder.encode("$brand $deviceType remote", "UTF-8")
            }"

            Log.d(TAG, "Searching IRDB: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "IRDB search failed: ${response.code}")
                return@withContext results
            }

            val body = response.body?.string() ?: return@withContext results

            // Parsing della risposta HTML (IRDB ritorna HTML, non JSON)
            // ... implementazione specifica per il parsing IRDB ...
            // Per ora, stub

            Log.i(TAG, "IRDB search returned ${body.length} bytes")

        } catch (e: Exception) {
            Log.e(TAG, "IRDB search error", e)
        }

        results
    }

    /**
     * Importa da file export IRDB in formato CSV/Pronto.
     * I file IRDB export hanno estensione .irdb o .irp.
     */
    suspend fun importFromFile(
        content: String,
        filename: String,
        dao: IrCodeDao,
        defaultBrand: String? = null
    ): Int = withContext(Dispatchers.IO) {
        var imported = 0

        try {
            // Determina formato dal nome file
            when {
                filename.endsWith(".irp") -> {
                    // Formato IRP (IR Protocol) - notazione testuale
                    imported += parseIrp(content, dao, defaultBrand)
                }
                filename.endsWith(".irdb") || filename.endsWith(".csv") -> {
                    // Formato CSV IRDB
                    imported += parseCsv(content, dao, defaultBrand)
                }
                filename.endsWith(".json") -> {
                    // Formato JSON
                    imported += parseJson(content, dao, defaultBrand)
                }
                else -> {
                    // Prova auto-rilevamento
                    imported += parseAuto(content, dao, defaultBrand)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing IRDB file: $filename", e)
        }

        imported
    }

    /**
     * Parsing formato IRP.
     * Esempio: "NEC1:0xE0E0,0x40BF" → 38kHz NEC, address=0xE0E0, command=0x40BF
     */
    private suspend fun parseIrp(
        content: String,
        dao: IrCodeDao,
        defaultBrand: String?
    ): Int {
        var count = 0

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

            try {
                // Formato IRP: "PROTOCOL:ADDR,CMD"
                // Esempio: "NEC1:0xE0E0,0x40BF"
                val parts = trimmed.split(":")
                if (parts.size < 2) continue

                val protocol = parts[0].trim().uppercase()
                val dataParts = parts[1].split(",")
                if (dataParts.size < 2) continue

                val address = dataParts[0].trim().removePrefix("0x").toLongOrNull(16) ?: continue
                val command = dataParts[1].trim().removePrefix("0x").toLongOrNull(16) ?: continue

                val entity = IrCodeEntity(
                    name = "Code_${count + 1}",
                    displayName = "IRP Code ${count + 1}",
                    brand = defaultBrand ?: "Unknown",
                    deviceType = "OTHER",
                    protocol = when (protocol) {
                        "NEC1", "NEC" -> "NEC"
                        "SAMSUNG" -> "SAMSUNG"
                        "SONY12", "SONY15", "SONY20" -> "SONY"
                        "RC5" -> "RC5"
                        "RC6" -> "RC6"
                        else -> "NEC"
                    },
                    frequency = 38000,
                    address = address,
                    command = command,
                    source = "irdb",
                    tags = "$protocol,${defaultBrand ?: "unknown"},irdb"
                )

                dao.insert(entity)
                count++
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing IRP line: $line", e)
            }
        }

        return count
    }

    /**
     * Parsing formato CSV IRDB.
     */
    private suspend fun parseCsv(
        content: String,
        dao: IrCodeDao,
        defaultBrand: String?
    ): Int {
        var count = 0
        val lines = content.lines()
        if (lines.isEmpty()) return 0

        // Intestazione CSV - cerca di determinare le colonne
        val header = lines.first().split(",").map { it.trim().lowercase() }
        val colName = header.indexOfFirst { it.contains("name") || it.contains("function") || it.contains("key") }
        val colBrand = header.indexOfFirst { it.contains("brand") || it.contains("manufacturer") || it.contains("make") }
        val colDevice = header.indexOfFirst { it.contains("device") || it.contains("type") || it.contains("category") }
        val colProtocol = header.indexOfFirst { it.contains("protocol") }
        val colFreq = header.indexOfFirst { it.contains("freq") || it.contains("carrier") }
        val colPattern = header.indexOfFirst { it.contains("pattern") || it.contains("data") || it.contains("code") || it.contains("hex") }
        val colAddress = header.indexOfFirst { it.contains("address") || it.contains("pre_data") }
        val colCommand = header.indexOfFirst { it.contains("command") || it.contains("function") }

        for (i in 1 until lines.size) {
            try {
                val cols = lines[i].split(",").map { it.trim() }
                if (cols.size < 3) continue

                val name = if (colName >= 0 && colName < cols.size) cols[colName] else "Code_$i"
                val brand = if (colBrand >= 0 && colBrand < cols.size) cols[colBrand] else defaultBrand ?: "Unknown"
                val deviceType = if (colDevice >= 0 && colDevice < cols.size) cols[colDevice] else "OTHER"
                val protocol = if (colProtocol >= 0 && colProtocol < cols.size) cols[colProtocol] else "NEC"
                val freq = if (colFreq >= 0 && colFreq < cols.size) cols[colFreq].toIntOrNull() ?: 38000 else 38000
                val pattern = if (colPattern >= 0 && colPattern < cols.size) cols[colPattern] else ""
                val address = if (colAddress >= 0 && colAddress < cols.size) cols[colAddress].removePrefix("0x").toLongOrNull(16) else null
                val command = if (colCommand >= 0 && colCommand < cols.size) cols[colCommand].removePrefix("0x").toLongOrNull(16) else null

                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = brand,
                    deviceType = deviceType,
                    protocol = protocol.uppercase(),
                    frequency = freq,
                    pattern = pattern,
                    address = address,
                    command = command,
                    source = "irdb",
                    tags = "$brand,$deviceType,$protocol,irdb"
                )

                dao.insert(entity)
                count++
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing CSV line $i", e)
            }
        }

        return count
    }

    /**
     * Parsing formato JSON.
     */
    private suspend fun parseJson(
        content: String,
        dao: IrCodeDao,
        defaultBrand: String?
    ): Int {
        var count = 0

        try {
            val root = JSONObject(content)
            val codes = root.optJSONArray("codes") ?: root.optJSONArray("data")
                ?: return 0

            for (i in 0 until codes.length()) {
                try {
                    val obj = codes.getJSONObject(i)
                    val entity = IrCodeEntity(
                        name = obj.optString("name", "Code_$i"),
                        displayName = obj.optString("display_name", obj.optString("name", "Code_$i")),
                        brand = obj.optString("brand", defaultBrand ?: "Unknown"),
                        model = obj.optString("model", ""),
                        deviceType = obj.optString("device_type", obj.optString("type", "OTHER")),
                        protocol = obj.optString("protocol", "NEC"),
                        frequency = obj.optInt("frequency", 38000),
                        pattern = obj.optString("pattern", ""),
                        address = if (obj.has("address")) obj.optLong("address", -1).let { if (it < 0) null else it } else null,
                        command = if (obj.has("command")) obj.optLong("command", -1).let { if (it < 0) null else it } else null,
                        source = "irdb",
                        tags = obj.optString("tags", ""),
                        isVerified = obj.optBoolean("is_verified", false)
                    )
                    dao.insert(entity)
                    count++
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing JSON code $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON", e)
        }

        return count
    }

    /**
     * Auto-rilevamento formato.
     */
    private suspend fun parseAuto(
        content: String,
        dao: IrCodeDao,
        defaultBrand: String?
    ): Int {
        return when {
            content.trimStart().startsWith("{") || content.trimStart().startsWith("[") ->
                parseJson(content, dao, defaultBrand)
            content.contains("NEC1:") || content.contains("SONY12:") ->
                parseIrp(content, dao, defaultBrand)
            content.contains(",") && content.lines().size > 5 ->
                parseCsv(content, dao, defaultBrand)
            else -> 0
        }
    }

    /**
     * Scarica database da fonti alternative.
     * Esempio: Remote Central database (formato text).
     */
    suspend fun downloadFromRemoteCentral(
        brand: String,
        dao: IrCodeDao
    ): Int = withContext(Dispatchers.IO) {
        var count = 0

        try {
            // Remote Central ha pagine con codici in formato testo
            val url = "https://www.remotecentral.com/cgi-bin/codes/?brand=${
                URLEncoder.encode(brand, "UTF-8")
            }"

            Log.d(TAG, "Downloading from Remote Central: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext 0

            val body = response.body?.string() ?: return@withContext 0

            // Remote Central usa un formato testuale specifico
            // Esempio: "Device: Samsung TV\nCode: 0xE0E0 0x40BF (Power)\n"
            val regex = Regex(
                """Code:\s*(0x[0-9A-Fa-f]+)\s+(0x[0-9A-Fa-f]+)\s*\((.+?)\)""",
                RegexOption.MULTILINE
            )

            for (match in regex.findAll(body)) {
                try {
                    val address = match.groupValues[1].removePrefix("0x").toLongOrNull(16) ?: continue
                    val command = match.groupValues[2].removePrefix("0x").toLongOrNull(16) ?: continue
                    val name = match.groupValues[3].trim()

                    val entity = IrCodeEntity(
                        name = name,
                        displayName = name,
                        brand = brand,
                        deviceType = "TV",
                        protocol = "NEC",
                        frequency = 38000,
                        address = address,
                        command = command,
                        source = "remote_central",
                        tags = "$brand,TV,NEC,remotecentral"
                    )

                    dao.insert(entity)
                    count++
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing Remote Central match", e)
                }
            }

            Log.i(TAG, "Downloaded $count codes from Remote Central for $brand")

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from Remote Central", e)
        }

        count
    }
}
