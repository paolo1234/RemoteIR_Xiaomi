package com.irxiaomi.clone

import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import com.irxiaomi.ir.PatternGenerator
import com.irxiaomi.model.Brand
import com.irxiaomi.model.DeviceType
import com.irxiaomi.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generatore automatico di varianti di codici IR.
 * Utile per trovare codici "mancanti" quando si conosce brand e tipo dispositivo
 * ma non si hanno tutti i comandi.
 *
 * Strategie:
 * 1. **Shifting indirizzo**: prova ±1, ±2 sull'indirizzo
 * 2. **Comandi standard**: genera tutti i comandi tipici per quel device type
 * 3. **Protocollo alternativo**: prova stesso comando con protocollo diverso
 * 4. **Pattern fusion**: combina header di un codice con data di un altro
 */
class VariantGenerator(private val dao: IrCodeDao) {

    companion object {
        /** Comandi standard TV per protocollo NEC (indirizzo Samsung 0xE0E0, LG 0x20DF, ecc.) */
        val TV_COMMANDS = listOf(
            0x40BF to "Power",
            0x609F to "Volume Up",
            0xA05F to "Volume Down",
            0x20DF to "Channel Up",
            0xE01F to "Channel Down",
            0x906F to "Mute",
            0xD02F to "Input",
            0x50AF to "Menu",
            0x10EF to "OK / Enter",
            0x8877 to "Up",
            0x9867 to "Down",
            0xA857 to "Left",
            0x48B7 to "Right",
            0x28D7 to "Back",
            0x7A85 to "Home",
            0x12ED to "Exit",
            0x5AA5 to "Info",
            0x1AE5 to "Source",
            0x0AF5 to "Guide",
            0xE21D to "Netflix",
            0x52AD to "YouTube",
            0x9A65 to "Prime Video",
            0x42BD to "Settings",
            0xE41B to "Subtitles",
            0x8A75 to "Aspect Ratio",
            0x4AB5 to "Sleep Timer",
            0xAA55 to "Picture Mode",
            0x3AC5 to "Sound Mode"
        )

        /** Comandi standard AC (solo modalità, temperature fittizie) */
        val AC_COMMANDS = listOf(
            0x01 to "Power",
            0x02 to "Mode Cool",
            0x03 to "Mode Heat",
            0x04 to "Mode Dry",
            0x05 to "Mode Fan",
            0x06 to "Mode Auto",
            0x07 to "Fan Auto",
            0x08 to "Fan Low",
            0x09 to "Fan Medium",
            0x0A to "Fan High",
            0x0B to "Swing On",
            0x0C to "Swing Off",
            0x0D to "Temp Up",
            0x0E to "Temp Down",
            0x0F to "Sleep",
            0x10 to "Turbo"
        )

        /** Comandi standard Audio */
        val AUDIO_COMMANDS = listOf(
            0x40BF to "Power",
            0x609F to "Volume Up",
            0xA05F to "Volume Down",
            0x906F to "Mute",
            0x10EF to "Input",
            0xD02F to "Source",
            0x50AF to "Play/Pause",
            0x20DF to "Next",
            0xE01F to "Previous",
            0x7A85 to "Bluetooth",
            0x1AE5 to "Bass Boost"
        )
    }

    /**
     * Trova "codici mancanti" per un brand/device type.
     * Confronta i codici presenti nel DB con la lista di comandi standard
     * e genera quelli assenti.
     */
    suspend fun findMissingCodes(brand: String, deviceType: String): List<IrCodeEntity> {
        return withContext(Dispatchers.Default) {
            val existing = dao.getCodesForMissingAnalysis(brand, deviceType)
            val existingCommands = existing.mapNotNull { it.command }.toSet()

            val standardCommands = when (DeviceType.fromString(deviceType)) {
                DeviceType.TV -> TV_COMMANDS
                DeviceType.AC -> AC_COMMANDS.map { it.first.toLong() to it.second }
                DeviceType.AUDIO -> AUDIO_COMMANDS
                else -> emptyList()
            }

            // Filtra comandi già presenti
            val missing = standardCommands.filter { (cmd, _) ->
                cmd.toLong() !in existingCommands
            }

            // Genera codici mancanti basati sul primo codice esistente (per protocollo/freq)
            val template = existing.firstOrNull() ?: return@withContext emptyList()
            val protocol = Protocol.fromString(template.protocol)
            val freq = template.frequency
            val address = template.address ?: return@withContext emptyList()

            missing.map { (cmd, name) ->
                val pattern = PatternGenerator.generate(protocol.name, address, cmd.toLong(), freq)
                val patternStr = pattern?.let { IrCodeEntity.patternToString(it) } ?: ""

                IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = brand,
                    deviceType = deviceType,
                    protocol = protocol.name,
                    frequency = freq,
                    pattern = patternStr,
                    address = address,
                    command = cmd.toLong(),
                    category = "$brand-$deviceType",
                    tags = "$brand,$deviceType,${protocol.name},variant",
                    source = "variant",
                    isVerified = false,
                    notes = "Generato automaticamente da variante di $brand"
                )
            }
        }
    }

    /**
     * Genera varianti shifting dell'indirizzo.
     * Utile quando un telecomando ha lo stesso protocollo ma indirizzo diverso.
     */
    suspend fun generateAddressShifts(base: IrCodeEntity, deltas: List<Int> = listOf(-2, -1, 1, 2)): List<IrCodeEntity> {
        return withContext(Dispatchers.Default) {
            val address = base.address ?: return@withContext emptyList()
            val protocol = Protocol.fromString(base.protocol)

            deltas.map { delta ->
                val newAddr = address + delta
                val pattern = PatternGenerator.generate(protocol.name, newAddr, base.command ?: 0, base.frequency)
                val patternStr = pattern?.let { IrCodeEntity.patternToString(it) } ?: ""

                base.copy(
                    id = 0,
                    address = newAddr,
                    pattern = patternStr,
                    name = "${base.name} (addr+$delta)",
                    displayName = "${base.displayName} (A${delta})",
                    source = "variant",
                    isVerified = false,
                    notes = "Generato shifting address di ${delta}",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Genera varianti cambiando protocollo (se stesso comando).
     * Es: se abbiamo un codice NEC, prova a generare lo stesso comando in SAMSUNG.
     */
    suspend fun generateProtocolVariants(base: IrCodeEntity): List<IrCodeEntity> {
        return withContext(Dispatchers.Default) {
            val altProtocols = when (Protocol.fromString(base.protocol)) {
                Protocol.NEC -> listOf(Protocol.SAMSUNG, Protocol.SONY, Protocol.RC5)
                Protocol.SAMSUNG -> listOf(Protocol.NEC, Protocol.SONY)
                Protocol.SONY -> listOf(Protocol.NEC, Protocol.RC5)
                Protocol.RC5 -> listOf(Protocol.NEC, Protocol.SAMSUNG)
                else -> return@withContext emptyList()
            }

            altProtocols.mapNotNull { proto ->
                val pattern = PatternGenerator.generate(proto.name, base.address ?: 0, base.command ?: 0, proto.defaultFrequency)
                val patternStr = pattern?.let { IrCodeEntity.patternToString(it) } ?: return@mapNotNull null

                base.copy(
                    id = 0,
                    protocol = proto.name,
                    frequency = proto.defaultFrequency,
                    pattern = patternStr,
                    name = "${base.name} (${proto.name})",
                    displayName = "${base.displayName} (${proto.displayName})",
                    source = "variant",
                    isVerified = false,
                    notes = "Convertito da ${base.protocol} a ${proto.name}",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Clona un codice IR da un altro dispositivo/brand.
     * Adatta il pattern se necessario.
     */
    fun cloneCode(source: IrCodeEntity, targetBrand: String, targetDeviceType: String, newName: String? = null): IrCodeEntity {
        return source.copy(
            id = 0,
            name = newName ?: source.name,
            displayName = newName ?: source.displayName,
            brand = targetBrand,
            deviceType = targetDeviceType,
            category = "$targetBrand-$targetDeviceType",
            tags = "$targetBrand,$targetDeviceType,${source.protocol},cloned",
            source = "cloned",
            isVerified = false,
            notes = "Clonato da ${source.brand} ${source.deviceType}",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastUsedAt = 0L,
            usageCount = 0,
            isFavorite = false
        )
    }
}
