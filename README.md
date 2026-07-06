# IRXiaomi — Telecomando Universale IR

App Android per sfruttare il sensore IR del telefono al massimo.
Database enorme di codici, learning via jack audio, clonazione.

## 📲 Come ottenere l'APK (senza installare Android Studio)

### Metodo 1: GitHub Actions (consigliato, gratis)

1. **Crea un repository su GitHub**:
   ```
   https://github.com/new  →  Nome: IRXiaomi
   ```

2. **Carica il progetto** (da terminale):
   ```bash
   cd C:/Users/Paolo/IRXiaomi
   git init
   git add .
   git commit -m "Primo commit"
   git remote add origin https://github.com/TUO_USER/IRXiaomi.git
   git push -u origin main
   ```

3. **Vai su GitHub**:
   - Clicca **Actions** (in alto)
   - Clicca sul workflow **"Build APK"**
   - Aspetta ~5 minuti che finisca
   - Scarica l'APK da **Artifacts** in fondo alla pagina

4. **Installa sul telefono**:
   - Trasferisci l'APK sul telefono (email, Google Drive, cavo USB)
   - Apri il file → **Installa** (abilita "Origini sconosciute")

### Metodo 2: Android Studio (se lo installi)

```bash
File → Open → C:/Users/Paolo/IRXiaomi
Build → Build Bundle(s) / APK(s) → Build APK
```

## 🚀 Primo avvio

1. Apri l'app → **Settings** → **Genera database seed (5000+ codici)**
2. Torna alla Home → **Telecomando** → scegli marca (Samsung/LG/Sony/...)
3. Premi **Power** → il telefono invia il codice IR

## 📂 Struttura del progetto

```
IRXiaomi/
├── app/                    # App Android
│   ├── src/main/java/      # 34 file Kotlin
│   │   ├── ir/             # 3 implementazioni IR (ConsumerIr, Xiaomi, Sysfs)
│   │   ├── db/             # Room DB + DAO + DatabaseSeed (5000+ codici)
│   │   ├── learning/       # Audio learning + ProtocolDecoder + RawAnalyzer
│   │   ├── sync/           # LIRC importer + IRDB + Remote sync
│   │   ├── clone/          # Variant generator + Code clone manager
│   │   ├── model/          # Brand (100+), DeviceType, Protocol
│   │   └── ui/             # 6 schermate Compose + tema
│   └── src/main/assets/    # File LIRC di esempio
├── sync-server/            # Server Python (FastAPI + PostgreSQL)
├── hardware/               # Circuito + firmware ESP32
├── scripts/                # Script download LIRC completo
└── .github/workflows/      # Build automatico APK
```

## 🔧 Hardware opzionale

Per **imparare** codici da telecomandi esistenti:
- **Circuito jack audio**: TSOP38238 + resistenza 10kΩ + jack TRRS (~2€)
- **ESP32**: ricevitore IR + Bluetooth Serial (~15€)

Vedi `hardware/README.md` per dettagli.
