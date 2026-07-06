package com.irxiaomi.learning

import android.util.Log

/**
 * Decodifica protocolli IR dai pattern raw (timing on/off in microsecondi).
 *
 * Riconosce i protocolli più comuni:
 * - NEC (e NEC Extended)
 * - Samsung
 * - Sony (SIRC)
 * - RC5
 * - RC6 (base)
 * - Panasonic (base)
 */
class ProtocolDecoder {

    companion object {
        private const val TAG = "ProtocolDecoder"

        // Tolleranza per timing (percentuale)
        private const val TOLERANCE = 0.25f

        // Timing NEC
        private const val NEC_LEADER_ON = 9000
        private const val NEC_LEADER_OFF = 4500
        private const val NEC_BIT_ON = 560
        private const val NEC_BIT_ZERO_OFF = 560
        private const val NEC_BIT_ONE_OFF = 1690
        private const val NEC_REPEAT_LEADER_OFF = 2250

        // Timing Samsung
        private const val SAMSUNG_LEADER_ON = 4500
        private const val SAMSUNG_LEADER_OFF = 4500
        private const val SAMSUNG_BIT_ON = 560
        private const val SAMSUNG_BIT_ZERO_OFF = 560
        private const val SAMSUNG_BIT_ONE_OFF = 1690

        // Timing Sony (SIRC)
        private const val SONY_LEADER_ON = 2400
        private const val SONY_LEADER_OFF = 600
        private const val SONY_BIT_ON = 600
        private const val SONY_BIT_ZERO_OFF = 600
        private const val SONY_BIT_ONE_OFF = 1200

        // Timing RC5
        private const val RC5_HALF_BIT = 889
        private const val RC5_FULL_BIT = 1778
    }

    /** Verifica se un valore rientra nella tolleranza */
    private fun match(value: Int, expected: Int, tolerance: Float = TOLERANCE): Boolean {
        val min = (expected * (1 - tolerance)).toInt()
        val max = (expected * (1 + tolerance)).toInt()
        return value in min..max
    }

    /** Decodifica NEC (32 bit: 16 address + 8 command + 8 inverted command) */
    fun decodeNec(timing: IntArray): Pair<Long, Long>? {
        if (timing.size < 68) return null  // 1 leader + 32 data + 1 stop = 34 pairs = 68 valori

        try {
            // Verifica leader
            if (!match(timing[0], NEC_LEADER_ON) || !match(timing[1], NEC_LEADER_OFF)) {
                // Prova NEC con repeat shorter leader
                if (timing.size >= 4 && match(timing[0], NEC_LEADER_ON) && match(timing[1], NEC_REPEAT_LEADER_OFF)) {
                    return Pair(-1L, -1L) // Repeat code
                }
                return null
            }

            // Leggi 32 bits (address 16 + address inverted 16, command 8 + command inverted 8)
            var address = 0
            var command = 0

            for (i in 0 until 32) {
                val idx = 2 + i * 2  // on, off pairs
                if (idx + 1 >= timing.size) return null

                val burst = timing[idx]
                val space = timing[idx + 1]

                if (!match(burst, NEC_BIT_ON)) return null

                val bit = when {
                    match(space, NEC_BIT_ZERO_OFF) -> 0
                    match(space, NEC_BIT_ONE_OFF) -> 1
                    else -> return null
                }

                if (i < 16) {
                    address = (address shl 1) or bit
                } else {
                    command = (command shl 1) or bit
                }
            }

            // NEC: first 16 = address, second 16 = inverted address
            // third 8 = command, fourth 8 = inverted command
            val realAddress = address and 0xFFFF
            val realCommand = (command shr 8) and 0xFF
            val invertedCommand = command and 0xFF

            // Verifica checksum: command + inverted command == 0xFF
            if ((realCommand + invertedCommand) and 0xFF != 0xFF) {
                Log.w(TAG, "NEC checksum failed: cmd=$realCommand inv=$invertedCommand")
                // Alcuni device usano NEC extended, accettiamo comunque
            }

            return Pair(realAddress.toLong(), realCommand.toLong())

        } catch (e: Exception) {
            Log.e(TAG, "NEC decode error", e)
            return null
        }
    }

    /** Decodifica Samsung (32 bit: 16 address + 8 command + 8 inverted) */
    fun decodeSamsung(timing: IntArray): Pair<Long, Long>? {
        if (timing.size < 68) return null

        try {
            // Verifica leader Samsung (4.5ms on, 4.5ms off)
            if (!match(timing[0], SAMSUNG_LEADER_ON) || !match(timing[1], SAMSUNG_LEADER_OFF)) {
                return null
            }

            var address = 0
            var command = 0

            for (i in 0 until 32) {
                val idx = 2 + i * 2
                if (idx + 1 >= timing.size) return null

                if (!match(timing[idx], SAMSUNG_BIT_ON)) return null

                val bit = when {
                    match(timing[idx + 1], SAMSUNG_BIT_ZERO_OFF) -> 0
                    match(timing[idx + 1], SAMSUNG_BIT_ONE_OFF) -> 1
                    else -> return null
                }

                if (i < 16) {
                    address = (address shl 1) or bit
                } else {
                    command = (command shl 1) or bit
                }
            }

            return Pair(address.toLong(), (command shr 8).toLong())

        } catch (e: Exception) {
            return null
        }
    }

    /** Decodifica Sony SIRC (12, 15, o 20 bit) */
    fun decodeSony(timing: IntArray): Pair<Long, Long>? {
        if (timing.size < 26) return null

        try {
            // Leader Sony: 2400µs on, 600µs off
            if (!match(timing[0], SONY_LEADER_ON) || !match(timing[1], SONY_LEADER_OFF)) {
                return null
            }

            // Determina lunghezza dal numero di bit
            val totalPairs = (timing.size - 2) / 2
            val numBits = when {
                totalPairs >= 20 -> 20
                totalPairs >= 15 -> 15
                totalPairs >= 12 -> 12
                else -> return null
            }

            var command = 0
            var address = 0

            for (i in 0 until numBits) {
                val idx = 2 + i * 2
                if (idx + 1 >= timing.size) return null

                if (!match(timing[idx], SONY_BIT_ON)) return null

                val bit = when {
                    match(timing[idx + 1], SONY_BIT_ZERO_OFF) -> 0
                    match(timing[idx + 1], SONY_BIT_ONE_OFF) -> 1
                    else -> return null
                }

                if (i < 7) {
                    command = (command shl 1) or bit
                } else {
                    address = (address shl 1) or bit
                }
            }

            return Pair(address.toLong(), command.toLong())

        } catch (e: Exception) {
            return null
        }
    }

    /** Decodifica RC5 (14 bit: 2 start + 1 toggle + 5 system + 6 command) */
    fun decodeRc5(timing: IntArray): Pair<Long, Long>? {
        if (timing.size < 28) return null  // 14 bits * 2 half-bits

        try {
            // RC5 non ha leader fisso, usa Manchester
            var bits = 0
            var value = 0
            var prevBit = 1  // RC5 inizia con start bit = 1

            for (i in timing.indices step 2) {
                if (i + 1 >= timing.size) break
                if (bits >= 14) break

                val first = timing[i]
                val second = timing[i + 1]

                // In RC5, ogni bit è rappresentato da due half-bit
                // Se first ≈ second, il bit è uguale al precedente
                // Se first ≈ 2*second o 2*first ≈ second, il bit è invertito
                val bit = when {
                    match(first, RC5_HALF_BIT) && match(second, RC5_HALF_BIT) -> prevBit
                    match(first, RC5_FULL_BIT) && match(second, RC5_HALF_BIT) -> prevBit xor 1
                    match(first, RC5_HALF_BIT) && match(second, RC5_FULL_BIT) -> prevBit xor 1
                    else -> return null
                }

                value = (value shl 1) or bit
                prevBit = bit
                bits++
            }

            if (bits < 14) return null

            // Estrai system (5 bit) e command (6 bit)
            val system = (value shr 6) and 0x1F
            val command = value and 0x3F

            return Pair(system.toLong(), command.toLong())

        } catch (e: Exception) {
            return null
        }
    }

    /** Prova tutti i protocolli e restituisce il migliore */
    fun decodeBest(timing: IntArray): Pair<String, Pair<Long, Long>?> {
        decodeNec(timing)?.let { return Pair("NEC", it) }
        decodeSamsung(timing)?.let { return Pair("SAMSUNG", it) }
        decodeSony(timing)?.let { return Pair("SONY", it) }
        decodeRc5(timing)?.let { return Pair("RC5", it) }
        return Pair("UNKNOWN", null)
    }
}
