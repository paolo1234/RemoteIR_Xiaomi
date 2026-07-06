# IRXiaomi - Hardware per Learning IR

Due soluzioni hardware per "imparare" (leggere) i codici IR dei telecomandi esistenti
e aggiungerli al database dell'app.

---

## Soluzione A: Jack Audio (Economica, < 2€)

Collega un fototransistor IR direttamente al jack audio del telefono.
L'app campiona il segnale a 192kHz e decodifica il protocollo.

### Componenti

| Q.tà | Componente | Costo | Note |
|------|-----------|-------|------|
| 1 | TSOP38238 (o VS1838B, TL1838) | 0.50€ | Ricevitore IR 38kHz |
| 1 | Resistenza 10kΩ (R1) | 0.10€ | Pull-up tra OUT e VCC |
| 1 | Resistenza 100Ω (R2) | 0.10€ | Protezione |
| 1 | Jack TRRS 3.5mm | 0.50€ | Stereo + microfono |
| - | Breadboard + cavetti | 1.00€ | Per prototipo |
| | **Totale** | **~2.20€** | |

### Schema Collegamento

```
┌─────────────────────────────────────────────┐
│                TSOP38238                     │
│                                              │
│   ┌────┐   ┌──────┐   ┌────┐                │
│   │OUT │   │ GND  │   │VCC │                │
│   └─┬──┘   └──┬───┘   └──┬─┘                │
│     │         │          │                   │
│     │    ┌────┤     ┌────┤                   │
│     │    │    │     │    │                   │
│    ╱     │    │     │    ╲  R2 (100Ω)        │
│   ╱ R1   │    │     │    ╲                   │
│   ╲ 10kΩ │    │     │    ╱                   │
│    ╲     │    │     │    │                   │
│     │    │    │     │    │                   │
│     ├────┘    │     └────┤                   │
│     │         │          │                   │
│    ┌┴─────────┴──────────┴┐                  │
│    │    JACK TRRS 3.5mm   │                  │
│    │                      │                  │
│    │ TIP │ RING1 │ RING2 │ SLEEVE            │
│    │  L  │   R   │  MIC  │  GND              │
│    └─────┴───────┴───────┴─────┘             │
│          │           │        │              │
│          │           │        │              │
│       NON CONNESSO   ├────────┤              │
│                      │  OUT   │  GND         │
│                      │  TSOP  │  TSOP        │
└─────────────────────────────────────────────┘
```

**Collegamenti dettagliati:**
1. `TSOP OUT` → `R1 (10kΩ)` → `Jack MIC (Ring2)` — segnale audio
2. `TSOP GND` → `Jack GND (Sleeve)` — massa comune
3. `TSOP VCC` → `R2 (100Ω)` → `Jack L (Tip)` — alimentazione 3.3V
   (Il jack L fornisce ~0.9V, sufficiente per il TSOP!)

### Come funziona

Il TSOP38238 è un **ricevitore IR demodulato**: quando riceve un segnale IR modulato
a 38kHz, la sua uscita va a LOW (0V). In assenza di segnale, è HIGH (pull-up a VCC).

Collegando OUT al microfono del jack audio, l'app campiona il segnale:
- HIGH = silenzio (nessun segnale IR)
- LOW = carrier presente (segnale IR attivo)

L'app campiona a 192kHz e ricostruisce i timing on/off del protocollo.

### Montaggio

1. Salda i componenti su una breadboard o basetta millefori
2. Collega il jack TRRS con cavetti volanti
3. Inserisci il jack nello smartphone
4. Apri IRXiaomi → Apprendimento → "Impara"
5. Punta il telecomando verso il TSOP e premi un tasto

---

## Soluzione B: ESP32 (Wireless, ~15€)

Usa un ESP32 con ricevitore IR e Bluetooth Serial.
L'app si connette via Bluetooth all'ESP32 per ricevere codici.
Vantaggio: maggiore portabilità e possibilità di testare i codici
inviandoli direttamente dall'ESP32.

### Componenti aggiuntivi

| Q.tà | Componente | Costo |
|------|-----------|-------|
| 1 | ESP32 Dev Board | 10€ |
| 1 | LED IR (5mm, 940nm) | 0.50€ |
| 1 | Transistor 2N2222 (NPN) | 0.50€ |
| 1 | Resistenza 1kΩ | 0.10€ |
| 1 | Resistenza 22Ω | 0.10€ |

### Firmware

Carica il file `ir_receiver_esp32.ino` sull'ESP32 con Arduino IDE o PlatformIO.

**Comandi via Bluetooth Serial:**
| Comando | Descrizione |
|---------|-------------|
| `LEARN` | Avvia apprendimento (punta il telecomando) |
| `STOP` | Ferma apprendimento |
| `STATUS` | Mostra stato (LEARNING/IDLE) |
| `SEND:freq,p1,p2,...` | Invia un pattern IR |
| `TEST` | Re-invia l'ultimo codice appreso |
| `DECODE` | Decodifica l'ultimo segnale |
| `INFO` | Info firmware |

### Protocollo Bluetooth

L'ESP32 si presenta come `IRXiaomi-ESP32`. L'app Android si connette
e scambia comandi testuali via Serial Bluetooth.

Esempio di risposta dopo un apprendimento:
```
SIGNAL:38000,9000,4500,560,560,560,1690,...
PROTOCOL:NEC
ADDRESS:0xE0E0
COMMAND:0x40BF
```

---

## Debug e Test

### Segnale assente?
- Verifica che il TSOP sia alimentato (3.3-5V)
- Il LED sul TSOP dovrebbe accendersi con luce IR (usa fotocamera smartphone per vedere se il led IR del telecomando funziona)
- Prova a collegare OUT direttamente a 3.3V (dovrebbe dare HIGH stabile)

### Rumore / Falsi positivi?
- Aggiungi un condensatore da 10µF tra VCC e GND del TSOP
- Assicurati che i cavi siano corti e schermati
- Usa una resistenza di pull-down da 100kΩ su OUT (se il segnale è ballerino)

### App non rileva segnale?
- Aumenta il volume del microfono al massimo
- Verifica che il jack sia TRRS (4 contatti), non TRS (3 contatti)
- Alcuni smartphone hanno lo switch automatico jack/microfono: inserisci e ruota delicatamente il jack

---

## Riferimenti

- [TSOP38238 Datasheet](https://www.vishay.com/docs/82491/tsop382.pdf)
- [LIRC - Linux Infrared Remote Control](http://lirc.sourceforge.net/)
- [IRDB - Global Cache](https://irdb.globalcache.com/)
- [Protocol timings (SB-Projects)](https://www.sbprojects.net/knowledge/ir/)
