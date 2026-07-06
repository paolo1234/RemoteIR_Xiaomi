package com.irxiaomi.clone

import android.content.Context
import android.net.Uri
import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Gestisce esportazione e importazione di codici IR in formato JSON/CSV.
 * Permette di condividere codici tra utenti (file .ircodes o .irx).
 */
class CodeCloneManager(private val context: Context) {

    companion object {
        private const val TAG = "CodeCloneManager"
        const val MIME_TYPE_IRX = "application/x-irxiaomi-codes"
        const val FILE_EXTENSION = ".ircodes"
    }

    data class ExportResult(
        val exportedCount: Int,
        val fileUri: Uri? = null,
        val error: String? = null
    )

    data class ImportResult(
        val importedCount: Int,
        val skippedCount: Int,
        val errors: List<String> = emptyList()
    )

    /**
     * Esporta codici in formato JSON.
     * Formato:
     * {
     *   "version": 1,
     *   "app": "IRXiaomi",
     *   "exported_at": "...",
     *   "codes": [ { ... }, ... ]
     * }
     */
    fun exportToJson(codes: List<IrCodeEntity>): String {
        val json = JSONObject().apply {
            put("version", 1)
            put("app", "IRXiaomi")
            put("exported_at", System.currentTimeMillis())
            put("code_count", codes.size)

            val arr = JSONArray()
            codes.forEach { code ->
                arr.put(JSONObject().apply {
                    put("name", code.name)
                    put("display_name", code.displayName)
                    put("brand", code.brand)
                    put("model", code.model)
                    put("device_type", code.deviceType)
                    put("protocol", code.protocol)
                    put("frequency", code.frequency)
                    put("pattern", code.pattern)
                    put("address", code.address ?: JSONObject.NULL)
                    put("command", code.command ?: JSONObject.NULL)
                    put("category", code.category)
                    put("tags", code.tags)
                    put("source", "imported")
                    put("notes", "Importato da file")
                })
            }
            put("codes", arr)
        }

        return json.toString(2)
    }

    /**
     * Importa codici da file JSON.
     */
    suspend fun importFromJson(
        content: String,
        dao: IrCodeDao,
        defaultBrand: String? = null
    ): ImportResult {
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0

        try {
            val json = JSONObject(content)
            val version = json.optInt("version", 0)
            val codesArr = json.optJSONArray("codes") ?: return ImportResult(0, 0, listOf("Nessun codice trovato"))

            for (i in 0 until codesArr.length()) {
                try {
                    val obj = codesArr.getJSONObject(i)
                    val pattern = obj.optString("pattern", "")
                    
                    if (pattern.isBlank()) {
                        skipped++
                        continue
                    }

                    val entity = IrCodeEntity(
                        name = obj.optString("name", "Unknown"),
                        displayName = obj.optString("display_name", obj.optString("name", "Unknown")),
                        brand = defaultBrand ?: obj.optString("brand", "Unknown"),
                        model = obj.optString("model", ""),
                        deviceType = obj.optString("device_type", "OTHER"),
                        protocol = obj.optString("protocol", "RAW"),
                        frequency = obj.optInt("frequency", 38000),
                        pattern = pattern,
                        address = if (obj.isNull("address")) null else obj.optLong("address"),
                        command = if (obj.isNull("command")) null else obj.optLong("command"),
                        category = obj.optString("category", ""),
                        tags = obj.optString("tags", ""),
                        source = "imported",
                        notes = obj.optString("notes", "Importato da file")
                    )

                    dao.insert(entity)
                    imported++
                } catch (e: Exception) {
                    errors.add("Errore al codice $i: ${e.message}")
                    skipped++
                }
            }

        } catch (e: Exception) {
            return ImportResult(0, 0, listOf("Errore parsing JSON: ${e.message}"))
        }

        return ImportResult(imported, skipped, errors)
    }

    /**
     * Importa da URI (file selezionato dal file picker).
     */
    suspend fun importFromUri(
        uri: Uri,
        dao: IrCodeDao
    ): ImportResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 0, listOf("Impossibile aprire il file"))

            val content = BufferedReader(InputStreamReader(inputStream)).readText()
            importFromJson(content, dao)
        } catch (e: Exception) {
            ImportResult(0, 0, listOf("Errore lettura file: ${e.message}"))
        }
    }

    /**
     * Crea un file di export e ne restituisce l'URI.
     */
    fun createExportFile(codes: List<IrCodeEntity>, filename: String = "ir_codes_export"): Uri? {
        return try {
            val json = exportToJson(codes)
            val file = java.io.File(context.cacheDir, "$filename$FILE_EXTENSION")
            file.writeText(json)
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }
}
