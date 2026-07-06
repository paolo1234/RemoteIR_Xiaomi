package com.irxiaomi.learning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

/**
 * Registra segnali IR attraverso il jack audio (microfono) con un fototransistor.
 *
 * Circuito richiesto:
 * - Fototransistor IR (es. TSOP38238) collegato al microfono del jack audio
 * - Resistenza da ~10kΩ in serie
 *
 * Il segnale IR modulato (tipicamente 38kHz) viene demodulato dal TSOP,
 * che produce un segnale digitale (HIGH = carrier presente, LOW = assente).
 * L'audio campiona questo segnale per ricostruire il pattern.
 */
class AudioIrLearner(private val context: Context) {

    companion object {
        private const val TAG = "AudioIrLearner"

        /** Frequenza di campionamento: 192kHz per catturare burst IR (min 50µs) */
        private const val SAMPLE_RATE = 192000

        /** Canale: mono */
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

        /** Formato: 16-bit PCM */
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** Buffer: 0.5 secondi di campioni */
        private const val BUFFER_SIZE = SAMPLE_RATE / 2

        /** Soglia per rilevare segnale (il TSOP produce ~0.9V per HIGH, ~0V per LOW) */
        private const val THRESHOLD = 500  // Valore tipico 16-bit

        /** Durata massima registrazione (secondi) */
        private const val MAX_RECORD_SECONDS = 10

        /** Tempo minimo di silenzio per considerare fine segnale (ms) */
        private const val SILENCE_TIMEOUT_MS = 100

        /** Frequenza del timer di campionamento (Hz) */
        private val TIMER_HZ = SAMPLE_RATE / 10
    }

    data class LearningState(
        val isRecording: Boolean = false,
        val signalDetected: Boolean = false,
        val progress: Float = 0f,           // 0..1
        val message: String = "Pronto per apprendere",
        val rawSignal: List<Int>? = null,
        val decodedDevices: List<LearnedSignal> = emptyList()
    )

    data class LearnedSignal(
        val rawPattern: IntArray,
        val frequency: Int = 38000,  // Default 38kHz, da confermare
        val protocol: String = "UNKNOWN",
        val address: Long? = null,
        val command: Long? = null,
        val name: String = "",
        val quality: Float = 0f      // 0..1
    )

    private val _state = MutableStateFlow(LearningState())
    val state: Flow<LearningState> = _state

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Verifica permesso microfono */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Inizia la cattura del segnale IR */
    fun startLearning() {
        if (!hasPermission()) {
            _state.value = _state.value.copy(
                message = "Permesso microfono non concesso"
            )
            return
        }

        if (_state.value.isRecording) return

        recordingJob = scope.launch {
            try {
                val bufferSize = maxOf(
                    AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
                    BUFFER_SIZE
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    _state.value = _state.value.copy(
                        message = "Impossibile inizializzare AudioRecord"
                    )
                    return@launch
                }

                audioRecord?.startRecording()
                _state.value = _state.value.copy(
                    isRecording = true,
                    message = "In ascolto... Punta il telecomando al microfono e premi un tasto",
                    rawSignal = null,
                    decodedDevices = emptyList()
                )

                val signal = captureSignal(audioRecord!!)
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                if (signal.isEmpty()) {
                    _state.value = _state.value.copy(
                        isRecording = false,
                        message = "Nessun segnale rilevato. Riprova."
                    )
                    return@launch
                }

                // Decodifica il segnale
                val decoded = decodeSignal(signal.toIntArray())

                _state.value = _state.value.copy(
                    isRecording = false,
                    signalDetected = true,
                    rawSignal = signal,
                    decodedDevices = listOf(decoded),
                    message = if (decoded.protocol != "UNKNOWN")
                        "Rilevato: ${decoded.protocol} (addr=${decoded.address}, cmd=${decoded.command})"
                    else
                        "Segnale grezzo rilevato (${signal.size} burst)"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Learning error", e)
                _state.value = _state.value.copy(
                    isRecording = false,
                    message = "Errore: ${e.message}"
                )
            }
        }
    }

    /** Interrompe la cattura */
    fun stopLearning() {
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _state.value = _state.value.copy(
            isRecording = false,
            message = "Registrazione interrotta"
        )
    }

    /** Cattura il segnale, rilevando burst e silence */
    private suspend fun captureSignal(recorder: AudioRecord): List<Int> {
        return withContext(Dispatchers.IO) {
            val buffer = ShortArray(BUFFER_SIZE)
            val signal = mutableListOf<Int>()
            var isInSignal = false
            var silenceStart = 0L
            var totalSamples = 0
            val startTime = System.currentTimeMillis()

            // Filtro passa-basso per pulire il rumore
            var lastValue = 0

            while (System.currentTimeMillis() - startTime < MAX_RECORD_SECONDS * 1000L) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                if (bytesRead <= 0) continue

                totalSamples += bytesRead

                for (i in 0 until bytesRead) {
                    val value = abs(buffer[i].toInt())

                    if (value > THRESHOLD && !isInSignal) {
                        // Inizio segnale
                        isInSignal = true
                        silenceStart = 0L
                        _state.value = _state.value.copy(
                            signalDetected = true,
                            message = "Segnale rilevato! Attendere..."
                        )
                    }

                    if (isInSignal) {
                        // Campiona a frequenza ridotta per ottenere i burst
                        if (i % (SAMPLE_RATE / TIMER_HZ) == 0) {
                            signal.add(if (value > THRESHOLD) 1 else 0)
                        }

                        if (value <= THRESHOLD) {
                            if (silenceStart == 0L) silenceStart = System.currentTimeMillis()
                            else if (System.currentTimeMillis() - silenceStart > SILENCE_TIMEOUT_MS) {
                                // Silenzio prolungato -> fine segnale
                                break
                            }
                        } else {
                            silenceStart = 0L
                        }
                    }

                    lastValue = value
                }

                if (isInSignal && silenceStart > 0 && 
                    System.currentTimeMillis() - silenceStart > SILENCE_TIMEOUT_MS) {
                    break
                }

                // Aggiorna progress
                val elapsed = System.currentTimeMillis() - startTime
                _state.value = _state.value.copy(
                    progress = (elapsed / (MAX_RECORD_SECONDS * 1000f)).coerceAtMost(1f)
                )
            }

            // Converti la sequenza di bit in timing (microsecondi)
            convertBitsToTiming(signal)
        }
    }

    /** Converte una sequenza di bit (1/0) in timing on/off in microsecondi */
    private fun convertBitsToTiming(bits: List<Int>): List<Int> {
        if (bits.isEmpty()) return emptyList()

        val timing = mutableListOf<Int>()
        var current = bits[0]
        var count = 1
        val usPerSample = 1_000_000 / TIMER_HZ

        for (i in 1 until bits.size) {
            if (bits[i] == current) {
                count++
            } else {
                timing.add(count * usPerSample)
                current = bits[i]
                count = 1
            }
        }
        timing.add(count * usPerSample)

        return timing
    }

    /** Decodifica il segnale grezzo in un protocollo */
    private fun decodeSignal(timing: IntArray): LearnedSignal {
        val decoder = ProtocolDecoder()

        // Prova NEC
        decoder.decodeNec(timing)?.let { (addr, cmd) ->
            return LearnedSignal(
                rawPattern = timing,
                protocol = "NEC",
                address = addr,
                command = cmd,
                quality = 0.95f,
                name = "Codice NEC (${addr.toInt().toHex()}:${cmd.toInt().toHex()})"
            )
        }

        // Prova Samsung
        decoder.decodeSamsung(timing)?.let { (addr, cmd) ->
            return LearnedSignal(
                rawPattern = timing,
                protocol = "SAMSUNG",
                address = addr,
                command = cmd,
                quality = 0.9f,
                name = "Codice Samsung (${addr.toInt().toHex()}:${cmd.toInt().toHex()})"
            )
        }

        // Prova Sony
        decoder.decodeSony(timing)?.let { (addr, cmd) ->
            return LearnedSignal(
                rawPattern = timing,
                protocol = "SONY",
                address = addr,
                command = cmd,
                quality = 0.85f,
                name = "Codice Sony (${addr}:${cmd})"
            )
        }

        // Prova RC5
        decoder.decodeRc5(timing)?.let { (addr, cmd) ->
            return LearnedSignal(
                rawPattern = timing,
                protocol = "RC5",
                address = addr,
                command = cmd,
                quality = 0.85f,
                name = "Codice RC5 (${addr}:${cmd})"
            )
        }

        // Non riconosciuto: restituisci raw
        val totalUs = timing.sum()
        return LearnedSignal(
            rawPattern = timing,
            protocol = "RAW",
            quality = 0.3f,
            name = "Raw (${timing.size} burst, ${totalUs / 1000}ms)"
        )
    }

    private fun Int.toHex(): String = String.format("%04X", this)

    /** Resetta lo stato */
    fun reset() {
        _state.value = LearningState()
    }

    /** Rilascia risorse */
    fun destroy() {
        stopLearning()
        scope.cancel()
    }
}
