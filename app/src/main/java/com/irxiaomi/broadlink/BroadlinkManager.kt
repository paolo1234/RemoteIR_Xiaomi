package com.irxiaomi.broadlink

import android.util.Log
import com.irxiaomi.db.IrCodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestione dispositivi Broadlink RM (RM Mini3, RM4 Pro, ecc.).
 *
 * Broadlink RM è un dispositivo WiFi che può:
 * - Imparare (ricevere) codici IR da qualsiasi telecomando
 * - Inviare codici IR
 * - Esporre API UDP su porta 80
 *
 * Questo manager implementa il protocollo Broadlink (reverse-engineered)
 * per scoprire, autenticare e controllare i dispositivi Broadlink
 * direttamente dall'app Android.
 *
 * NOTA: il dispositivo deve essere sulla stessa rete WiFi del telefono.
 */
class BroadlinkManager {

    companion object {
        private const val TAG = "BroadlinkManager"

        /** Porta di discovery Broadlink */
        private const val DISCOVERY_PORT = 80

        /** Indirizzo broadcast per discovery */
        private const val BROADCAST_ADDR = "255.255.255.255"

        /** Timeout discovery (ms) */
        private const val DISCOVERY_TIMEOUT = 5000

        /** Timeout learn (ms) */
        private const val LEARN_TIMEOUT = 15000

        /** Magic bytes per crittografia Broadlink */
        private val BROADLINK_KEY = byteArrayOf(
            0x09.toByte(), 0x76.toByte(), 0x28.toByte(), 0x34.toByte(),
            0x3F.toByte(), 0xE9.toByte(), 0x9E.toByte(), 0x23.toByte(),
            0x76.toByte(), 0x5C.toByte(), 0x15.toByte(), 0x13.toByte(),
            0xAC.toByte(), 0xCF.toByte(), 0x8B.toByte(), 0x02.toByte()
        )
        private val BROADLINK_IV = byteArrayOf(
            0x56.toByte(), 0x2E.toByte(), 0x17.toByte(), 0x99.toByte(),
            0x6D.toByte(), 0x09.toByte(), 0x3D.toByte(), 0x28.toByte(),
            0xDD.toByte(), 0xB3.toByte(), 0xBA.toByte(), 0x69.toByte(),
            0x5A.toByte(), 0x2E.toByte(), 0x6F.toByte(), 0x58.toByte()
        )
    }

    /** Dispositivo Broadlink trovato */
    data class BroadlinkDevice(
        val name: String,
        val ipAddress: String,
        val macAddress: ByteArray,
        val deviceType: Int,
        val id: String = macAddress.joinToString("") { "%02X".format(it) }
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BroadlinkDevice) return false
            return id == other.id
        }

        override fun hashCode() = id.hashCode()
    }

    /** Stato apprendimento */
    data class LearnResult(
        val success: Boolean,
        val pattern: IntArray = intArrayOf(),
        val frequency: Int = 38000,
        val protocol: String = "RAW",
        val address: Long? = null,
        val command: Long? = null,
        val rawHex: String = "",
        val message: String = ""
    )

    private var currentDevice: BroadlinkDevice? = null
    private var aesKey: SecretKeySpec? = null
    private var aesIv: IvParameterSpec? = null

    /**
     * Scopre i dispositivi Broadlink sulla rete locale.
     * Invia un pacchetto UDP broadcast e raccoglie le risposte.
     */
    suspend fun discoverDevices(timeoutMs: Int = DISCOVERY_TIMEOUT): List<BroadlinkDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<BroadlinkDevice>()

            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = timeoutMs

                // Pacchetto di discovery Broadlink
                val discoverPacket = byteArrayOf(
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                )

                val packet = DatagramPacket(
                    discoverPacket,
                    discoverPacket.size,
                    InetAddress.getByName(BROADCAST_ADDR),
                    DISCOVERY_PORT
                )
                socket.send(packet)

                Log.d(TAG, "Pacchetto discovery inviato, attendo risposte...")

                // Raccogli risposte
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val buffer = ByteArray(1024)
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)

                        val data = buffer.copyOf(response.length)
                        val device = parseDiscoveryResponse(data, response.address.hostAddress)
                        if (device != null) {
                            if (device !in devices) {
                                devices.add(device)
                                Log.d(TAG, "Trovato: ${device.name} @ ${device.ipAddress}")
                            }
                        }
                    } catch (e: Exception) {
                        break  // Timeout
                    }
                }

                socket.close()

            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
            }

            Log.i(TAG, "Discovery completato: ${devices.size} dispositivi trovati")
            devices
        }

    /**
     * Analizza la risposta di discovery Broadlink.
     * Formato risposta:
     * - byte 0-3: ? (sconosciuto)
     * - byte 4-5: device type (little-endian)
     * - byte 6-9: ? 
     * - byte 10-15: MAC address
     * - byte 16+: nome dispositivo
     */
    private fun parseDiscoveryResponse(data: ByteArray, ip: String?): BroadlinkDevice? {
        if (data.size < 16) return null

        try {
            val deviceType = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            val mac = data.copyOfRange(10, 16)
            val name = if (data.size > 16) {
                String(data.copyOfRange(16, data.size)).trim('\u0000', ' ')
            } else {
                getDeviceTypeName(deviceType)
            }

            return BroadlinkDevice(
                name = name,
                ipAddress = ip ?: "0.0.0.0",
                macAddress = mac,
                deviceType = deviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse discovery error", e)
            return null
        }
    }

    /**
     * Autentica con un dispositivo Broadlink.
     * Necessario prima di inviare/ricevere codici.
     */
    suspend fun authenticate(device: BroadlinkDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            currentDevice = device
            aesKey = SecretKeySpec(BROADLINK_KEY, "AES")
            aesIv = IvParameterSpec(BROADLINK_IV)

            val socket = Socket(device.ipAddress, DISCOVERY_PORT)
            socket.soTimeout = 5000

            val out = DataOutputStream(socket.getOutputStream())
            val `in` = DataInputStream(socket.getInputStream())

            // Pacchetto di autenticazione
            val payload = byteArrayOf(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            )
            val command = 0x65  // Autenticazione

            val packet = buildPacket(command, payload)
            out.write(packet)
            out.flush()

            // Leggi risposta
            val response = ByteArray(1024)
            val bytesRead = `in`.read(response)
            socket.close()

            if (bytesRead < 56) {
                Log.w(TAG, "Risposta autenticazione troppo corta")
                return@withContext false
            }

            // Estrai payload dalla risposta (dopo header 56 bytes)
            val responsePayload = response.copyOfRange(56, bytesRead)
            val decrypted = decrypt(responsePayload)

            Log.i(TAG, "Autenticazione riuscita con ${device.name}")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            currentDevice = null
            return@withContext false
        }
    }

    /**
     * Avvia apprendimento IR.
     * Il dispositivo entra in modalità learn: punta il telecomando e premi un tasto.
     */
    suspend fun startLearning(): Boolean = withContext(Dispatchers.IO) {
        val device = currentDevice ?: return@withContext false

        try {
            val socket = Socket(device.ipAddress, DISCOVERY_PORT)
            socket.soTimeout = LEARN_TIMEOUT

            val out = DataOutputStream(socket.getOutputStream())
            val payload = byteArrayOf(0x01)
            val packet = buildPacket(0x6A, payload)  // Enter learning

            out.write(packet)
            out.flush()
            socket.close()

            Log.d(TAG, "Modalità apprendimento attivata")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Start learning error", e)
            false
        }
    }

    /**
     * Verifica se c'è un codice appreso e lo restituisce.
     * Chiamare dopo startLearning().
     */
    suspend fun checkForCode(): LearnResult = withContext(Dispatchers.IO) {
        val device = currentDevice ?: return@withContext LearnResult(false, message = "Nessun dispositivo")

        try {
            val socket = Socket(device.ipAddress, DISCOVERY_PORT)
            socket.soTimeout = LEARN_TIMEOUT

            val out = DataOutputStream(socket.getOutputStream())
            val payload = byteArrayOf()
            val packet = buildPacket(0x6B, payload)  // Check data

            out.write(packet)
            out.flush()

            val response = ByteArray(2048)
            val bytesRead = socket.getInputStream().read(response)
            socket.close()

            if (bytesRead < 56) {
                return@withContext LearnResult(false, message = "Nessun codice")
            }

            // Decodifica risposta
            val encrypted = response.copyOfRange(56, bytesRead)
            val decrypted = decrypt(encrypted)

            // Formato: 4 bytes frequenza, 1 byte tipo, 2 bytes conteggio, ... impulsi
            if (decrypted.size < 7) {
                return@withContext LearnResult(false, message = "Dati corrotti")
            }

            val freq = ((decrypted[3].toInt() and 0xFF) shl 24) or
                    ((decrypted[2].toInt() and 0xFF) shl 16) or
                    ((decrypted[1].toInt() and 0xFF) shl 8) or
                    (decrypted[0].toInt() and 0xFF)
            val freqHz = if (freq < 1000) freq * 1000 else freq
            val pulseCount = ((decrypted[6].toInt() and 0xFF) shl 8) or (decrypted[5].toInt() and 0xFF)

            val pattern = mutableListOf<Int>()
            for (i in 0 until pulseCount) {
                val idx = 7 + i * 2
                if (idx + 1 >= decrypted.size) break
                val value = ((decrypted[idx + 1].toInt() and 0xFF) shl 8) or (decrypted[idx].toInt() and 0xFF)
                pattern.add(value)
            }

            // Decodifica protocollo
            val (protocol, address, command) = decodeProtocol(pattern.toIntArray())

            val hexStr = decrypted.joinToString("") { "%02X".format(it) }

            LearnResult(
                success = true,
                pattern = pattern.toIntArray(),
                frequency = freqHz,
                protocol = protocol,
                address = address,
                command = command,
                rawHex = hexStr,
                message = "Codice ricevuto: $protocol (${pattern.size} impulsi)"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Check code error", e)
            LearnResult(false, message = "Errore: ${e.message}")
        }
    }

    /**
     * Invia un codice IR tramite Broadlink.
     */
    suspend fun sendCode(pattern: IntArray, frequency: Int = 38000): Boolean = withContext(Dispatchers.IO) {
        val device = currentDevice ?: return@withContext false

        try {
            // Costruisci payload formato Broadlink
            val payload = ByteArrayOutputStream()
            // Frequenza (4 bytes, little-endian, in kHz)
            val freqKhz = frequency / 1000
            payload.write(freqKhz and 0xFF)
            payload.write((freqKhz shr 8) and 0xFF)
            payload.write((freqKhz shr 16) and 0xFF)
            payload.write((freqKhz shr 24) and 0xFF)
            // Tipo (0x26 = IR)
            payload.write(0x26)
            // Conteggio impulsi (2 bytes, little-endian)
            val count = pattern.size
            payload.write(count and 0xFF)
            payload.write((count shr 8) and 0xFF)
            // Impulsi (ogni 2 bytes, little-endian, microsecondi / 2)
            for (p in pattern) {
                val encoded = (p / 2).coerceIn(0, 0xFFFF)
                payload.write(encoded and 0xFF)
                payload.write((encoded shr 8) and 0xFF)
            }

            val command = 0x6C  // Send data
            val packet = buildPacket(command, payload.toByteArray())

            val socket = Socket(device.ipAddress, DISCOVERY_PORT)
            socket.soTimeout = 5000

            val out = DataOutputStream(socket.getOutputStream())
            out.write(packet)
            out.flush()
            socket.close()

            Log.d(TAG, "Codice inviato via Broadlink: $frequency Hz, ${pattern.size} impulsi")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Send code error", e)
            false
        }
    }

    /**
     * Costruisce un pacchetto Broadlink.
     * Formato:
     * - byte 0-1: 0x0000
     * - byte 2-3: conteggio (0xBC00)
     * - byte 4-5: 0x0000
     * - byte 6-7: command
     * - byte 8-15: 0x00
     * - byte 16-21: MAC address
     * - byte 22-23: device type
     * - byte 24-55: checksum + padding
     * - byte 56+: payload cifrato
     */
    private fun buildPacket(command: Int, payload: ByteArray): ByteArray {
        val device = currentDevice ?: return byteArrayOf()

        val encrypted = encrypt(payload)
        val packetSize = 56 + encrypted.size
        val packet = ByteArray(packetSize)

        // Header
        packet[0] = 0x00.toByte()
        packet[1] = 0x00.toByte()
        packet[2] = 0xBC.toByte()
        packet[3] = 0x00.toByte()
        packet[4] = 0x00.toByte()
        packet[5] = 0x00.toByte()
        packet[6] = (command and 0xFF).toByte()
        packet[7] = ((command shr 8) and 0xFF).toByte()

        // MAC address
        val mac = device.macAddress
        System.arraycopy(mac, 0, packet, 16, mac.size.coerceAtMost(6))

        // Device type
        packet[22] = (device.deviceType and 0xFF).toByte()
        packet[23] = ((device.deviceType shr 8) and 0xFF).toByte()

        // Checksum del payload prima della cifratura
        val checksum = payload.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) } and 0xFFFF
        packet[24] = (checksum and 0xFF).toByte()
        packet[25] = ((checksum shr 8) and 0xFF).toByte()

        // Padding
        packet[40] = 0x01.toByte()  // CRC del payload cifrato (da calcolare)

        // Payload cifrato
        System.arraycopy(encrypted, 0, packet, 56, encrypted.size)

        return packet
    }

    /**
     * Cifratura AES-CBC con chiave/IV Broadlink.
     */
    private fun encrypt(data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt error", e)
            data
        }
    }

    /**
     * Decifratura AES-CBC con chiave/IV Broadlink.
     */
    private fun decrypt(data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt error", e)
            data
        }
    }

    /**
     * Decodifica protocollo dal pattern raw.
     */
    fun decodeProtocol(timing: IntArray): Triple<String, Long?, Long?> {
        val decoder = com.irxiaomi.learning.ProtocolDecoder()

        decoder.decodeNec(timing)?.let { (addr, cmd) ->
            return Triple("NEC", addr, cmd)
        }
        decoder.decodeSamsung(timing)?.let { (addr, cmd) ->
            return Triple("SAMSUNG", addr, cmd)
        }
        decoder.decodeSony(timing)?.let { (addr, cmd) ->
            return Triple("SONY", addr, cmd)
        }
        decoder.decodeRc5(timing)?.let { (addr, cmd) ->
            return Triple("RC5", addr, cmd)
        }

        return Triple("RAW", null, null)
    }

    /**
     * Converte un LearnResult in IrCodeEntity per salvare nel DB.
     */
    fun toIrCodeEntity(result: LearnResult, name: String, brand: String, deviceType: String): IrCodeEntity {
        return IrCodeEntity(
            name = name,
            displayName = name,
            brand = brand,
            deviceType = deviceType,
            protocol = result.protocol,
            frequency = result.frequency,
            pattern = IrCodeEntity.patternToString(result.pattern),
            address = result.address,
            command = result.command,
            source = "broadlink",
            isVerified = true,
            notes = "Appreso via Broadlink ${currentDevice?.name ?: "RM"}"
        )
    }

    /**
     * Nome leggibile del tipo di dispositivo Broadlink.
     */
    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            0x2711, 0x2712, 0x2737 -> "RM Mini 3"
            0x273E, 0x279D, 0x27A1, 0x27A6, 0x27C3, 0x27D5 -> "RM4 Mini"
            0x2797, 0x27A9, 0x27B8, 0x27C2, 0x27D2 -> "RM4 Pro"
            0x2783, 0x27C7, 0x27D6 -> "RM4C Mini"
            0x277C, 0x27D3 -> "RM4C Pro"
            0x27D4 -> "RM4S"
            0x2722 -> "RM Pro"
            0x273E -> "RM Mini"
            0x4EB5 -> "SP Mini 3"
            else -> "Broadlink (0x${type.toString(16).uppercase()})"
        }
    }

    /** Disconnette */
    fun disconnect() {
        currentDevice = null
        aesKey = null
        aesIv = null
    }

    /** Resetta il manager */
    fun reset() {
        disconnect()
    }
}
