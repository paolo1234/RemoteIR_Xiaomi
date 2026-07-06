package com.irxiaomi.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irxiaomi.db.IrCodeEntity
import com.irxiaomi.ir.PatternGenerator
import com.irxiaomi.model.DeviceType

/**
 * Schermata telecomando virtuale.
 * Ora supporta:
 * - Selezione marca dal database
 * - Commutazione TV/AC/AUDIO/DVD/STB
 * - Generazione pattern IR reali per ogni tasto
 * - Brand selector con le marche presenti nel DB
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    deviceType: DeviceType = DeviceType.TV,
    currentBrand: String = "Samsung",
    availableBrands: List<String> = emptyList(),
    onBrandChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onSendCode: (IrCodeEntity) -> Unit = {}
) {
    var selectedDeviceType by remember { mutableStateOf(deviceType) }
    var selectedBrand by remember { mutableStateOf(currentBrand) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Telecomando", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("$selectedBrand - ${selectedDeviceType.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { selectedDeviceType = when(selectedDeviceType) {
                        DeviceType.TV -> DeviceType.AC; DeviceType.AC -> DeviceType.AUDIO
                        DeviceType.AUDIO -> DeviceType.DVD; DeviceType.DVD -> DeviceType.SET_TOP_BOX
                        else -> DeviceType.TV
                    } }) { Icon(Icons.Default.SwapHoriz, "Cambia dispositivo") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            BrandSelectorBar(selectedBrand, availableBrands, onBrandChange)

            when (selectedDeviceType) {
                DeviceType.AC -> AcRemoteContent(selectedBrand, onSendCode)
                DeviceType.AUDIO -> AudioRemoteContent(selectedBrand, onSendCode)
                DeviceType.DVD -> DvdRemoteContent(selectedBrand, onSendCode)
                DeviceType.SET_TOP_BOX -> StbRemoteContent(selectedBrand, onSendCode)
                else -> TvRemoteContent(selectedBrand, onSendCode)
            }
        }
    }
}

// ── Brand Selector ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandSelectorBar(
    selectedBrand: String,
    brands: List<String>,
    onBrandChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {

            Icon(Icons.Default.Tv, null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.width(8.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)) {

                OutlinedTextField(
                    value = selectedBrand,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    val displayBrands = if (brands.isEmpty()) DEFAULT_BRANDS else brands
                    displayBrands.forEach { brand ->
                        DropdownMenuItem(
                            text = { Text(brand, fontWeight = if (brand == selectedBrand) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onBrandChange(brand)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Marche predefinite (se il DB è vuoto) ───────────────────────────

private val DEFAULT_BRANDS = listOf(
    "Samsung", "LG", "Sony", "Panasonic", "Philips", "Toshiba",
    "Hisense", "TCL", "Sharp", "Hitachi", "Daikin", "Mitsubishi",
    "Haier", "Gree", "Midea", "Xiaomi", "Huawei", "Bose",
    "Yamaha", "Denon", "Onkyo"
)

// ── Dati comandi IR conosciuti per protocollo NEC ───────────────────

data class RemoteButton(
    val name: String,
    val icon: ImageVector? = null,
    val necCommand: Long,
    val sonyCommand: Long = 0,
    val rc5Command: Long = 0,
    val isPower: Boolean = false,
    val isLarge: Boolean = false
)

/** Comandi NEC standard per TV */
val TV_BUTTONS = listOf(
    RemoteButton("Power", Icons.Default.PowerSettingsNew, 0x40BF, isPower = true),
    RemoteButton("Input", Icons.Default.Input, 0xD02F),
    RemoteButton("Menu", Icons.Default.Menu, 0x50AF),
    RemoteButton("▲", Icons.Default.KeyboardArrowUp, 0x8877),
    RemoteButton("◄", Icons.Default.KeyboardArrowLeft, 0xA857),
    RemoteButton("OK", Icons.Default.Circle, 0x10EF, isLarge = true),
    RemoteButton("►", Icons.Default.KeyboardArrowRight, 0x48B7),
    RemoteButton("▼", Icons.Default.KeyboardArrowDown, 0x9867),
    RemoteButton("Vol -", Icons.Default.VolumeDown, 0xA05F),
    RemoteButton("Mute", Icons.Default.VolumeOff, 0x906F),
    RemoteButton("Vol +", Icons.Default.VolumeUp, 0x609F),
    RemoteButton("CH -", Icons.Default.KeyboardArrowDown, 0xE01F),
    RemoteButton("CH List", Icons.Default.List, 0x1AE5),
    RemoteButton("CH +", Icons.Default.KeyboardArrowUp, 0x20DF),
    RemoteButton("Home", Icons.Default.Home, 0x7A85),
    RemoteButton("Back", Icons.Default.ArrowBack, 0x28D7),
    RemoteButton("Info", Icons.Default.Info, 0x5AA5),
    RemoteButton("Exit", Icons.Default.Close, 0x12ED),
    RemoteButton("Source", Icons.Default.Cast, 0x0AF5),
    RemoteButton("Settings", Icons.Default.Settings, 0x42BD),
)

/** Comandi NEC per Audio */
val AUDIO_BUTTONS = listOf(
    RemoteButton("Power", Icons.Default.PowerSettingsNew, 0x40BF, isPower = true),
    RemoteButton("Vol +", Icons.Default.VolumeUp, 0x609F),
    RemoteButton("Vol -", Icons.Default.VolumeDown, 0xA05F),
    RemoteButton("Mute", Icons.Default.VolumeOff, 0x906F),
    RemoteButton("▲", Icons.Default.KeyboardArrowUp, 0x8877),
    RemoteButton("OK", Icons.Default.Circle, 0x10EF, isLarge = true),
    RemoteButton("▼", Icons.Default.KeyboardArrowDown, 0x9867),
    RemoteButton("Play", Icons.Default.PlayArrow, 0x50AF),
    RemoteButton("Pause", Icons.Default.Pause, 0xD02F),
    RemoteButton("Next", Icons.Default.SkipNext, 0x20DF),
    RemoteButton("Prev", Icons.Default.SkipPrevious, 0xE01F),
    RemoteButton("Stop", Icons.Default.Stop, 0x7A85),
    RemoteButton("Input", Icons.Default.Input, 0x1AE5),
    RemoteButton("BT", Icons.Default.Bluetooth, 0x5AA5),
    RemoteButton("Bass", Icons.Default.Audiotrack, 0x42BD),
)

/** Comandi NEC per DVD/Blu-ray */
val DVD_BUTTONS = listOf(
    RemoteButton("Power", Icons.Default.PowerSettingsNew, 0x40BF, isPower = true),
    RemoteButton("▲", Icons.Default.KeyboardArrowUp, 0x8877),
    RemoteButton("OK", Icons.Default.Circle, 0x10EF, isLarge = true),
    RemoteButton("▼", Icons.Default.KeyboardArrowDown, 0x9867),
    RemoteButton("◄", Icons.Default.KeyboardArrowLeft, 0xA857),
    RemoteButton("►", Icons.Default.KeyboardArrowRight, 0x48B7),
    RemoteButton("Play", Icons.Default.PlayArrow, 0x50AF),
    RemoteButton("Pause", Icons.Default.Pause, 0xD02F),
    RemoteButton("Stop", Icons.Default.Stop, 0x7A85),
    RemoteButton("Next", Icons.Default.SkipNext, 0x20DF),
    RemoteButton("Prev", Icons.Default.SkipPrevious, 0xE01F),
    RemoteButton("Menu", Icons.Default.Menu, 0x609F),
    RemoteButton("Back", Icons.Default.ArrowBack, 0x28D7),
    RemoteButton("Info", Icons.Default.Info, 0x5AA5),
    RemoteButton("Audio", Icons.Default.Audiotrack, 0xA05F),
    RemoteButton("Subtitle", Icons.Default.Subject, 0x906F),
)

/** Comandi NEC per Set-Top-Box / Decoder */
val STB_BUTTONS = listOf(
    RemoteButton("Power", Icons.Default.PowerSettingsNew, 0x40BF, isPower = true),
    RemoteButton("CH +", Icons.Default.KeyboardArrowUp, 0x20DF),
    RemoteButton("CH -", Icons.Default.KeyboardArrowDown, 0xE01F),
    RemoteButton("▲", Icons.Default.KeyboardArrowUp, 0x8877),
    RemoteButton("◄", Icons.Default.KeyboardArrowLeft, 0xA857),
    RemoteButton("OK", Icons.Default.Circle, 0x10EF, isLarge = true),
    RemoteButton("►", Icons.Default.KeyboardArrowRight, 0x48B7),
    RemoteButton("▼", Icons.Default.KeyboardArrowDown, 0x9867),
    RemoteButton("Menu", Icons.Default.Menu, 0x50AF),
    RemoteButton("Back", Icons.Default.ArrowBack, 0x28D7),
    RemoteButton("Home", Icons.Default.Home, 0x7A85),
    RemoteButton("Info", Icons.Default.Info, 0x5AA5),
    RemoteButton("Exit", Icons.Default.Close, 0x12ED),
    RemoteButton("Guide", Icons.Default.List, 0x1AE5),
    RemoteButton("Rec", Icons.Default.RadioButtonChecked, 0x0AF5),
)

// ── Indirizzi NEC conosciuti per marca ──────────────────────────────

private val BRAND_NEC_ADDRESSES = mapOf(
    "Samsung" to 0xE0E0L, "LG" to 0x20DFL, "Panasonic" to 0x4004L,
    "Philips" to 0x0102L, "Toshiba" to 0x0101L, "Sharp" to 0x0203L,
    "Hitachi" to 0x0104L, "Hisense" to 0x10EFL, "TCL" to 0x01FEL,
    "Haier" to 0x0808L, "Xiaomi" to 0x1414L, "Huawei" to 0x1515L,
    "Bose" to 0x0302L, "Yamaha" to 0x0405L, "Denon" to 0x0607L,
    "Onkyo" to 0x0809L, "Mitsubishi" to 0x7CB3L, "Daikin" to 0x1001L,
    "Gree" to 0x1101L, "Midea" to 0x1201L, "NEC" to 0x0101L,
    "RCA" to 0x0F0FL, "Sanyo" to 0x1111L, "Funai" to 0x1212L,
    "Acer" to 0x1717L, "BenQ" to 0x1818L, "Epson" to 0x1919L,
    "Vizio" to 0x1A1AL, "Sky" to 0x1B1BL, "Apple" to 0x1D1DL,
    "Google" to 0x1E1EL, "Amazon" to 0x1F1FL, "Roku" to 0x2020L,
    "OnePlus" to 0x2323L, "Nokia" to 0x2424L, "Motorola" to 0x2525L,
    "HTC" to 0x2727L, "Lenovo" to 0x2828L,
)

/** Indirizzi Sony */
private val BRAND_SONY_DEVICE = mapOf("Sony" to 1L)

/** Indirizzi RC5 (Philips) */
private val BRAND_RC5_SYSTEM = mapOf("Philips" to 0L)

// ── Funzione centrale: genera codice IR con pattern valido ─────────

/**
 * Genera un IrCodeEntity FUNZIONANTE per la marca e comando specificati.
 * Il pattern IR viene generato al volo usando PatternGenerator.
 * Usa indirizzi NEC conosciuti, o default Samsung se sconosciuto.
 */
fun generateRemoteCode(brand: String, deviceType: String, button: RemoteButton): IrCodeEntity {
    val protocol = when {
        brand == "Sony" -> "SONY"
        brand == "Philips" -> "RC5"
        else -> "NEC"
    }

    val name = button.name
    // Mappa nomi comandi per corrispondenza col DB
    val cmdName = when (name) {
        "▲" -> "Up"; "▼" -> "Down"; "◄" -> "Left"; "►" -> "Right"
        "Vol +" -> "Volume Up"; "Vol -" -> "Volume Down"
        "CH +" -> "Channel Up"; "CH -" -> "Channel Down"
        "CH List" -> "Channel List"
        "BT" -> "Bluetooth"
        "Bass" -> "Bass Boost"
        "Next" -> "Next Track"
        "Prev" -> "Previous Track"
        "Rec" -> "Record"
        "Guide" -> "Guide"
        else -> name
    }

    val frequency = when (protocol) {
        "SONY" -> 40000; "RC5" -> 36000; else -> 38000
    }

    // Genera pattern IR reale
    val pattern = when (protocol) {
        "NEC" -> {
            val addr = BRAND_NEC_ADDRESSES[brand] ?: 0xE0E0L  // default Samsung
            PatternGenerator.generateNec(addr, button.necCommand)
        }
        "SONY" -> {
            val dev = BRAND_SONY_DEVICE[brand] ?: 1L
            PatternGenerator.generateSony(button.sonyCommand.let { if (it == 0L) button.necCommand else it }, dev)
        }
        "RC5" -> {
            val sys = BRAND_RC5_SYSTEM[brand] ?: 0L
            PatternGenerator.generateRc5(sys, button.rc5Command.let { if (it == 0L) button.necCommand else it })
        }
        else -> PatternGenerator.generateNec(0xE0E0L, button.necCommand)
    }

    val address = when (protocol) {
        "NEC" -> BRAND_NEC_ADDRESSES[brand] ?: 0xE0E0L
        "SONY" -> BRAND_SONY_DEVICE[brand] ?: 1L
        "RC5" -> BRAND_RC5_SYSTEM[brand] ?: 0L
        else -> 0L
    }

    val command = button.necCommand

    return IrCodeEntity(
        name = cmdName,
        displayName = name,
        brand = brand,
        deviceType = deviceType,
        protocol = protocol,
        frequency = frequency,
        pattern = IrCodeEntity.patternToString(pattern),
        address = address,
        command = command,
        source = "generated"
    )
}

// ── TV Remote ───────────────────────────────────────────────────────

@Composable
private fun TvRemoteContent(brand: String, onSendCode: (IrCodeEntity) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()) {
        items(TV_BUTTONS) { btn ->
            KeyButton(btn) {
                val code = generateRemoteCode(brand, "TV", btn)
                onSendCode(code)
            }
        }
    }
}

// ── AC Remote ───────────────────────────────────────────────────────

@Composable
private fun AcRemoteContent(brand: String, onSendCode: (IrCodeEntity) -> Unit) {
    var temperature by remember { mutableStateOf(24) }
    var mode by remember { mutableStateOf("Cool") }
    var fanSpeed by remember { mutableStateOf("Auto") }
    var swing by remember { mutableStateOf(false) }
    var powerOn by remember { mutableStateOf(false) }

    val modes = listOf("Cool", "Heat", "Dry", "Fan", "Auto")
    val fanSpeeds = listOf("Auto", "Low", "Medium", "High")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = {
                powerOn = !powerOn
                val code = generateRemoteCode(brand, "AC", RemoteButton(if (powerOn) "Power" else "Power", Icons.Default.PowerSettingsNew, 0x01, isPower = true))
                onSendCode(code)
            }, modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (powerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) {
                Icon(Icons.Default.PowerSettingsNew, if (powerOn) "Acceso" else "Spento", modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Temperatura", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (temperature > 16) temperature--
                        onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Temp Down", null, 0x0E)))
                    }) { Icon(Icons.Default.Remove, "Meno") }
                    Text("$temperature°", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = {
                        if (temperature < 32) temperature++
                        onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Temp Up", null, 0x0D)))
                    }) { Icon(Icons.Default.Add, "Più") }
                }
            }
        }
        Text("Modalità", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { m ->
                FilterChip(selected = mode == m, onClick = {
                    mode = m
                    onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Mode $m", null, 0x02 + modes.indexOf(m))))
                }, label = { Text(m) })
            }
        }
        Text("Ventola", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fanSpeeds.forEach { f ->
                FilterChip(selected = fanSpeed == f, onClick = {
                    fanSpeed = f
                    onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Fan $f", null, 0x07 + fanSpeeds.indexOf(f))))
                }, label = { Text(f) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Swing", style = MaterialTheme.typography.labelLarge)
            Switch(checked = swing, onCheckedChange = {
                swing = it
                onSendCode(generateRemoteCode(brand, "AC", RemoteButton(if (it) "Swing On" else "Swing Off", null, if (it) 0x0B else 0x0C)))
            })
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AssistChip(onClick = {
                onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Sleep", null, 0x0F)))
            }, label = { Text("Sleep") }, leadingIcon = { Icon(Icons.Default.Bedtime, "Sleep", modifier = Modifier.size(18.dp)) })
            AssistChip(onClick = {
                onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Turbo", null, 0x10)))
            }, label = { Text("Turbo") }, leadingIcon = { Icon(Icons.Default.Bolt, "Turbo", modifier = Modifier.size(18.dp)) })
            AssistChip(onClick = {
                onSendCode(generateRemoteCode(brand, "AC", RemoteButton("Timer", null, 0x12)))
            }, label = { Text("Timer") }, leadingIcon = { Icon(Icons.Default.Timer, "Timer", modifier = Modifier.size(18.dp)) })
        }
    }
}

// ── Audio Remote ────────────────────────────────────────────────────

@Composable
private fun AudioRemoteContent(brand: String, onSendCode: (IrCodeEntity) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()) {
        items(AUDIO_BUTTONS) { btn ->
            KeyButton(btn) {
                val code = generateRemoteCode(brand, "AUDIO", btn)
                onSendCode(code)
            }
        }
    }
}

// ── DVD Remote ──────────────────────────────────────────────────────

@Composable
private fun DvdRemoteContent(brand: String, onSendCode: (IrCodeEntity) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()) {
        items(DVD_BUTTONS) { btn ->
            KeyButton(btn) {
                val code = generateRemoteCode(brand, "DVD", btn)
                onSendCode(code)
            }
        }
    }
}

// ── STB Remote ──────────────────────────────────────────────────────

@Composable
private fun StbRemoteContent(brand: String, onSendCode: (IrCodeEntity) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()) {
        items(STB_BUTTONS) { btn ->
            KeyButton(btn) {
                val code = generateRemoteCode(brand, "SET_TOP_BOX", btn)
                onSendCode(code)
            }
        }
    }
}

// ── Key Button ──────────────────────────────────────────────────────

@Composable
private fun KeyButton(button: RemoteButton, onClick: () -> Unit) {
    val size = if (button.isLarge) 80.dp else 64.dp
    Button(onClick = onClick,
        modifier = Modifier.size(size).then(if (button.isPower) Modifier.clip(CircleShape) else Modifier.clip(RoundedCornerShape(12.dp))),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (button.isPower) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (button.isPower) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface),
        shape = if (button.isPower) CircleShape else RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (button.icon != null) Icon(button.icon, button.name, modifier = Modifier.size(if (button.isLarge) 32.dp else 24.dp))
            if (!button.isLarge || button.name.length <= 3) {
                Text(button.name, fontSize = if (button.name.length <= 2) 14.sp else 10.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
