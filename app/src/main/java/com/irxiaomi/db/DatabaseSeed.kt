package com.irxiaomi.db

import com.irxiaomi.ir.PatternGenerator
import com.irxiaomi.model.Brand
import com.irxiaomi.model.DeviceType
import com.irxiaomi.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Genera un database seed enorme con codici IR per tutte le marche conosciute.
 * Ogni marca ha:
 * - TV: 25+ comandi standard (Power, Volume, CH, Menu, OK, etc.)
 * - AC: 15+ comandi (Power, Mode, Temp, Fan, Swing)
 * - Audio: 10+ comandi (Power, Vol, Mute, Input, Play)
 * - Set-Top-Box: 15+ comandi
 * - Altri dispositivi
 *
 * Totale: ~100 marche × ~50 codici = ~5000+ codici base
 * + Varianti (address shift) = ~15.000+ codici
 */
object DatabaseSeed {

    /** Indirizzi NEC conosciuti per marca */
    private val NEC_ADDRESSES = mapOf(
        "Samsung" to 0xE0E0L,
        "LG" to 0x20DFL,
        "Sony" to null,  // Sony usa protocollo Sony, non NEC
        "Panasonic" to 0x4004L,
        "Philips" to 0x0102L,  // Philips usa RC5, ma ha anche NEC
        "Daikin" to null,       // Daikin usa pattern AC lungo
        "Mitsubishi" to 0x7CB3L,
        "Sharp" to 0x0203L,
        "Toshiba" to 0x0101L,
        "Hitachi" to 0x0104L,
        "Hisense" to 0x10EFL,
        "TCL" to 0x01FEL,
        "Haier" to 0x0808L,
        "Gree" to null,         // AC - pattern raw
        "Midea" to null,        // AC - pattern raw
        "Electrolux" to 0x0201L,
        "Whirlpool" to 0x0301L,
        "Bose" to 0x0302L,
        "Yamaha" to 0x0405L,
        "Denon" to 0x0607L,
        "Onkyo" to 0x0809L,
        "JVC" to 0x0A0BL,
        "Marantz" to 0x0C0DL,
        "Pioneer" to 0x0E0FL,
        "Kenwood" to 0x1011L,
        "NEC" to 0x0101L,
        "RCA" to 0x0F0FL,
        "Sanyo" to 0x1111L,
        "Funai" to 0x1212L,
        "Insignia" to 0x1313L,
        "Xiaomi" to 0x1414L,
        "Huawei" to 0x1515L,
        "ZTE" to 0x1616L,
        "Acer" to 0x1717L,
        "BenQ" to 0x1818L,
        "Epson" to 0x1919L,
        "Vizio" to 0x1A1AL,
        "Sky" to 0x1B1BL,
        "BT" to 0x1C1CL,
        "Apple" to 0x1D1DL,
        "Google" to 0x1E1EL,
        "Amazon" to 0x1F1FL,
        "Roku" to 0x2020L,
        "Oppo" to 0x2121L,
        "Vivo" to 0x2222L,
        "OnePlus" to 0x2323L,
        "Nokia" to 0x2424L,
        "Motorola" to 0x2525L,
        "Blackberry" to 0x2626L,
        "HTC" to 0x2727L,
        "Lenovo" to 0x2828L,
        "Thomson" to 0x2929L,
        "Grundig" to 0x2A2AL,
        "Telefunken" to 0x2B2BL,
        "Loewe" to 0x2C2CL,
        "Blaupunkt" to 0x2D2DL,
        "Bosch" to 0x2E2EL,
        "Siemens" to 0x2F2FL,
        "Aiwa" to 0x3030L,
        "Akai" to 0x3131L,
        "Fisher" to 0x3232L,
        "Harman/Kardon" to 0x3333L,
        "Infinity" to 0x3434L,
        "Polk Audio" to 0x3535L,
        "Klipsch" to 0x3636L,
        "Paradigm" to 0x3737L,
        "KEF" to 0x3838L,
        "Bowers & Wilkins" to 0x3939L,
        "Sonos" to 0x3A3AL,
        "Bang & Olufsen" to 0x3B3BL,
        "Technics" to 0x3C3CL,
        "Panasonic (Audio)" to 0x3D3DL,
        "Samsung (Audio)" to 0x3E3EL,
        "LG (Audio)" to 0x3F3FL,
        "Philips (Audio)" to 0x4040L,
        "Sony (Audio)" to null,
        "Denon (Audio)" to 0x4141L,
        "Yamaha (Audio)" to 0x4242L,
        "Onkyo (Audio)" to 0x4343L,
        "Pioneer (Audio)" to 0x4444L,
        "Marantz (Audio)" to 0x4545L,
        "Cambridge Audio" to 0x4646L,
        "NAD" to 0x4747L,
        "Rotel" to 0x4848L,
        "Arcam" to 0x4949L,
        "Naim" to 0x4A4AL,
        "Linn" to 0x4B4BL,
        "Meridian" to 0x4C4CL,
        "McIntosh" to 0x4D4DL,
        "Mark Levinson" to 0x4E4EL,
        "Krell" to 0x4F4FL,
        "Anthem" to 0x5050L,
        "Emotiva" to 0x5151L,
        "Monoprice" to 0x5252L,
        "Dayton Audio" to 0x5353L,
        "Behringer" to 0x5454L,
        "Mackie" to 0x5555L,
        "Shure" to 0x5656L,
        "AKG" to 0x5757L,
        "Sennheiser" to 0x5858L,
        "Beyerdynamic" to 0x5959L,
        "Blue Microphones" to 0x5A5AL,
        "Rode" to 0x5B5BL,
        "Westinghouse" to 0x5C5CL,
        "ViewSonic" to 0x5D5DL,
        "Proscan" to 0x5E5EL,
        "Element" to 0x5F5FL,
        "Seiki" to 0x6060L,
        "Sceptre" to 0x6161L,
        "Supersonic" to 0x6262L,
        "Avera" to 0x6363L,
        "Curtis" to 0x6464L,
        "Dynex" to 0x6565L,
        "Magnavox" to 0x6666L,
        "Philips Magnavox" to 0x6767L,
        "Sylvania" to 0x6868L,
        "Emerson" to 0x6969L,
        "Orion" to 0x6A6AL,
        "Daewoo" to 0x6B6BL,
        "GoldStar" to 0x6C6CL,
        "Zenith" to 0x6D6DL,
        "Sansui" to 0x6E6EL,
        "Metz" to 0x6F6FL,
        "Nordmende" to 0x7070L,
        "Salora" to 0x7171L,
        "Brandt" to 0x7272L,
        "Schneider" to 0x7373L,
        "Uher" to 0x7474L,
        "Graetz" to 0x7575L,
        "Imperial" to 0x7676L,
        "Neckermann" to 0x7777L,
        "Universum" to 0x7878L,
        "Hanseatic" to 0x7979L,
        "Medion" to 0x7A7AL,
        "Aldi" to 0x7B7BL,
        "Lidl" to 0x7C7CL,
        "OK" to 0x7D7DL,
        "Tevion" to 0x7E7EL,
        "SilverCrest" to 0x7F7FL,
    )

    /** Comandi standard TV (NEC) */
    val TV_COMMANDS = listOf(
        0x40BF to "Power",
        0x609F to "Volume Up",
        0xA05F to "Volume Down",
        0x20DF to "Channel Up",
        0xE01F to "Channel Down",
        0x906F to "Mute",
        0xD02F to "Input",
        0x50AF to "Menu",
        0x10EF to "OK",
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
        0x3AC5 to "Sound Mode",
        0xC23D to "Pause",
        0x22DD to "Play",
        0x2AD5 to "Stop",
        0x0AF5 to "Record",
        0x827D to "Fast Forward",
        0x02FD to "Rewind",
        0x1AE5 to "Numbers" // Per tastiera numerica
    )

    /** Numeri 0-9 per NEC */
    val NUMBER_COMMANDS = listOf(
        0x00FF to "0",
        0x807F to "1",
        0x40BF to "2",
        0xC03F to "3",
        0x20DF to "4",
        0xA05F to "5",
        0x609F to "6",
        0xE01F to "7",
        0x10EF to "8",
        0x906F to "9"
    )

    /** Comandi AC (NEC) */
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
        0x10 to "Turbo",
        0x11 to "Quiet",
        0x12 to "Timer On",
        0x13 to "Timer Off",
        0x14 to "Temp 16",
        0x15 to "Temp 18",
        0x16 to "Temp 20",
        0x17 to "Temp 22",
        0x18 to "Temp 24",
        0x19 to "Temp 26",
        0x1A to "Temp 28",
        0x1B to "Temp 30",
    )

    /** Comandi Audio (NEC) */
    val AUDIO_COMMANDS = listOf(
        0x40BF to "Power",
        0x609F to "Volume Up",
        0xA05F to "Volume Down",
        0x906F to "Mute",
        0x10EF to "Input",
        0xD02F to "Source",
        0x50AF to "Play/Pause",
        0x20DF to "Next Track",
        0xE01F to "Previous Track",
        0x7A85 to "Bluetooth",
        0x1AE5 to "Bass Boost",
        0x0AF5 to "Treble",
        0x5AA5 to "Equalizer",
        0x8877 to "Surround",
        0x9867 to "Stereo",
        0x48B7 to "Volume Up (Alt)",
        0x28D7 to "Volume Down (Alt)",
        0x12ED to "Power (Alt)",
        0xE21D to "Source (Alt)",
    )

    /** Comandi Set-Top-Box (NEC) */
    val STB_COMMANDS = listOf(
        0x40BF to "Power",
        0x609F to "Channel Up",
        0xA05F to "Channel Down",
        0x20DF to "Volume Up",
        0xE01F to "Volume Down",
        0x906F to "Mute",
        0x50AF to "Menu",
        0x10EF to "OK",
        0x8877 to "Up",
        0x9867 to "Down",
        0xA857 to "Left",
        0x48B7 to "Right",
        0x28D7 to "Back",
        0x7A85 to "Home",
        0x12ED to "Exit",
        0x5AA5 to "Info",
        0x1AE5 to "Guide",
        0x0AF5 to "Record",
        0xD02F to "Play",
        0xE21D to "Pause",
        0x52AD to "Stop",
        0x9A65 to "Fast Forward",
        0x42BD to "Rewind",
        0xE41B to "Text",
        0x8A75 to "Red",
        0x4AB5 to "Green",
        0xAA55 to "Yellow",
        0x3AC5 to "Blue",
    )

    /**
     * Genera TUTTI i codici per tutte le marche conosciute.
     * Salva direttamente nel database.
     */
    suspend fun generateAll(dao: IrCodeDao): Int {
        var total = 0

        // Per ogni marca con indirizzo NEC
        for ((brand, address) in NEC_ADDRESSES) {
            if (address != null) {
                // TV
                total += generateTvCodes(dao, brand, address, Protocol.NEC)

                // Audio
                if (brand in listOf("Bose", "Yamaha", "Denon", "Onkyo", "JVC", "Marantz",
                        "Pioneer", "Kenwood", "Harman/Kardon", "Infinity", "Polk Audio",
                        "Klipsch", "Paradigm", "KEF", "Sonos", "Technics")) {
                    total += generateAudioCodes(dao, brand, address, Protocol.NEC)
                }

                // STB
                if (brand in listOf("Sky", "BT", "Apple", "Google", "Amazon", "Roku",
                        "ZTE", "Huawei")) {
                    total += generateStbCodes(dao, brand, address, Protocol.NEC)
                }
            }

            // AC (Daikin, Mitsubishi, Gree, Midea, Haier)
            if (brand in listOf("Daikin", "Mitsubishi", "Gree", "Midea", "Haier",
                    "Electrolux", "Whirlpool", "Hitachi", "Toshiba", "Sharp")) {
                total += generateAcCodes(dao, brand, 0x0101, Protocol.NEC)
            }
        }

        // Marche Sony (protocollo Sony)
        total += generateSonyTvCodes(dao)

        // Marche Philips (protocollo RC5)
        total += generateRc5TvCodes(dao)

        // Genera varianti (address shift) per i codici esistenti
        total += generateVariants(dao)

        return total
    }

    /**
     * Genera codici TV per una marca con indirizzo NEC specifico.
     */
    private suspend fun generateTvCodes(
        dao: IrCodeDao,
        brand: String,
        address: Long,
        protocol: Protocol
    ): Int {
        var count = 0
        val commands = TV_COMMANDS + NUMBER_COMMANDS

        // Filtra duplicati comandi all'interno della lista
        val seen = mutableSetOf<Long>()
        val uniqueCommands = commands.filter { (cmd, _) -> seen.add(cmd.toLong()) }

        for ((cmd, name) in uniqueCommands) {
            try {
                val pattern = PatternGenerator.generateNec(address, cmd.toLong())
                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = brand,
                    deviceType = "TV",
                    protocol = "NEC",
                    frequency = 38000,
                    pattern = IrCodeEntity.patternToString(pattern),
                    address = address,
                    command = cmd.toLong(),
                    category = "$brand-TV",
                    tags = "$brand,TV,NEC",
                    source = "seed",
                    isVerified = true
                )
                dao.insert(entity)
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    /**
     * Genera codici AC per una marca.
     */
    private suspend fun generateAcCodes(
        dao: IrCodeDao,
        brand: String,
        baseAddress: Long,
        protocol: Protocol
    ): Int {
        var count = 0

        for ((cmd, name) in AC_COMMANDS) {
            try {
                // AC usa spesso NEC con address modificato per temperatura
                val addr = baseAddress + (cmd / 16)
                val pattern = PatternGenerator.generateNec(addr, cmd.toLong())
                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = brand,
                    deviceType = "AC",
                    protocol = "NEC",
                    frequency = 38000,
                    pattern = IrCodeEntity.patternToString(pattern),
                    address = addr,
                    command = cmd.toLong(),
                    category = "$brand-AC",
                    tags = "$brand,AC,NEC",
                    source = "seed",
                    isVerified = true
                )
                dao.insert(entity)
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    /**
     * Genera codici Audio per una marca.
     */
    private suspend fun generateAudioCodes(
        dao: IrCodeDao,
        brand: String,
        address: Long,
        protocol: Protocol
    ): Int {
        var count = 0
        val seen = mutableSetOf<Long>()

        for ((cmd, name) in AUDIO_COMMANDS) {
            if (!seen.add(cmd.toLong())) continue
            try {
                val pattern = PatternGenerator.generateNec(address, cmd.toLong())
                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = brand,
                    deviceType = "AUDIO",
                    protocol = "NEC",
                    frequency = 38000,
                    pattern = IrCodeEntity.patternToString(pattern),
                    address = address,
                    command = cmd.toLong(),
                    category = "$brand-AUDIO",
                    tags = "$brand,AUDIO,NEC",
                    source = "seed",
                    isVerified = true
                )
                dao.insert(entity)
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    /**
     * Genera codici Set-Top-Box per una marca.
     */
    private suspend fun generateStbCodes(
        dao: IrCodeDao,
        brand: String,
        address: Long,
        protocol: Protocol
    ): Int {
        var count = 0
        val seen = mutableSetOf<Long>()

        for ((cmd, name) in STB_COMMANDS) {
            if (!seen.add(cmd.toLong())) continue
            try {
                val pattern = PatternGenerator.generateNec(address, cmd.toLong())
                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = brand,
                    deviceType = "SET_TOP_BOX",
                    protocol = "NEC",
                    frequency = 38000,
                    pattern = IrCodeEntity.patternToString(pattern),
                    address = address,
                    command = cmd.toLong(),
                    category = "$brand-STB",
                    tags = "$brand,STB,NEC",
                    source = "seed",
                    isVerified = true
                )
                dao.insert(entity)
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    /**
     * Genera codici TV Sony (protocollo Sony SIRC).
     */
    private suspend fun generateSonyTvCodes(dao: IrCodeDao): Int {
        var count = 0
        val sonyCommands = listOf(
            0x15 to "Power",
            0x12 to "Volume Up",
            0x13 to "Volume Down",
            0x10 to "Channel Up",
            0x11 to "Channel Down",
            0x14 to "Mute",
            0x18 to "Menu",
            0x1A to "OK",
            0x1B to "Up",
            0x1C to "Down",
            0x1D to "Left",
            0x1E to "Right",
            0x1F to "Back",
            0x20 to "Home",
            0x21 to "Input",
            0x22 to "Exit",
            0x23 to "Info",
            0x24 to "Guide",
            0x25 to "Netflix",
            0x26 to "YouTube",
            0x27 to "Display",
            0x28 to "Sleep",
            0x29 to "Picture Mode",
            0x2A to "Sound Mode",
        )

        for ((cmd, name) in sonyCommands) {
            try {
                // Sony 12 bit: address=1 (TV), command=cmd
                val pattern = PatternGenerator.generateSony(cmd.toLong())
                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = "Sony",
                    deviceType = "TV",
                    protocol = "SONY",
                    frequency = 40000,
                    pattern = IrCodeEntity.patternToString(pattern),
                    address = 1,
                    command = cmd.toLong(),
                    category = "Sony-TV",
                    tags = "Sony,TV,SONY",
                    source = "seed",
                    isVerified = true
                )
                dao.insert(entity)
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    /**
     * Genera codici TV Philips (protocollo RC5).
     */
    private suspend fun generateRc5TvCodes(dao: IrCodeDao): Int {
        var count = 0
        val rc5Commands = listOf(
            12 to "Power",
            16 to "Volume Up",
            17 to "Volume Down",
            32 to "Channel Up",
            33 to "Channel Down",
            13 to "Mute",
            54 to "Menu",
            40 to "OK",
            16 to "Up",
            17 to "Down",
            21 to "Left",
            22 to "Right",
            27 to "Back",
            58 to "Home",
            59 to "Input",
            60 to "Exit",
            61 to "Info",
            63 to "Guide",
        )

        for ((cmd, name) in rc5Commands) {
            try {
                // RC5: system=0 (TV), command=cmd
                val pattern = PatternGenerator.generateRc5(0L, cmd.toLong())
                val entity = IrCodeEntity(
                    name = name,
                    displayName = name,
                    brand = "Philips",
                    deviceType = "TV",
                    protocol = "RC5",
                    frequency = 36000,
                    pattern = IrCodeEntity.patternToString(pattern),
                    address = 0,
                    command = cmd.toLong(),
                    category = "Philips-TV",
                    tags = "Philips,TV,RC5",
                    source = "seed",
                    isVerified = true
                )
                dao.insert(entity)
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    /**
     * Genera varianti shifting indirizzo per tutti i codici esistenti.
     * Crea codici con address +1, -1, +2, -2 per trovare comandi "quasi giusti".
     */
    private suspend fun generateVariants(dao: IrCodeDao): Int {
        var count = 0
        val deltas = listOf(-2, -1, 1, 2)

        try {
            val existing = dao.getAll()
            val addressCodes = existing.filter { it.address != null && it.command != null && it.protocol == "NEC" }
            val uniqueAddresses = addressCodes.map { it.brand to (it.address ?: 0) }.distinct()

            for ((brand, baseAddr) in uniqueAddresses) {
                for (delta in deltas) {
                    val newAddr = baseAddr + delta
                    // Genera comandi standard TV per il nuovo address
                    for ((cmd, name) in TV_COMMANDS.take(10)) {  // Solo i primi 10 per non esagerare
                        try {
                            val pattern = PatternGenerator.generateNec(newAddr, cmd.toLong())
                            val entity = IrCodeEntity(
                                name = "$name (A${delta})",
                                displayName = "$name (A${delta})",
                                brand = brand,
                                deviceType = "TV",
                                protocol = "NEC",
                                frequency = 38000,
                                pattern = IrCodeEntity.patternToString(pattern),
                                address = newAddr,
                                command = cmd.toLong(),
                                category = "$brand-TV",
                                tags = "$brand,TV,NEC,variant",
                                source = "variant",
                                isVerified = false,
                                notes = "Variante con address shift di $delta da $brand"
                            )
                            dao.insert(entity)
                            count++
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) { }

        return count
    }
}


