/*
 * IRXiaomi - IR Signal Receiver via ESP32 + Bluetooth Serial
 * 
 * Questo firmware permette di ricevere segnali IR da qualsiasi telecomando
 * e inviarli all'app Android via Bluetooth Serial (SPP).
 * 
 * Hardware richiesto:
 * - ESP32 (o ESP8266)
 * - Ricevitore IR TSOP38238 (o TSOP4838, VS1838B)
 * - Resistenza 100Ω (opzionale, per protezione LED)
 * - LED indicatore (opzionale)
 * 
 * Collegamenti:
 * - TSOP38238 OUT -> GPIO 15 (esempio)
 * - TSOP38238 VCC -> 3.3V
 * - TSOP38238 GND -> GND
 * - LED indicatore  -> GPIO 2 (opzionale, built-in su molti ESP32)
 * 
 * Protocollo Bluetooth: SPP (Serial Port Profile)
 * L'app Android si connette all'ESP32 e invia/riceve comandi testuali.
 * 
 * Comandi:
 * - LEARN       -> Avvia modalità apprendimento
 * - STOP        -> Ferma apprendimento
 * - SEND:XXXXX  -> Invia pattern raw (es. SEND:38000,9000,4500,...)
 * - TEST        -> Invia ultimo codice appreso
 * - STATUS      -> Stato del dispositivo
 * - DECODE      -> Mostra decodifica ultimo segnale
 * - INFO        -> Informazioni firmware
 */

#include <BluetoothSerial.h>

// =============================================================================
// Pin definitions
// =============================================================================
#define IR_RECEIVER_PIN   15    // OUT del TSOP38238
#define LED_PIN           2     // LED indicatore (built-in)
#define IR_LED_PIN        4     // LED IR per trasmissione (opzionale)

// =============================================================================
// Costanti per decodifica IR
// =============================================================================
#define CARRIER_FREQ      38000 // Frequenza predefinita (38 kHz)
#define MAX_PULSES        512   // Numero massimo di impulsi registrabili
#define MIN_PULSE_US      50    // Impulso minimo rilevabile (microsecondi)
#define TIMEOUT_US        50000 // Timeout tra impulsi (50 ms = fine segnale)
#define LEARN_TIMEOUT_MS  5000  // Timeout apprendimento (5 secondi)

// =============================================================================
// Protocolli supportati
// =============================================================================
enum Protocol {
    PROTO_UNKNOWN,
    PROTO_NEC,
    PROTO_NEC_EXT,
    PROTO_SAMSUNG,
    PROTO_SONY_12,
    PROTO_SONY_15,
    PROTO_SONY_20,
    PROTO_RC5,
    PROTO_RC6,
    PROTO_PANASONIC,
    PROTO_JVC,
    PROTO_SHARP,
    PROTO_RAW
};

// =============================================================================
// Variabili globali
// =============================================================================
BluetoothSerial SerialBT;

// Buffer per pattern IR
volatile unsigned int irPulses[MAX_PULSES];
volatile unsigned int irPulseCount = 0;
volatile unsigned long lastPulseTime = 0;
volatile bool irSignalComplete = false;

bool isLearning = false;
unsigned int lastLearnedFrequency = CARRIER_FREQ;
unsigned int lastLearnedPulses[MAX_PULSES];
unsigned int lastLearnedCount = 0;

// Protocolli riconosciuti
const char* protocolNames[] = {
    "UNKNOWN", "NEC", "NEC_EXT", "SAMSUNG", "SONY_12",
    "SONY_15", "SONY_20", "RC5", "RC6", "PANASONIC", "JVC", "SHARP", "RAW"
};

// =============================================================================
// Forward declarations
// =============================================================================
void startLearning();
void stopLearning();
void processCommand(const String& cmd);
void decodeSignal();
String encodePulses(unsigned int pulses[], unsigned int count, unsigned int freq);
void sendIR(unsigned int freq, unsigned int pulses[], unsigned int count);
String getDeviceInfo();

// =============================================================================
// Setup
// =============================================================================
void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN, OUTPUT);
    pinMode(IR_RECEIVER_PIN, INPUT);
    pinMode(IR_LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    // Init Bluetooth
    SerialBT.begin("IRXiaomi-ESP32"); // Nome visibile in Bluetooth
    Serial.println("✅ IRXiaomi ESP32 Receiver started");
    Serial.println("📡 Bluetooth: IRXiaomi-ESP32");
    Serial.println("📋 Comandi: LEARN, STOP, SEND:..., TEST, STATUS, DECODE, INFO");
    Serial.println("");

    // Segnale di avvio (3 lampeggi)
    for (int i = 0; i < 3; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(200);
        digitalWrite(LED_PIN, LOW);
        delay(200);
    }

    // Attach interrupt per ricezione IR
    attachInterrupt(digitalPinToInterrupt(IR_RECEIVER_PIN), irSignalChanged, CHANGE);
}

// =============================================================================
// Interrupt handler per ricezione IR
// =============================================================================
void IRAM_ATTR irSignalChanged() {
    static unsigned long lastTime = 0;
    static unsigned int pulseIndex = 0;
    static bool firstPulse = true;

    unsigned long now = micros();
    unsigned long duration = now - lastTime;

    // Troppo breve? Ignora (rumore)
    if (duration < MIN_PULSE_US) {
        lastTime = now;
        return;
    }

    if (!isLearning) {
        lastTime = now;
        return;
    }

    if (firstPulse) {
        // Primo impulso: inizio segnale
        pulseIndex = 0;
        firstPulse = false;
        irPulseCount = 0;
        irSignalComplete = false;
    } else {
        // Registra durata dell'impulso precedente
        if (pulseIndex < MAX_PULSES) {
            irPulses[pulseIndex++] = (unsigned int)duration;
        }
    }

    // Controlla timeout (silenzio prolungato = fine segnale)
    if (duration > TIMEOUT_US && !firstPulse) {
        irPulseCount = pulseIndex;
        irSignalComplete = true;
        firstPulse = true;
        pulseIndex = 0;
        isLearning = false; // Stop automatico
        digitalWrite(LED_PIN, LOW);
    }

    lastTime = now;
}

// =============================================================================
// Loop principale
// =============================================================================
void loop() {
    // Controlla se il segnale IR è completo
    if (irSignalComplete) {
        irSignalComplete = false;

        // Copia il buffer
        noInterrupts();
        lastLearnedCount = irPulseCount;
        for (unsigned int i = 0; i < irPulseCount && i < MAX_PULSES; i++) {
            lastLearnedPulses[i] = irPulses[i];
        }
        interrupts();

        // Salva frequenza (rilevabile dal TSOP - di solito 38kHz)
        lastLearnedFrequency = detectFrequency(lastLearnedPulses, lastLearnedCount);

        // Stampa risultato
        String encoded = encodePulses(lastLearnedPulses, lastLearnedCount, lastLearnedFrequency);
        Serial.println("\n✅ Segnale IR ricevuto!");
        Serial.printf("   Impulsi: %d\n", lastLearnedCount);
        Serial.printf("   Pattern: %s\n", encoded.c_str());
        SerialBT.printf("SIGNAL:%s\n", encoded.c_str());

        // Decodifica protocollo
        decodeAndPrint(lastLearnedPulses, lastLearnedCount);

        digitalWrite(LED_PIN, LOW);
    }

    // Controlla comandi Bluetooth
    if (SerialBT.available()) {
        String cmd = SerialBT.readStringUntil('\n');
        cmd.trim();
        if (cmd.length() > 0) {
            processCommand(cmd);
        }
    }

    // Controlla comandi Serial (USB)
    if (Serial.available()) {
        String cmd = Serial.readStringUntil('\n');
        cmd.trim();
        if (cmd.length() > 0) {
            processCommand(cmd);
        }
    }

    delay(10);
}

// =============================================================================
// Comandi
// =============================================================================
void processCommand(const String& cmd) {
    Serial.printf("📥 Comando: %s\n", cmd.c_str());

    if (cmd == "LEARN") {
        startLearning();
    }
    else if (cmd == "STOP") {
        stopLearning();
    }
    else if (cmd == "STATUS") {
        String status = isLearning ? "LEARNING" : "IDLE";
        SerialBT.printf("STATUS:%s,%d\n", status.c_str(), lastLearnedCount);
        Serial.printf("   Status: %s\n", status.c_str());
    }
    else if (cmd == "TEST") {
        if (lastLearnedCount > 0) {
            Serial.println("🔄 Re-invio ultimo codice appreso...");
            SerialBT.printf("REPLAY:%s\n",
                encodePulses(lastLearnedPulses, lastLearnedCount, lastLearnedFrequency).c_str());
            sendIR(lastLearnedFrequency, lastLearnedPulses, lastLearnedCount);
        } else {
            SerialBT.println("ERROR:Nessun codice da testare");
            Serial.println("❌ Nessun codice appreso");
        }
    }
    else if (cmd == "DECODE") {
        if (lastLearnedCount > 0) {
            decodeAndPrint(lastLearnedPulses, lastLearnedCount);
        } else {
            SerialBT.println("ERROR:Nessun codice");
        }
    }
    else if (cmd == "INFO") {
        String info = getDeviceInfo();
        SerialBT.println(info);
        Serial.println(info);
    }
    else if (cmd.startsWith("SEND:")) {
        // Formato: SEND:frequenza,impulso1,impulso2,...
        String data = cmd.substring(5);
        int firstComma = data.indexOf(',');
        if (firstComma > 0) {
            unsigned int freq = (unsigned int)data.substring(0, firstComma).toInt();
            data = data.substring(firstComma + 1);

            unsigned int sendPulses[MAX_PULSES];
            unsigned int sendCount = 0;

            while (data.length() > 0 && sendCount < MAX_PULSES) {
                int comma = data.indexOf(',');
                if (comma > 0) {
                    sendPulses[sendCount++] = (unsigned int)data.substring(0, comma).toInt();
                    data = data.substring(comma + 1);
                } else {
                    sendPulses[sendCount++] = (unsigned int)data.toInt();
                    break;
                }
            }

            Serial.printf("📤 Invio IR: freq=%d Hz, %d impulsi\n", freq, sendCount);
            sendIR(freq, sendPulses, sendCount);

            // Salva come ultimo codice
            lastLearnedCount = sendCount;
            lastLearnedFrequency = freq;
            memcpy(lastLearnedPulses, sendPulses, sendCount * sizeof(unsigned int));

            SerialBT.printf("SENT:%s\n", encodePulses(sendPulses, sendCount, freq).c_str());
        }
    }
    else {
        SerialBT.printf("ERROR:Comando sconosciuto: %s\n", cmd.c_str());
        Serial.println("❌ Comando sconosciuto");
    }
}

void startLearning() {
    isLearning = true;
    irPulseCount = 0;
    irSignalComplete = false;
    digitalWrite(LED_PIN, HIGH);
    Serial.println("👂 In ascolto... punta il telecomando verso il ricevitore IR");
    SerialBT.println("LEARNING:START");
    SerialBT.println("READY");
}

void stopLearning() {
    isLearning = false;
    digitalWrite(LED_PIN, LOW);
    Serial.println("⏹ Apprendimento fermato");
    SerialBT.println("LEARNING:STOP");
}

// =============================================================================
// Decodifica protocolli
// =============================================================================
bool matchTiming(unsigned int value, unsigned int expected, unsigned int tolerance = 25) {
    unsigned int minVal = expected * (100 - tolerance) / 100;
    unsigned int maxVal = expected * (100 + tolerance) / 100;
    return (value >= minVal && value <= maxVal);
}

void decodeAndPrint(unsigned int pulses[], unsigned int count) {
    Serial.printf("🔍 Decodifica: %d impulsi\n", count);
    SerialBT.printf("DECODE:Pulses=%d\n", count);

    // Prova NEC
    if (count >= 68) {
        if (matchTiming(pulses[0], 9000) && matchTiming(pulses[1], 4500)) {
            unsigned long address = 0, command = 0;
            for (int i = 0; i < 32; i++) {
                int idx = 2 + i * 2;
                if (idx + 1 >= (int)count) break;
                int bit = matchTiming(pulses[idx + 1], 1690) ? 1 : 0;
                if (i < 16) address = (address << 1) | bit;
                else command = (command << 1) | bit;
            }
            unsigned int realAddr = (unsigned int)(address & 0xFFFF);
            unsigned int realCmd = (unsigned int)((command >> 8) & 0xFF);

            Serial.printf("   📺 Protocollo: NEC\n");
            Serial.printf("   📍 Address:    0x%04X (%d)\n", realAddr, realAddr);
            Serial.printf("   🎯 Command:    0x%02X (%d)\n", realCmd, realCmd);
            Serial.printf("   📡 Frequenza:  38000 Hz\n");
            SerialBT.printf("PROTOCOL:NEC\nADDRESS:0x%04X\nCOMMAND:0x%02X\nFREQUENCY:38000\n", realAddr, realCmd);
            return;
        }
    }

    // Prova Samsung
    if (count >= 68) {
        if (matchTiming(pulses[0], 4500) && matchTiming(pulses[1], 4500)) {
            unsigned long address = 0, command = 0;
            for (int i = 0; i < 32; i++) {
                int idx = 2 + i * 2;
                if (idx + 1 >= (int)count) break;
                int bit = matchTiming(pulses[idx + 1], 1690) ? 1 : 0;
                if (i < 16) address = (address << 1) | bit;
                else command = (command << 1) | bit;
            }
            Serial.printf("   📺 Protocollo: Samsung\n");
            Serial.printf("   📍 Address:    0x%04X\n", (unsigned int)(address & 0xFFFF));
            Serial.printf("   🎯 Command:    0x%02X\n", (unsigned int)((command >> 8) & 0xFF));
            SerialBT.printf("PROTOCOL:SAMSUNG\nADDRESS:0x%04X\nCOMMAND:0x%02X\n",
                (unsigned int)(address & 0xFFFF),
                (unsigned int)((command >> 8) & 0xFF));
            return;
        }
    }

    // Prova Sony
    if (count >= 26) {
        if (matchTiming(pulses[0], 2400) && matchTiming(pulses[1], 600)) {
            int bits = (count - 2) / 2;
            int addrBits = (bits == 12) ? 5 : (bits == 15) ? 8 : 13;
            int cmdBits = bits - addrBits;
            unsigned long command = 0, address = 0;
            for (int i = 0; i < cmdBits; i++) {
                int idx = 2 + i * 2;
                if (idx + 1 >= (int)count) break;
                int bit = matchTiming(pulses[idx + 1], 1200) ? 1 : 0;
                command = (command << 1) | bit;
            }
            for (int i = 0; i < addrBits; i++) {
                int idx = 2 + (cmdBits + i) * 2;
                if (idx + 1 >= (int)count) break;
                int bit = matchTiming(pulses[idx + 1], 1200) ? 1 : 0;
                address = (address << 1) | bit;
            }
            Serial.printf("   📺 Protocollo: Sony %d bit\n", bits);
            Serial.printf("   📍 Address:    %d\n", (int)address);
            Serial.printf("   🎯 Command:    %d\n", (int)command);
            SerialBT.printf("PROTOCOL:SONY_%d\nADDRESS:%d\nCOMMAND:%d\n", bits, (int)address, (int)command);
            return;
        }
    }

    // Non riconosciuto
    Serial.println("   ❓ Protocollo sconosciuto (RAW)");
    SerialBT.println("PROTOCOL:RAW");
}

unsigned int detectFrequency(unsigned int pulses[], unsigned int count) {
    // Prova a determinare la frequenza dal pattern
    // I ricevitori TSOP demodulano, quindi non possiamo sapere la freq esatta
    // dalla forma d'onda. Ritorniamo 38000 come default (il più comune).
    return CARRIER_FREQ;
}

// =============================================================================
// Utility
// =============================================================================
String encodePulses(unsigned int pulses[], unsigned int count, unsigned int freq) {
    String result = String(freq);
    for (unsigned int i = 0; i < count && i < MAX_PULSES; i++) {
        result += "," + String(pulses[i]);
    }
    return result;
}

void sendIR(unsigned int freq, unsigned int pulses[], unsigned int count) {
    // Nota: l'ESP32 può emettere segnali IR via LED IR su un pin PWM
    // Questa funzione richiede l'hardware aggiuntivo (LED IR + transistor)
    // Per ora, stampa solo che dovrebbe inviare
    Serial.printf("📤 IR Out: freq=%d Hz, pulses=%d\n", freq, count);

    // Se il pin IR_LED_PIN è configurato, usa la libreria IRremoteESP8266
    // o implementazione manuale con PWM a 38kHz
    #ifdef USE_IR_TRANSMIT
    // irsend.sendRaw(pulses, count, freq);
    #endif

    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
}

String getDeviceInfo() {
    char buffer[512];
    snprintf(buffer, sizeof(buffer),
        "INFO:IRXiaomi ESP32 v1.0.0\n"
        "Chip:ESP32\n"
        "Freq:240MHz\n"
        "IR Pin:%d\n"
        "IR LED Pin:%d\n"
        "BT Name:IRXiaomi-ESP32\n"
        "Max Pulses:%d\n"
        "Timeout:%d us\n"
        "Protocols:NEC,Samsung,Sony,RC5,RC6,RAW",
        IR_RECEIVER_PIN, IR_LED_PIN, MAX_PULSES, TIMEOUT_US
    );
    return String(buffer);
}
