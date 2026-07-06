package com.irxiaomi.ir

import android.content.Context
import android.util.Log

/**
 * Implementazione per dispositivi Xiaomi / MIUI.
 * Utilizza reflection per accedere al servizio IR proprietario MIUI.
 *
 * I percorsi di classe noti:
 * - com.xiaomi.ir.IRService  (MIUI 10+)
 * - com.xiaomi.ir.IrService
 * - android.hardware.ir.IrService (alcune versioni)
 */
class XiaomiIrManagerImpl(context: Context) : IrManager {

    companion object {
        private const val TAG = "XiaomiIrManager"

        /** Classi conosciute del servizio IR MIUI */
        private val IR_SERVICE_CLASSES = listOf(
            "com.xiaomi.ir.IRService",
            "com.xiaomi.ir.IrService",
            "android.hardware.ir.IrService"
        )

        /** Metodi conosciuti per la trasmissione */
        private val TRANSMIT_METHODS = listOf(
            "transmit",
            "sendIR",
            "send",
            "irSend"
        )
    }

    private var irService: Any? = null
    private var transmitMethod: java.lang.reflect.Method? = null

    override val name: String get() = "Xiaomi MIUI IR Service (Reflection)"

    init {
        initializeService()
    }

    private fun initializeService() {
        for (className in IR_SERVICE_CLASSES) {
            try {
                val clazz = Class.forName(className)
                // Prova getInstance() prima
                val getInstance = try {
                    clazz.getMethod("getInstance")
                } catch (e: NoSuchMethodException) {
                    try {
                        clazz.getMethod("getService")
                    } catch (e2: NoSuchMethodException) {
                        null
                    }
                }

                irService = getInstance?.invoke(null)

                if (irService == null) {
                    // Prova costruttore context
                    try {
                        val ctor = clazz.getDeclaredConstructor(Context::class.java)
                        ctor.isAccessible = true
                        irService = ctor.newInstance(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot instantiate $className", e)
                    }
                }

                if (irService != null) {
                    // Trova il metodo di trasmissione
                    for (methodName in TRANSMIT_METHODS) {
                        try {
                            transmitMethod = irService!!.javaClass.getMethod(
                                methodName,
                                Int::class.java,
                                IntArray::class.java
                            )
                            Log.i(TAG, "Found IR service: $className / method: $methodName")
                            return
                        } catch (e: NoSuchMethodException) {
                            // Prossimo metodo
                        }
                    }

                    // Se non trovato, cerca con firme alternative
                    for (method in irService!!.javaClass.methods) {
                        if (method.name in TRANSMIT_METHODS) {
                            val paramTypes = method.parameterTypes
                            if (paramTypes.size == 2 &&
                                (paramTypes[0] == Int::class.java || paramTypes[0] == Integer.TYPE) &&
                                (paramTypes[1] == IntArray::class.java || paramTypes[1] == IntArray::class.java)
                            ) {
                                transmitMethod = method
                                Log.i(TAG, "Found alternative method: ${method.name}")
                                return
                            }
                        }
                    }
                }
            } catch (e: ClassNotFoundException) {
                Log.d(TAG, "Class $className not found")
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing IR service", e)
            }
        }

        Log.w(TAG, "No Xiaomi IR service found. Trying fallback to ConsumerIrManager...")
    }

    override fun isSupported(): Boolean {
        return irService != null && transmitMethod != null
    }

    override fun transmit(frequency: Int, pattern: IntArray): Boolean {
        return try {
            if (!isSupported()) return false

            // Alcune implementazioni Xiaomi vogliono la frequenza in kHz
            val freqArg = if (frequency > 1000) frequency / 1000 else frequency

            transmitMethod?.invoke(irService, freqArg, pattern)
            Log.d(TAG, "Xiaomi IR sent: freq=$frequency, len=${pattern.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Xiaomi IR transmit failed", e)
            false
        }
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
        try {
            irService?.javaClass?.getMethod("cancelTransmit", Int::class.java)?.invoke(irService, 0)
            irService?.javaClass?.getMethod("stopTransmit")?.invoke(irService)
        } catch (e: Exception) {
            Log.d(TAG, "Cancel not supported", e)
        }
    }

    override fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "supported" to isSupported(),
            "service_class" to (irService?.javaClass?.name ?: "none"),
            "method" to (transmitMethod?.name ?: "none")
        )
    }
}
