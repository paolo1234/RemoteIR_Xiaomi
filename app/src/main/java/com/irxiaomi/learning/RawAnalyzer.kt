package com.irxiaomi.learning

import android.util.Log

/**
 * Analisi e manipolazione di pattern IR raw.
 * Utility per pulire, validare, normalizzare e analizzare segnali IR grezzi.
 */
object RawAnalyzer {

    private const val TAG = "RawAnalyzer"

    /**
     * Pulisce un pattern raw rimuovendo rumore e outlier.
     * - Rimuove impulsi troppo brevi (< 50µs, probabilmente rumore)
     * - Accorpa impulsi adiacenti dello stesso tipo se separati da gap minimo
     * - Normalizza i valori eliminando glitch
     */
    fun cleanPattern(timing: IntArray, minPulseUs: Int = 50): IntArray {
        if (timing.size < 2) return timing

        val cleaned = mutableListOf<Int>()
        var i = 0

        while (i < timing.size) {
            // Ignora impulsi troppo brevi (rumore)
            if (timing[i] < minPulseUs) {
                i++
                // Somma il rumore all'impulso successivo? Meglio ignorare
                if (i < timing.size) {
                    // Salta anche il successivo se era parte del rumore
                    i++
                }
                continue
            }

            cleaned.add(timing[i])
            i++
        }

        return cleaned.toIntArray()
    }

    /**
     * Normalizza un pattern a un insieme di timing standard.
     * Arrotonda i valori ai timing più vicini per NEC/Sony/etc.
     */
    fun normalizeToNec(timing: IntArray): IntArray {
        val result = IntArray(timing.size)

        for (i in timing.indices) {
            result[i] = normalizeValue(timing[i])
        }

        return result
    }

    private fun normalizeValue(value: Int): Int {
        // Timing standard NEC (in µs)
        // Leader: 9000, 4500
        // Bit 0: 560, 560
        // Bit 1: 560, 1690
        // Repeat: 9000, 2250
        return when {
            value in 8000..10000 -> 9000       // Leader on
            value in 4000..5000 -> 4500        // Leader off / repeat
            value in 2000..2500 -> 2250        // Repeat
            value in 1500..1900 -> 1690        // Bit 1
            value in 500..650 -> 560            // Bit 0 / burst
            value in 300..499 -> 560            // Quasi 560
            value > 10000 -> 9000               // Leader lungo
            else -> value                        // Non normalizzato
        }
    }

    /**
     * Rileva la frequenza portante dal pattern.
     * Non possiamo saperla dal solo segnale demodulato,
     * ma possiamo stimarla dal burst più corto.
     * (Il TSOP demodula, quindi il burst più corto è ~560µs per 38kHz)
     */
    fun detectCarrierFrequency(timing: IntArray): Int {
        if (timing.isEmpty()) return 38000

        val minBurst = timing.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: return 38000

        // I burst più corti corrispondono a 560µs per 38kHz
        // Se vediamo burst di ~440µs, potrebbe essere 40kHz
        // Se vediamo burst di ~560µs, è 38kHz
        // Se vediamo burst di ~650µs, è 36kHz
        return when {
            minBurst < 500 -> 40000
            minBurst in 500..600 -> 38000
            minBurst > 600 -> 36000
            else -> 38000
        }
    }

    /**
     * Verifica se un pattern è valido (ha senso come segnale IR).
     */
    fun isValidPattern(timing: IntArray): Boolean {
        if (timing.size < 4) return false  // Troppo corto

        // Deve iniziare con un burst (on)
        if (timing.size % 2 != 0) return false  // Numero dispari di timing

        // Il primo impulso deve essere un burst (on) di almeno 100µs
        if (timing[0] < 100) return false

        // Deve avere almeno un leader riconoscibile per protocolli comuni
        val leaderOn = timing[0]
        if (leaderOn < 2000 && leaderOn > 10000) {
            // Forse è RC5/RC6 che non hanno leader lungo
            return timing.size >= 20
        }

        return true
    }

    /**
     * Trova ripetizioni nel pattern (codice che si ripete).
     * Utile per estrarre un singolo frame da una sequenza ripetuta.
     */
    fun findRepetitions(timing: IntArray): List<Pair<Int, Int>> {
        val repetitions = mutableListOf<Pair<Int, Int>>()

        if (timing.size < 4) return repetitions

        // Cerca pattern che si ripetono con gap specifico
        // NEC: gap di 40ms tra ripetizioni
        val necGapMin = 35000  // 35 ms
        val necGapMax = 50000  // 50 ms

        var lastEnd = 0
        for (i in 0 until timing.size - 1) {
            if (timing[i] > necGapMin && timing[i] < necGapMax) {
                // Trovato un gap NEC: sezione da lastEnd a i è un frame
                val frameStart = lastEnd
                val frameEnd = i - 1
                if (frameEnd - frameStart > 4) {
                    repetitions.add(Pair(frameStart, frameEnd))
                }
                lastEnd = i + 1
            }
        }

        return repetitions
    }

    /**
     * Estrae un singolo frame dal pattern (prima ripetizione).
     */
    fun extractFirstFrame(timing: IntArray): IntArray {
        val reps = findRepetitions(timing)
        if (reps.isEmpty()) return timing

        val (start, end) = reps.first()
        return timing.copyOfRange(start, end + 1)
    }

    /**
     * Calcola statistiche del segnale.
     */
    data class SignalStats(
        val totalPulses: Int,
        val totalDurationUs: Long,
        val averageBurstUs: Double,
        val averageSpaceUs: Double,
        val minBurstUs: Int,
        val maxBurstUs: Int,
        val minSpaceUs: Int,
        val maxSpaceUs: Int,
        val frequency: Int,
        val estimatedProtocol: String
    )

    fun calculateStats(timing: IntArray): SignalStats {
        var totalBurstUs = 0L
        var totalSpaceUs = 0L
        var burstCount = 0
        var spaceCount = 0
        var minBurst = Int.MAX_VALUE
        var maxBurst = 0
        var minSpace = Int.MAX_VALUE
        var maxSpace = 0

        for (i in timing.indices) {
            if (i % 2 == 0) {
                // Burst (on)
                totalBurstUs += timing[i]
                burstCount++
                if (timing[i] < minBurst) minBurst = timing[i]
                if (timing[i] > maxBurst) maxBurst = timing[i]
            } else {
                // Space (off)
                totalSpaceUs += timing[i]
                spaceCount++
                if (timing[i] < minSpace) minSpace = timing[i]
                if (timing[i] > maxSpace) maxSpace = timing[i]
            }
        }

        val totalDuration = timing.sumOf { it.toLong() }

        return SignalStats(
            totalPulses = timing.size,
            totalDurationUs = totalDuration,
            averageBurstUs = if (burstCount > 0) totalBurstUs.toDouble() / burstCount else 0.0,
            averageSpaceUs = if (spaceCount > 0) totalSpaceUs.toDouble() / spaceCount else 0.0,
            minBurstUs = if (minBurst != Int.MAX_VALUE) minBurst else 0,
            maxBurstUs = maxBurst,
            minSpaceUs = if (minSpace != Int.MAX_VALUE) minSpace else 0,
            maxSpaceUs = maxSpace,
            frequency = detectCarrierFrequency(timing),
            estimatedProtocol = estimateProtocol(timing)
        )
    }

    /**
     * Stima il protocollo basandosi sulle statistiche del segnale.
     */
    fun estimateProtocol(timing: IntArray): String {
        if (timing.size < 4) return "UNKNOWN"

        val firstBurst = timing[0]
        val firstSpace = timing[1]

        return when {
            // NEC: leader 9000 on, 4500 off
            firstBurst in 8000..10000 && firstSpace in 4000..5000 -> {
                val bits = (timing.size - 2) / 2
                when {
                    bits in 64..68 -> "NEC"         // 32 bit + stop
                    bits in 32..35 -> "NEC_REPEAT"   // Repeat
                    else -> "NEC_EXT"                 // Extended NEC
                }
            }
            // Samsung: leader 4500 on, 4500 off
            firstBurst in 4000..5000 && firstSpace in 4000..5000 -> "SAMSUNG"
            // Sony: leader 2400 on, 600 off
            firstBurst in 2200..2600 && firstSpace in 500..700 -> {
                val bits = (timing.size - 2) / 2
                when {
                    bits <= 12 -> "SONY_12"
                    bits <= 15 -> "SONY_15"
                    else -> "SONY_20"
                }
            }
            // RC5: half-bit 889µs, full-bit 1778µs (no leader)
            firstBurst in 800..950 -> "RC5"
            // NEC repeat: 9000 on, 2250 off
            firstBurst in 8000..10000 && firstSpace in 2000..2500 -> "NEC_REPEAT"
            // Raw/probabilmente AC (pattern lungo)
            timing.size > 150 -> "AC_RAW"
            // Default
            else -> {
                if (timing.size > 100) "RAW_LONG" else "RAW"
            }
        }
    }

    /**
     * Converte pattern in formato Pronto hex (standard universale).
     * Formato: "0000 006D 0000 0022 00AC 00AC ..."
     */
    fun toProntoHex(frequency: Int, timing: IntArray): String {
        if (timing.size < 2) return ""

        try {
            val freqCode = (1000000.0 / frequency / 0.241246).toInt()
            val burstCount = timing.size / 2

            val sb = StringBuilder()
            sb.append("0000 ")  // Raw
            sb.append(String.format("%04X ", freqCode and 0xFFFF))
            sb.append("0000 ")  // Burst 1
            sb.append(String.format("%04X ", burstCount and 0xFFFF))

            for (i in timing.indices) {
                val code = (timing[i] / 0.241246).toInt()
                sb.append(String.format("%04X ", code and 0xFFFF))
            }

            return sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting to Pronto", e)
            return ""
        }
    }

    /**
     * Converte stringa Pronto hex in pattern IntArray.
     */
    fun fromProntoHex(prontoHex: String): Pair<Int, IntArray>? {
        return try {
            val parts = prontoHex.trim().split("\\s+".toRegex()).map { it.toInt(16) }
            if (parts.size < 6) return null

            val freqCode = parts[1]
            val freq = (1000000.0 / (freqCode * 0.241246)).toInt()
            val burst1 = parts[2]
            val burst2 = parts[3]
            val totalBursts = burst1 + burst2

            if (parts.size < 4 + totalBursts) return null

            val timing = IntArray(totalBursts * 2) { i ->
                if (i < totalBursts * 2) {
                    (parts[4 + i] * 0.241246).toInt()
                } else 0
            }

            Pair(freq, timing)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Unisce pattern identici consecutivi (rimuove ripetizioni).
     */
    fun deduplicate(timing: IntArray): IntArray {
        if (timing.size < 8) return timing

        val result = mutableListOf<Int>()
        var i = 0

        while (i < timing.size) {
            // Cerca ripetizioni del frame corrente
            val frameLen = findFrameLength(timing, i)
            if (frameLen <= 0) {
                result.add(timing[i])
                i++
                continue
            }

            // Copia il frame una volta
            for (j in 0 until frameLen) {
                if (i + j < timing.size) {
                    result.add(timing[i + j])
                }
            }

            // Salta le ripetizioni
            i += frameLen
            while (i + frameLen <= timing.size) {
                var isRepeat = true
                for (j in 0 until frameLen) {
                    if (timing[i + j] != result[result.size - frameLen + j]) {
                        isRepeat = false
                        break
                    }
                }
                if (isRepeat) {
                    i += frameLen
                } else {
                    break
                }
            }
        }

        return result.toIntArray()
    }

    private fun findFrameLength(timing: IntArray, start: Int): Int {
        if (start + 4 >= timing.size) return -1

        // Cerca un gap lungo (fine frame) nei primi 200 elementi
        val searchEnd = minOf(start + 200, timing.size)
        for (i in start + 1 until searchEnd) {
            if (timing[i] > 30000) { // Gap > 30ms = fine frame
                return i - start + 1
            }
        }

        return -1
    }
}
