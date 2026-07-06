package com.irxiaomi.sync

import android.content.Context
import android.util.Log
import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import kotlinx.coroutines.*

/**
 * Crowdsourcing: permette agli utenti di contribuire codici IR
 * e di "votare" quelli funzionanti.
 *
 * Meccanismi:
 * 1. Upload di codici appresi (da learning, Broadlink, ESP32)
 * 2. Download di codici dalla community
 * 3. Votazione (conferma funzionamento)
 * 4. Segnalazione codici non funzionanti
 * 5. "Code Quest": trovare codici mancanti completando sfide
 */
class CrowdsourceManager(private val context: Context) {

    companion object {
        private const val TAG = "CrowdsourceManager"
        private const val SYNC_INTERVAL_HOURS = 24L
        private const val MIN_VOTES_FOR_VERIFICATION = 3
        private const val VERIFICATION_THRESHOLD = 0.7f  // 70% positivi
    }

    data class CrowdCode(
        val code: IrCodeEntity,
        val upvotes: Int = 0,
        val downvotes: Int = 0,
        val reports: Int = 0,
        val uploaderDevice: String = "",
        val uploadedAt: Long = System.currentTimeMillis()
    )

    data class CodeQuest(
        val id: String,
        val title: String,
        val description: String,
        val brand: String,
        val deviceType: String,
        val targetCount: Int,
        val currentCount: Int = 0,
        val reward: String = ""
    )

    private val pendingUploads = mutableListOf<IrCodeEntity>()
    private val localCrowdCodes = mutableListOf<CrowdCode>()

    /**
     * Prepara un codice per l'upload al server community.
     * Viene accodato e sincronizzato quando possibile.
     */
    fun queueForUpload(code: IrCodeEntity) {
        synchronized(pendingUploads) {
            if (pendingUploads.none { it.brand == code.brand && it.command == code.command }) {
                pendingUploads.add(code.copy(
                    notes = if (code.notes.isBlank()) "Contribuito via crowdsourcing" else code.notes
                ))
                Log.d(TAG, "Codice accodato per upload: ${code.brand} ${code.name}")
            }
        }
    }

    /**
     * Sincronizza i codici in sospeso con il server community.
     */
    suspend fun syncPending(dao: IrCodeDao): Int = withContext(Dispatchers.IO) {
        var uploaded = 0

        val batch: List<IrCodeEntity>
        synchronized(pendingUploads) {
            batch = pendingUploads.toList()
            pendingUploads.clear()
        }

        for (code in batch) {
            try {
                // In produzione: chiamata API al server community
                dao.insert(code)
                uploaded++
                Log.i(TAG, "Uploaded: ${code.brand} ${code.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                synchronized(pendingUploads) {
                    pendingUploads.add(code)  // Riaccoda
                }
            }
        }

        uploaded
    }

    /**
     * Registra un voto positivo (codice funzionante).
     */
    suspend fun upvoteCode(codeId: Long, dao: IrCodeDao) {
        withContext(Dispatchers.IO) {
            try {
                dao.incrementUsage(codeId)

                // Se ha abbastanza voti positivi, marchia come verificato
                val code = dao.getById(codeId) ?: return@withContext
                if (!code.isVerified && code.usageCount >= MIN_VOTES_FOR_VERIFICATION) {
                    dao.verify(codeId)
                    Log.i(TAG, "Codice $codeId verificato ($codeId usageCount ≥ $MIN_VOTES_FOR_VERIFICATION)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upvote error", e)
            }
        }
    }

    /**
     * Segnala un codice come non funzionante.
     */
    suspend fun reportCode(codeId: Long, reason: String, dao: IrCodeDao) {
        withContext(Dispatchers.IO) {
            Log.w(TAG, "Codice $codeId segnalato: $reason")
            // In produzione: invia segnalazione al server
        }
    }

    /**
     * Genera una "Code Quest": sfida per trovare codici mancanti.
     * Quando un brand ha pochi codici per un device type,
     * viene creata una quest per gli utenti.
     */
    suspend fun generateQuests(dao: IrCodeDao): List<CodeQuest> = withContext(Dispatchers.Default) {
        val quests = mutableListOf<CodeQuest>()

        try {
            // Marche con pochi codici TV
            val tvBrands = dao.getBrandsWithFewCodes("TV", 10)
            for (brandCount in tvBrands) {
                quests.add(CodeQuest(
                    id = "tv_${brandCount.brand}",
                    title = "Completa i comandi TV ${brandCount.brand}",
                    description = "Mancano comandi TV per ${brandCount.brand} (solo ${brandCount.cnt})",
                    brand = brandCount.brand,
                    deviceType = "TV",
                    targetCount = 25,
                    currentCount = brandCount.cnt
                ))
            }

            // Marche con pochi codici AC
            val acBrands = dao.getBrandsWithFewCodes("AC", 5)
            for (brandCount in acBrands) {
                quests.add(CodeQuest(
                    id = "ac_${brandCount.brand}",
                    title = "Completa i comandi AC ${brandCount.brand}",
                    description = "Mancano comandi AC per ${brandCount.brand} (solo ${brandCount.cnt})",
                    brand = brandCount.brand,
                    deviceType = "AC",
                    targetCount = 15,
                    currentCount = brandCount.cnt
                ))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Generate quests error", e)
        }

        quests
    }

    /**
     * Trova codici "orfani" nel database (marchio sconosciuto, pochi dati).
     * Questi codici possono essere adottati e migliorati dagli utenti.
     */
    suspend fun findOrphanCodes(dao: IrCodeDao): List<IrCodeEntity> = withContext(Dispatchers.Default) {
        try {
            val allCodes = dao.getAll()
            allCodes.filter { code ->
                code.brand == "Unknown" || code.brand == "Appreso" ||
                code.deviceType == "OTHER" || code.pattern.isBlank()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Prepara un batch di codici da condividere con altri utenti.
     * Include solo codici verificati o ad alta qualità.
     */
    suspend fun prepareShareableCodes(dao: IrCodeDao): List<IrCodeEntity> = withContext(Dispatchers.Default) {
        try {
            val verified = mutableListOf<IrCodeEntity>()

            // Prendi codici verificati
            dao.getVerified().collect { codes ->
                verified.addAll(codes.filter { it.pattern.isNotBlank() })
            }

            // Se non bastano, aggiungi i più usati
            if (verified.size < 50) {
                dao.getMostUsed(100).collect { codes ->
                    verified.addAll(codes.filter { it.pattern.isNotBlank() && it !in verified })
                }
            }

            verified
        } catch (e: Exception) {
            emptyList()
        }
    }
}
