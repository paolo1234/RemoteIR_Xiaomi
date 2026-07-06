#!/usr/bin/env python3
"""
IRXiaomi — Broadlink RM Mini3 / RM4 Cloner
============================================
Clona (impara) codici IR da qualsiasi telecomando usando un Broadlink RM Mini3/RM4.
Salva i codici in formato JSON importabile dall'app IRXiaomi.

Requisiti:
  pip install broadlink pycryptodome

Uso:
  python scripts/broadlink_cloner.py

Modalità:
  1. Discovery — trova Broadlink sulla rete WiFi
  2. Learn — impara un codice IR (punta il telecomando e premi un tasto)
  3. Test — reinvia l'ultimo codice appreso
  4. Decode — analizza l'ultimo codice (tenta di capire protocollo)
  5. Export — salva TUTTI i codici appresi in JSON per IRXiaomi
  6. Interactive — modalità guidata passo-passo
"""

import sys
import json
import time
import struct
import base64
import binascii
from datetime import datetime
from typing import Optional, List, Dict, Any

# ──────────────────────────────────────────────────────────────────────
# Tentativo import broadlink
# ──────────────────────────────────────────────────────────────────────
try:
    import broadlink
except ImportError:
    print("❌ Libreria 'broadlink' non trovata. Installa:")
    print("   pip install broadlink pycryptodome")
    sys.exit(1)

# ──────────────────────────────────────────────────────────────────────
# Costanti
# ──────────────────────────────────────────────────────────────────────
VERSION = "1.0.0"
EXPORT_FILE = "broadlink_codes_export.json"
MAX_CODES = 500

# Protocolli supportati per decodifica
KNOWN_PROTOCOLS = {
    "NEC": {"freq": 38000, "leader_on": 9000, "leader_off": 4500, "bit_on": 560, "bit_one_off": 1690, "bit_zero_off": 560},
    "SAMSUNG": {"freq": 38000, "leader_on": 4500, "leader_off": 4500, "bit_on": 560, "bit_one_off": 1690, "bit_zero_off": 560},
    "SONY_12": {"freq": 40000, "leader_on": 2400, "leader_off": 600, "bit_on": 600, "bit_one_off": 1200, "bit_zero_off": 600},
    "SONY_15": {"freq": 40000, "leader_on": 2400, "leader_off": 600, "bit_on": 600, "bit_one_off": 1200, "bit_zero_off": 600},
    "SONY_20": {"freq": 40000, "leader_on": 2400, "leader_off": 600, "bit_on": 600, "bit_one_off": 1200, "bit_zero_off": 600},
    "RC5": {"freq": 36000, "half_bit": 889, "full_bit": 1778},
    "RC6": {"freq": 36000, "half_bit": 444, "full_bit": 888},
    "PANASONIC": {"freq": 37000, "leader_on": 3500, "leader_off": 1750, "bit_on": 438, "bit_one_off": 1312, "bit_zero_off": 438},
}


# ──────────────────────────────────────────────────────────────────────
# Classe principale
# ──────────────────────────────────────────────────────────────────────
class BroadlinkCloner:
    """Gestisce discovery, apprendimento e export codici IR via Broadlink."""

    def __init__(self):
        self.device: Optional[broadlink.Device] = None
        self.learned_codes: List[Dict[str, Any]] = []
        self.last_raw: Optional[bytes] = None
        self.last_hex: str = ""

    # ── Discovery ────────────────────────────────────────────────────
    def discover(self, timeout: int = 10) -> bool:
        """Cerca dispositivi Broadlink sulla rete WiFi locale."""
        print(f"\n🔍 Scansione rete WiFi in corso ({timeout}s)...")
        print("   Assicurati che il Broadlink sia acceso e sulla stessa rete.")
        
        devices = broadlink.discover(timeout=timeout)
        
        if not devices:
            print("❌ Nessun Broadlink trovato.")
            print("   Verifica:")
            print("   - Il Broadlink è alimentato?")
            print("   - Sei sulla stessa rete WiFi?")
            print("   - L'app Broadlink ufficiale lo vede?")
            return False

        print(f"\n✅ Trovati {len(devices)} dispositivo/i:")
        for i, dev in enumerate(devices):
            dev_type = self._get_device_type(dev.devtype)
            print(f"\n   [{i}] {dev_type}")
            print(f"       Nome:     {dev.name}")
            print(f"       IP:       {dev.host[0]}")
            print(f"       MAC:      {binascii.hexlify(dev.mac).decode().upper()}")
            print(f"       Tipo:     0x{dev.devtype:04X}")
            print(f"       Firmware: {dev.fwver}")

        # Seleziona dispositivo
        if len(devices) == 1:
            idx = 0
        else:
            while True:
                try:
                    idx = int(input("\n   Seleziona dispositivo [0]: ") or "0")
                    if 0 <= idx < len(devices):
                        break
                except ValueError:
                    pass

        self.device = devices[idx]
        self.device.auth()
        print(f"\n✅ Autenticato: {self._get_device_type(self.device.devtype)} ({self.device.host[0]})")
        return True

    def _get_device_type(self, devtype: int) -> str:
        types = {
            0x2711: "RM Mini 3",
            0x2712: "RM Mini 3",
            0x2737: "RM Mini 3",
            0x273E: "RM4 Mini",
            0x2797: "RM4 Pro",
            0x279D: "RM4 Mini",
            0x2783: "RM4C Mini",
            0x277C: "RM4C Pro",
            0x27A1: "RM4S",
            0x27A6: "RM4 Mini",
            0x27A9: "RM4C Mini",
            0x27B8: "RM4 Pro",
            0x27C2: "RM4S",
            0x27C3: "RM4 Mini",
            0x27C7: "RM4C Mini",
            0x27D2: "RM4 Pro",
            0x27D3: "RM4C Pro",
            0x27D4: "RM4S",
            0x27D5: "RM4 Mini",
            0x27D6: "RM4C Mini",
            0x27D7: "RM4 Pro",
        }
        return types.get(devtype, f"Broadlink sconosciuto (0x{devtype:04X})")

    # ── Apprendimento ────────────────────────────────────────────────
    def learn(self, code_name: str = "", timeout: int = 15) -> bool:
        """
        Impara un codice IR dal telecomando.
        1. Il Broadlink entra in modalità apprendimento
        2. PUNTA IL TELECOMANDO VERSO IL BROADLINK e premi un tasto
        3. Il codice viene catturato
        """
        if not self.device:
            print("❌ Nessun dispositivo. Esegui 'discover' prima.")
            return False

        if not code_name:
            code_name = f"Code_{len(self.learned_codes) + 1}"

        print(f"\n🎯 Apprendimento: '{code_name}'")
        print("   Il Broadlink è in ascolto...")
        print("   ▶ PUNTA IL TELECOMANDO VERSO IL BROADLINK e premi un tasto")
        print("   (Premi Ctrl+C per annullare)")
        
        self.device.enter_learning()
        
        start = time.time()
        raw = None
        while time.time() - start < timeout:
            time.sleep(0.5)
            try:
                raw = self.device.check_data()
                if raw:
                    break
            except Exception:
                pass
            # Feedback
            elapsed = int(time.time() - start)
            if elapsed % 2 == 0:
                sys.stdout.write(f"\r   ⏳ In ascolto... ({elapsed}s/{timeout}s)")
                sys.stdout.flush()

        if not raw:
            print("\n❌ Nessun codice ricevuto. Riprova.")
            return False

        self.last_raw = raw
        self.last_hex = binascii.hexlify(raw).decode()

        # Decodifica
        parsed = self._parse_broadlink_code(raw)
        
        code_entry = {
            "name": code_name,
            "display_name": code_name,
            "brand": "Broadlink",
            "device_type": "OTHER",
            "protocol": parsed["protocol"],
            "frequency": parsed["frequency"],
            "pattern": parsed["pattern"],
            "raw_hex": self.last_hex,
            "raw_b64": base64.b64encode(raw).decode(),
            "source": "broadlink_learned",
            "learned_at": datetime.now().isoformat(),
            "is_verified": False,
        }

        self.learned_codes.append(code_entry)

        print(f"\n✅ Codice appreso! ({len(self.last_hex)//2} bytes)")
        if parsed["protocol"] != "RAW":
            print(f"   Protocollo: {parsed['protocol']}")
            if parsed["address"] is not None:
                print(f"   Address:    0x{parsed['address']:04X}")
            if parsed["command"] is not None:
                print(f"   Command:    0x{parsed['command']:04X}")
        print(f"   Pattern:    {parsed['pattern'][:60]}...")
        print(f"   Frequenza:  {parsed['frequency']} Hz")

        return True

    def _parse_broadlink_code(self, raw: bytes) -> Dict[str, Any]:
        """
        Decodifica il formato Broadlink:
        - 4 bytes: frequenza (kHz)
        - 1 byte: 0x26 (tipo)
        - 2 bytes: numero impulsi (little-endian)
        - ...: pattern (ogni impulso = 2 bytes, little-endian, in microsecondi * 2)
        """
        try:
            if len(raw) < 7:
                return {"protocol": "RAW", "frequency": 38000, "pattern": "", 
                        "address": None, "command": None}

            freq_khz = struct.unpack_from('<I', raw, 0)[0]
            freq_hz = freq_khz * 1000 if freq_khz < 1000 else freq_khz

            pulse_type = raw[4]
            pulse_count = struct.unpack_from('<H', raw, 5)[0]

            pattern = []
            for i in range(pulse_count):
                idx = 7 + i * 2
                if idx + 1 >= len(raw):
                    break
                val = struct.unpack_from('<H', raw, idx)[0]
                pattern.append(val)

            # Prova decodifica protocolli
            protocol, address, command = self._decode_protocol(pattern)

            # Se non riconosciuto, prova NEC
            if protocol == "RAW":
                # Broadlink a volte restituisce pattern con valori divisi per 2
                pattern2 = [p * 2 for p in pattern]
                protocol, address, command = self._decode_protocol(pattern2)
                if protocol != "RAW":
                    pattern = pattern2

            pattern_str = ",".join(map(str, pattern))

            return {
                "protocol": protocol,
                "frequency": freq_hz,
                "pattern": pattern_str,
                "address": address,
                "command": command,
                "pulse_count": pulse_count,
                "raw_type": pulse_type,
            }

        except Exception as e:
            return {"protocol": "RAW", "frequency": 38000, "pattern": "",
                    "address": None, "command": None, "error": str(e)}

    def _decode_protocol(self, pattern: List[int]) -> tuple:
        """Tenta decodifica protocollo dal pattern raw."""
        if len(pattern) < 4:
            return "RAW", None, None

        # NEC
        if len(pattern) >= 68:
            p = pattern
            tol = 0.25
            # Leader NEC: 9000 on, 4500 off
            if (abs(p[0] - 9000) < 9000 * tol and abs(p[1] - 4500) < 4500 * tol):
                try:
                    addr = 0
                    cmd = 0
                    for i in range(32):
                        idx = 2 + i * 2
                        if idx + 1 >= len(p):
                            break
                        if abs(p[idx] - 560) > 560 * tol:
                            return "RAW", None, None
                        bit = 1 if abs(p[idx + 1] - 1690) < 1690 * tol else 0
                        if i < 16:
                            addr = (addr << 1) | bit
                        else:
                            cmd = (cmd << 1) | bit
                    real_addr = addr & 0xFFFF
                    real_cmd = (cmd >> 8) & 0xFF
                    # Verifica checksum
                    inv_cmd = cmd & 0xFF
                    if (real_cmd + inv_cmd) & 0xFF == 0xFF:
                        return "NEC", real_addr, real_cmd
                    return "NEC_EXT", real_addr, real_cmd
                except Exception:
                    pass

        # Samsung
        if len(pattern) >= 68:
            p = pattern
            tol = 0.25
            if (abs(p[0] - 4500) < 4500 * tol and abs(p[1] - 4500) < 4500 * tol):
                try:
                    addr = 0
                    cmd = 0
                    for i in range(32):
                        idx = 2 + i * 2
                        if idx + 1 >= len(p):
                            break
                        bit = 1 if abs(p[idx + 1] - 1690) < 1690 * tol else 0
                        if i < 16:
                            addr = (addr << 1) | bit
                        else:
                            cmd = (cmd << 1) | bit
                    return "SAMSUNG", addr & 0xFFFF, (cmd >> 8) & 0xFF
                except Exception:
                    pass

        # Sony (leader: 2400 on, 600 off)
        if len(pattern) >= 26:
            p = pattern
            tol = 0.3
            if (abs(p[0] - 2400) < 2400 * tol and abs(p[1] - 600) < 600 * tol):
                bits = (len(pattern) - 2) // 2
                try:
                    cmd = 0
                    addr = 0
                    cmd_bits = min(bits - 5, 7)
                    addr_bits = bits - cmd_bits
                    for i in range(cmd_bits):
                        idx = 2 + i * 2
                        bit = 1 if abs(p[idx + 1] - 1200) < 1200 * tol else 0
                        cmd = (cmd << 1) | bit
                    for i in range(addr_bits):
                        idx = 2 + (cmd_bits + i) * 2
                        bit = 1 if abs(p[idx + 1] - 1200) < 1200 * tol else 0
                        addr = (addr << 1) | bit
                    return f"SONY_{bits}", addr, cmd
                except Exception:
                    pass

        return "RAW", None, None

    # ── Test ──────────────────────────────────────────────────────────
    def test_last_code(self):
        """Reinvia l'ultimo codice appreso per test."""
        if not self.last_raw:
            print("❌ Nessun codice da testare. Esegui 'learn' prima.")
            return

        if not self.device:
            print("❌ Nessun dispositivo connesso.")
            return

        print(f"\n🔄 Invio codice... ({len(self.last_hex)//2} bytes)")
        self.device.send_data(self.last_raw)
        print("✅ Codice inviato! Il dispositivo dovrebbe aver risposto.")

    # ── Export ────────────────────────────────────────────────────────
    def export_to_json(self, filename: str = EXPORT_FILE) -> str:
        """Esporta tutti i codici appresi in formato JSON per IRXiaomi."""
        if not self.learned_codes:
            print("❌ Nessun codice da esportare.")
            return ""

        data = {
            "version": 1,
            "app": "IRXiaomi",
            "source": "Broadlink Cloner",
            "exported_at": datetime.now().isoformat(),
            "device": self._get_device_type(self.device.devtype) if self.device else "unknown",
            "device_ip": self.device.host[0] if self.device else "unknown",
            "code_count": len(self.learned_codes),
            "codes": self.learned_codes,
        }

        with open(filename, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

        print(f"\n💾 Esportati {len(self.learned_codes)} codici in '{filename}'")
        return filename

    def import_into_app_json(self) -> Dict:
        """Converte i codici nel formato specifico IRXiaomi per l'import nell'app."""
        app_codes = []
        for code in self.learned_codes:
            app_code = {
                "name": code["name"],
                "display_name": code["name"],
                "brand": input(f"\n   Marca per '{code['name']}' [Broadlink]: ") or "Broadlink",
                "model": input(f"   Modello (opzionale) [{code.get('device_type', '')}]: ") or "",
                "device_type": input(f"   Tipo dispositivo [TV/AC/AUDIO/OTHER] [OTHER]: ") or "OTHER",
                "protocol": code["protocol"],
                "frequency": code["frequency"],
                "pattern": code["pattern"],
            }

            # Se decodificato, chiedi conferma
            if code["protocol"] != "RAW":
                addr = code.get("address")
                cmd = code.get("command")
                if addr is not None:
                    app_code["address"] = addr
                if cmd is not None:
                    app_code["command"] = cmd

            app_codes.append(app_code)

        return {
            "version": 1,
            "app": "IRXiaomi",
            "exported_at": datetime.now().isoformat(),
            "codes": app_codes,
        }

    # ── Interactive ───────────────────────────────────────────────────
    def interactive_mode(self):
        """Modalità interattiva guidata."""
        print("\n" + "="*60)
        print("  IRXiaomi — Broadlink Interactive Cloner")
        print("="*60)

        # 1. Discovery
        if not self.discover():
            return

        while True:
            print("\n" + "─"*50)
            print(f"  Codici appresi: {len(self.learned_codes)}")
            print("─"*50)
            print("  1. 📖 Impara un codice")
            print("  2. 🔄 Testa ultimo codice")
            print("  3. 💾 Esporta tutto in JSON")
            print("  4. 📋 Lista codici appresi")
            print("  5. 🗑️  Cancella ultimo codice")
            print("  0. 🚪 Esci")
            print("─"*50)

            choice = input("  Scelta: ").strip()

            if choice == "1":
                name = input("  Nome comando (es. Power, Volume Up): ").strip()
                if not name:
                    name = f"Code_{len(self.learned_codes) + 1}"
                
                device_type = input("  Tipo dispositivo [TV/AC/AUDIO/OTHER] [OTHER]: ").strip()
                brand = input("  Marca [Broadlink]: ").strip() or "Broadlink"

                if self.learn(name):
                    # Aggiorna metadati
                    self.learned_codes[-1]["device_type"] = device_type if device_type else "OTHER"
                    self.learned_codes[-1]["brand"] = brand

                input("\n  Premi INVIO per continuare...")

            elif choice == "2":
                self.test_last_code()
                input("\n  Premi INVIO per continuare...")

            elif choice == "3":
                self.export_to_json()
                
                # Chiedi se vuole anche l'export specifico per app
                app_export = input("  Creare anche file importabile nell'app? (s/N): ").strip().lower()
                if app_export == "s":
                    app_data = self.import_into_app_json()
                    app_file = "irxiaomi_import_codes.json"
                    with open(app_file, "w", encoding="utf-8") as f:
                        json.dump(app_data, f, indent=2, ensure_ascii=False)
                    print(f"\n✅ File creato: {app_file}")
                    print(f"   Trasferiscilo sul telefono e usa l'app → Database → Importa")

                input("\n  Premi INVIO per continuare...")

            elif choice == "4":
                if not self.learned_codes:
                    print("\n  Nessun codice ancora.")
                else:
                    print(f"\n  📋 {len(self.learned_codes)} codici:")
                    for i, code in enumerate(self.learned_codes):
                        print(f"     [{i+1}] {code['name']} — {code['protocol']}")
                input("\n  Premi INVIO per continuare...")

            elif choice == "5":
                if self.learned_codes:
                    removed = self.learned_codes.pop()
                    print(f"\n  🗑️  Rimosso: {removed['name']}")
                input("\n  Premi INVIO per continuare...")

            elif choice == "0":
                # All'uscita, chiede se salvare
                if self.learned_codes:
                    save = input("\n  Salvare i codici appresi? (S/n): ").strip().lower()
                    if save != "n":
                        self.export_to_json()
                print("\n  👋 Arrivederci!")
                break


# ──────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────
def main():
    print("\n" + "█"*60)
    print("  IRXiaomi — Broadlink RM Cloner v" + VERSION)
    print("  Clona codici IR da qualsiasi telecomando usando Broadlink")
    print("█"*60)

    cloner = BroadlinkCloner()

    # Modalità rapida
    if "--fast" in sys.argv:
        if cloner.discover(timeout=5):
            while True:
                name = input("\n  Nome codice (o INVIO per uscire): ").strip()
                if not name:
                    break
                if cloner.learn(name, timeout=20):
                    test = input("  Testare? (s/N): ").strip().lower()
                    if test == "s":
                        cloner.test_last_code()
            cloner.export_to_json()
        return

    # Modalità interattiva
    cloner.interactive_mode()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n❌ Interrotto dall'utente.")
        sys.exit(0)
    except Exception as e:
        print(f"\n❌ Errore: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
