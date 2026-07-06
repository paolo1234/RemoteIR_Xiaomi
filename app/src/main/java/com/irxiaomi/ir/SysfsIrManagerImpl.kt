package com.irxiaomi.ir

import android.content.Context
import android.util.Log
import java.io.*

/**
 * Implementazione IR tramite sysfs (richiede root o permessi speciali).
 * Pathway comuni:
 * - /sys/class/sec/ir/ir_send  (Samsung)
 * - /sys/devices/platform/ir.0/ir_send  (MTK)
 * - /sys/class/irda/ir_send
 * - /proc/ir/ir_send
 * - /dev/ir  (dispositivo a caratteri)
 *
 * NOTA: richiede root o SELinux permissivo sulla maggior parte dei dispositivi.
 */
class SysfsIrManagerImpl(context: Context) : IrManager {

    companion object {
        private const val TAG = "SysfsIrManager"

        /** Path sysfs da provare in ordine */
        private val SYSFS_PATHS = listOf(
            "/sys/class/sec/ir/ir_send",
            "/sys/devices/platform/ir.0/ir_send",
            "/sys/class/irda/ir_send",
            "/proc/ir/ir_send",
            "/sys/kernel/ir/ir_send",
            "/sys/class/misc/irtx/transmit",
            "/sys/devices/virtual/ir/ir_send"
        )

        /** Path dispositivo a caratteri */
        private val DEV_PATHS = listOf(
            "/dev/ir",
            "/dev/irtx",
            "/dev/irled"
        )
    }

    private var activePath: String? = null
    private var useDev: Boolean = false

    override val name: String get() = "Sysfs IR (${activePath ?: "none"})"

    init {
        findPath()
    }

    private fun findPath() {
        // Prova path sysfs
        for (path in SYSFS_PATHS) {
            if (File(path).canWrite()) {
                activePath = path
                useDev = false
                Log.i(TAG, "Found writable IR sysfs: $path")
                return
            }
        }

        // Prova /dev/ paths
        for (path in DEV_PATHS) {
            if (File(path).canWrite()) {
                activePath = path
                useDev = true
                Log.i(TAG, "Found writable IR dev: $path")
                return
            }
        }

        // Prova con root
        for (path in SYSFS_PATHS + DEV_PATHS) {
            if (checkRootAccess(path)) {
                activePath = path
                useDev = path.startsWith("/dev/")
                Log.i(TAG, "Found IR via root: $path")
                return
            }
        }

        Log.w(TAG, "No IR sysfs path found")
    }

    private fun checkRootAccess(path: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("echo test > $path 2>/dev/null\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun writeSysfs(path: String, data: String): Boolean {
        return try {
            if (useDev) {
                // /dev/ paths: scrittura diretta
                FileOutputStream(path).use { fos ->
                    fos.write(data.toByteArray())
                    fos.flush()
                }
            } else {
                // sysfs paths
                FileWriter(path).use { fw ->
                    fw.write(data)
                    fw.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write failed to $path", e)
            false
        }
    }

    private fun writeRoot(path: String, data: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("echo '$data' > $path\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root write failed", e)
            false
        }
    }

    override fun isSupported(): Boolean {
        return activePath != null
    }

    override fun transmit(frequency: Int, pattern: IntArray): Boolean {
        val path = activePath ?: return false

        // Formato: "frequenza,pattern" (es. "38000,9000,4500,560,560,...")
        val data = "$frequency,${pattern.joinToString(",")}"

        return if (writeSysfs(path, data)) true
        else writeRoot(path, data)
    }

    override fun transmitCode(code: com.irxiaomi.db.IrCodeEntity): Boolean {
        val pattern = code.parsePattern()
        if (pattern.isEmpty()) return false
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
        // sysfs non supporta cancellazione
        Log.d(TAG, "Cancel not supported for sysfs")
    }

    override fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "supported" to isSupported(),
            "path" to (activePath ?: "none"),
            "use_root" to false,
            "access" to if (activePath != null && File(activePath).canWrite()) "direct" else "root"
        )
    }
}
