package com.irxiaomi.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.irxiaomi.db.IrCodeEntity

/**
 * Interfaccia astratta per la trasmissione IR.
 * L'implementazione concreta dipende dal dispositivo (API standard, Xiaomi, sysfs, root).
 */
interface IrManager {
    /** Nome del driver IR in uso */
    val name: String

    /** Il dispositivo supporta IR? */
    fun isSupported(): Boolean

    /** Invia un pattern raw alla frequenza specificata */
    fun transmit(frequency: Int, pattern: IntArray): Boolean

    /** Invia un codice IREntity */
    fun transmitCode(code: IrCodeEntity): Boolean

    /** Invia un pattern Pronto hex (formato standard universale) */
    fun transmitPronto(prontoHex: String): Boolean

    /** Invia un codice decodificato (ri-genera il pattern dal protocollo) */
    fun transmitDecoded(protocol: String, address: Long, command: Long, frequency: Int): Boolean

    /** Interrompe ripetizioni in corso */
    fun cancelTransmission()

    /** Temperatura del dispositivo (per debugging) */
    fun getDeviceInfo(): Map<String, Any>
}

/**
 * Implementazione standard tramite ConsumerIrManager (API Android 19+).
 * Funziona su: Samsung, LG, HTC, Google Pixel (pochi), e alcuni Xiaomi con API standard.
 */
class ConsumerIrManagerImpl(context: Context) : IrManager {

    companion object {
        private const val TAG = "ConsumerIrManager"
    }

    private val consumerIrManager: ConsumerIrManager? = try {
        val service = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        service
    } catch (e: Exception) {
        Log.w(TAG, "ConsumerIrManager not available", e)
        null
    }

    override val name: String get() = "ConsumerIrManager (API Standard)"

    override fun isSupported(): Boolean {
        return consumerIrManager?.hasIrEmitter() == true
    }

    override fun transmit(frequency: Int, pattern: IntArray): Boolean {
        return try {
            if (!isSupported()) {
                Log.w(TAG, "IR not supported on this device")
                return false
            }
            consumerIrManager?.transmit(frequency, pattern)
            Log.d(TAG, "Transmitted: freq=$frequency, len=${pattern.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Transmit failed: freq=$frequency, len=${pattern.size}", e)
            false
        }
    }

    override fun transmitCode(code: IrCodeEntity): Boolean {
        val pattern = code.parsePattern()
        if (pattern.isEmpty()) {
            Log.w(TAG, "Empty pattern for code: ${code.name}")
            return false
        }
        return transmit(code.frequency, pattern)
    }

    override fun transmitPronto(prontoHex: String): Boolean {
        val (freq, pattern) = ProntoParser.parsePronto(prontoHex) ?: return false
        return transmit(freq, pattern)
    }

    override fun transmitDecoded(protocol: String, address: Long, command: Long, frequency: Int): Boolean {
        val pattern = PatternGenerator.generate(protocol, address, command)
            ?: return false
        return transmit(frequency, pattern)
    }

    override fun cancelTransmission() {
        // ConsumerIrManager non supporta cancellazione; i pattern vengono inviati una volta
        Log.d(TAG, "Cancel requested (no-op for ConsumerIrManager)")
    }

    override fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "supported" to isSupported(),
            "carrier_frequencies" to (consumerIrManager?.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency}Hz" } ?: "unknown")
        )
    }
}

/**
 * Parser per formato Pronto (standard universale per codici IR).
 */
object ProntoParser {
    /**
     * Formato Pronto: "0000 006D 0000 0022 00AC 00AC ..."
     * - 0000: raw
     * - 006D: frequency code (convertito in Hz)
     * - 0000: burst pairs (1)
     * - 0022: number of pairs
     * - ...: timing in microsecondi (conversione)
     */
    fun parsePronto(hex: String): Pair<Int, IntArray>? {
        return try {
            val parts = hex.trim().split("\\s+".toRegex()).map { it.toInt(16) }
            if (parts.size < 6) return null

            val freqCode = parts[1]
            val freq = 1000000 / (freqCode * 0.241246).toInt()

            val burst1 = parts[2]  // numero burst nella prima sequenza
            val burst2 = parts[3]  // numero burst nella seconda sequenza
            val totalBursts = burst1 + burst2

            if (parts.size < 4 + totalBursts) return null

            // La conversione: ogni valore * 0.241246 = microseconds
            val pattern = IntArray(totalBursts) { i ->
                (parts[4 + i] * 0.241246).toInt()
            }

            Pair(freq, pattern)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Generatore di pattern per protocolli comuni.
 * Permette di creare il pattern raw a partire da address + command decodificati.
 */
object PatternGenerator {
    /**
     * Genera un pattern NEC: leader (9ms on, 4.5ms off), 16 address + 16 inverted address,
     * 8 command + 8 inverted command.
     */
    fun generateNec(address: Long, command: Long): IntArray {
        val pattern = mutableListOf<Int>()
        // Leader
        pattern.add(9000)
        pattern.add(4500)

        val addr = address.toInt() and 0xFFFF
        val cmd = command.toInt() and 0xFF
        val addrInv = addr.inv() and 0xFFFF
        val cmdInv = cmd.inv() and 0xFF

        // Address (16 bit) + inverted address (16 bit)
        for (i in 0 until 32) {
            val bit = (if (i < 16) (addr shr (15 - i)) else (addrInv shr (15 - (i - 16)))) and 1
            pattern.add(560)           // burst
            pattern.add(if (bit == 1) 1690 else 560)  // space
        }

        // Command (8 bit) + inverted command (8 bit)
        for (i in 0 until 16) {
            val bit = (if (i < 8) (cmd shr (7 - i)) else (cmdInv shr (7 - (i - 8)))) and 1
            pattern.add(560)           // burst
            pattern.add(if (bit == 1) 1690 else 560)  // space
        }

        // Stop bit
        pattern.add(560)

        return pattern.toIntArray()
    }

    fun generateSamsung(address: Long, command: Long): IntArray {
        val pattern = mutableListOf<Int>()
        // Leader Samsung: 4.5ms on, 4.5ms off
        pattern.add(4500)
        pattern.add(4500)

        val addr = address.toInt() and 0xFFFF
        val cmd = command.toInt() and 0xFF

        // Address (16 bit)
        for (i in 0 until 16) {
            val bit = (addr shr (15 - i)) and 1
            pattern.add(560)
            pattern.add(if (bit == 1) 1690 else 560)
        }

        // Command (8 bit) + inverted (8 bit)
        for (i in 0 until 16) {
            val bit = (if (i < 8) (cmd shr (7 - i)) else ((cmd.inv() and 0xFF) shr (7 - (i - 8)))) and 1
            pattern.add(560)
            pattern.add(if (bit == 1) 1690 else 560)
        }

        pattern.add(560)
        return pattern.toIntArray()
    }

    /** Genera pattern Sony SIRC 12 bit. */
    fun generateSony(command: Long, deviceAddress: Long = 1L): IntArray {
        val cmdBits = 7
        val addrBits = 5
        val pattern = mutableListOf<Int>()

        // Leader
        pattern.add(2400)
        pattern.add(600)

        // Command (7 bit, LSB first)
        for (i in 0 until cmdBits) {
            val bit = ((command shr i) and 1L).toInt()
            pattern.add(600)
            pattern.add(if (bit == 1) 1200 else 600)
        }

        // Device address (5 bit, LSB first)
        for (i in 0 until addrBits) {
            val bit = ((deviceAddress shr i) and 1L).toInt()
            pattern.add(600)
            pattern.add(if (bit == 1) 1200 else 600)
        }

        return pattern.toIntArray()
    }

    fun generateRc5(address: Long, command: Long): IntArray {
        // RC5: 36 kHz, Manchester coding, 14 bits total
        // Start bit (1), Field bit (1), Control bit (1), System (5 bits), Command (6 bits)
        val pattern = mutableListOf<Int>()

        val system = address.toInt() and 0x1F
        val cmd = command.toInt() and 0x3F

        // Manchester encoding
        val bits = mutableListOf<Int>()
        bits.add(1)  // Start
        bits.add(1)  // Field
        bits.add(1)  // Control (toggle)
        for (i in 4 downTo 0) bits.add((system shr i) and 1)
        for (i in 5 downTo 0) bits.add((cmd shr i) and 1)

        // RC5: half-bit time = 889µs (36kHz), full bit = 1778µs
        var lastBit = 1
        for (bit in bits) {
            if (bit == lastBit) {
                // Same as previous: no transition at start => 2 half-bits
                pattern.add(889)  // first half at same level
                pattern.add(889)  // transition + second half
            } else {
                // Bit changed: transition at start
                pattern.add(1778) // full bit
            }
            lastBit = bit
        }

        return pattern.toIntArray()
    }

    fun generate(protocol: String, address: Long, command: Long, frequency: Int? = null): IntArray? {
        return when (protocol.uppercase()) {
            "NEC" -> generateNec(address, command)
            "SAMSUNG" -> generateSamsung(address, command)
            "RC5" -> generateRc5(address, command)
            else -> null  // Protocollo non supportato per generazione
        }
    }
}
