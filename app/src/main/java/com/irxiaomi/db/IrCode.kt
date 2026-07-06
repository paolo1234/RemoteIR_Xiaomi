package com.irxiaomi.db

import androidx.room.*
import com.irxiaomi.model.DeviceType
import com.irxiaomi.model.Protocol
import java.time.Instant

/**
 * Entity principale per i codici IR.
 * Ogni record rappresenta un singolo comando IR (es. "TV Power", "Volume Up", "Temperatura 25°C")
 */
@Entity(
    tableName = "ir_codes",
    indices = [
        Index(value = ["brand", "device_type", "model"]),
        Index(value = ["brand"]),
        Index(value = ["device_type"]),
        Index(value = ["protocol"]),
        Index(value = ["category"]),
        Index(value = ["is_verified"]),
        Index(value = ["address", "command", "protocol"]),
    ]
)
data class IrCodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Nome del comando (es. "Power", "Volume Up", "CH+") */
    @ColumnInfo(name = "name")
    val name: String = "",

    /** Nome visualizzato nella UI */
    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    /** Marca (es. "Samsung", "Daikin", "Xiaomi") */
    @ColumnInfo(name = "brand")
    val brand: String = "",

    /** Modello specifico (es. "UE40NU7100", "FTX35J"), vuoto se generico */
    @ColumnInfo(name = "model")
    val model: String = "",

    /** Tipo di dispositivo: TV, AC, SET_TOP_BOX, etc. */
    @ColumnInfo(name = "device_type")
    val deviceType: String = "OTHER",

    /** Protocollo: NEC, SONY, RC5, RAW, etc. */
    @ColumnInfo(name = "protocol")
    val protocol: String = "NEC",

    /** Frequenza portante in Hz (es. 38000, 40000, 36000) */
    @ColumnInfo(name = "frequency")
    val frequency: Int = 38000,

    /** Pattern raw: array di int (microseconds on/off) serializzato come CSV */
    @ColumnInfo(name = "pattern")
    val pattern: String = "",

    /** Indirizzo decodificato (es. per NEC: 0x00FF) */
    @ColumnInfo(name = "address")
    val address: Long? = null,

    /** Comando decodificato (es. per NEC: 0x0D per Power) */
    @ColumnInfo(name = "command")
    val command: Long? = null,

    /** Categoria per raggruppamento (es. "TV-LED", "AC-SPLIT", "TV-SMART") */
    @ColumnInfo(name = "category")
    val category: String = "",

    /** Tags per ricerca libera, separati da virgola */
    @ColumnInfo(name = "tags")
    val tags: String = "",

    /** Sorgente del codice: "lirc", "irdb", "user_created", "learned", "variant" */
    @ColumnInfo(name = "source")
    val source: String = "user_created",

    /** True se il codice è stato verificato funzionante */
    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean = false,

    /** Votazione media utenti (0.0 - 5.0) */
    @ColumnInfo(name = "rating")
    val rating: Float = 0.0f,

    /** Numero di voti */
    @ColumnInfo(name = "vote_count")
    val voteCount: Int = 0,

    /** True se il codice è un preferito */
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    /** Note utente */
    @ColumnInfo(name = "notes")
    val notes: String = "",

    /** Timestamp creazione */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Timestamp ultima modifica */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /** Timestamp ultimo utilizzo (per ordinamento frequente) */
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = 0L,

    /** Numero di volte che è stato inviato */
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0
) {
    /** Converte la stringa pattern in IntArray per la trasmissione */
    fun parsePattern(): IntArray {
        if (pattern.isBlank()) return intArrayOf()
        return try {
            pattern.split(",").map { it.trim().toInt() }.toIntArray()
        } catch (e: NumberFormatException) {
            intArrayOf()
        }
    }

    /** Crea stringa pattern da IntArray */
    companion object {
        fun patternToString(p: IntArray): String = p.joinToString(",")

        fun fromValues(
            name: String,
            brand: String,
            deviceType: DeviceType,
            protocol: Protocol,
            frequency: Int,
            pattern: IntArray,
            address: Long? = null,
            command: Long? = null,
            model: String = ""
        ) = IrCodeEntity(
            name = name,
            displayName = name,
            brand = brand,
            deviceType = deviceType.name,
            protocol = protocol.name,
            frequency = frequency,
            pattern = patternToString(pattern),
            address = address,
            command = command,
            category = "$brand-${deviceType.displayName}",
            tags = "$brand,${deviceType.name},${protocol.name}"
        )
    }
}

/**
 * Entity per i layout dei telecomandi
 */
@Entity(tableName = "remote_layouts")
data class RemoteLayoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "brand")
    val brand: String = "",

    @ColumnInfo(name = "model")
    val model: String = "",

    @ColumnInfo(name = "device_type")
    val deviceType: String = "",

    /** JSON che descrive la disposizione dei pulsanti */
    @ColumnInfo(name = "layout_json")
    val layoutJson: String = "",

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entity per i telecomandi "appresi" (learning)
 */
@Entity(tableName = "learned_codes")
data class LearnedCodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "raw_pattern")
    val rawPattern: String = "",

    @ColumnInfo(name = "protocol")
    val protocol: String = "UNKNOWN",

    @ColumnInfo(name = "frequency")
    val frequency: Int = 38000,

    @ColumnInfo(name = "decoded_address")
    val decodedAddress: Long? = null,

    @ColumnInfo(name = "decoded_command")
    val decodedCommand: Long? = null,

    @ColumnInfo(name = "is_confirmed")
    val isConfirmed: Boolean = false,

    @ColumnInfo(name = "device_type")
    val deviceType: String = "OTHER",

    @ColumnInfo(name = "learned_at")
    val learnedAt: Long = System.currentTimeMillis()
)

/**
 * Entity per il DB dei brand (ricerca rapida)
 */
@Entity(tableName = "brands")
data class BrandEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    @ColumnInfo(name = "aliases")
    val aliases: String = "",  // CSV

    @ColumnInfo(name = "country")
    val country: String = "",

    @ColumnInfo(name = "code_count")
    val codeCount: Int = 0,

    @ColumnInfo(name = "popular_models")
    val popularModels: String = ""  // CSV
)
