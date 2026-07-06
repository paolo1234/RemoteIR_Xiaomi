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
import com.irxiaomi.model.DeviceType

/**
 * Schermata telecomando virtuale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    deviceType: DeviceType = DeviceType.TV,
    currentBrand: String = "Samsung",
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
                    IconButton(onClick = { }) { Icon(Icons.Default.SwapHoriz, "Cambia dispositivo") }
                    IconButton(onClick = { }) { Icon(Icons.Default.FavoriteBorder, "Preferito") }
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
            DeviceSelectorBar(selectedDeviceType, selectedBrand, { selectedDeviceType = it }, { selectedBrand = it })
            when (selectedDeviceType) {
                DeviceType.AC -> AcRemoteContent(onSendCode)
                else -> TvRemoteContent(onSendCode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectorBar(
    selectedType: DeviceType, selectedBrand: String,
    onTypeChange: (DeviceType) -> Unit, onBrandChange: (String) -> Unit
) {
    var showDeviceMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(expanded = showDeviceMenu, onExpandedChange = { showDeviceMenu = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(value = selectedType.displayName, onValueChange = {},
                    readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDeviceMenu) },
                    modifier = Modifier.menuAnchor(), textStyle = MaterialTheme.typography.bodyMedium, singleLine = true)
                ExposedDropdownMenu(expanded = showDeviceMenu, onDismissRequest = { showDeviceMenu = false }) {
                    DeviceType.entries.forEach { type ->
                        DropdownMenuItem(text = { Text(type.displayName) }, onClick = { onTypeChange(type); showDeviceMenu = false })
                    }
                }
            }
            Text(selectedBrand, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- TV Remote ----------

data class RemoteButton(val name: String, val icon: ImageVector? = null, val command: Long, val isPower: Boolean = false, val isLarge: Boolean = false)

@Composable
private fun TvRemoteContent(onSendCode: (IrCodeEntity) -> Unit) {
    val buttons = listOf(
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

    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()) {
        items(buttons) { btn -> KeyButton(btn) { onSendCode(sampleNecCode(btn.name, btn.command)) } }
    }
}

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

// ---------- AC Remote ----------

@Composable
private fun AcRemoteContent(onSendCode: (IrCodeEntity) -> Unit) {
    var temperature by remember { mutableStateOf(24) }
    var mode by remember { mutableStateOf("Cool") }
    var fanSpeed by remember { mutableStateOf("Auto") }
    var swing by remember { mutableStateOf(false) }
    var powerOn by remember { mutableStateOf(false) }

    val modes = listOf("Cool", "Heat", "Dry", "Fan", "Auto")
    val fanSpeeds = listOf("Auto", "Low", "Medium", "High")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { powerOn = !powerOn }, modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (powerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) {
                Icon(Icons.Default.PowerSettingsNew, if (powerOn) "Acceso" else "Spento", modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Temperatura", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (temperature > 16) temperature-- }) { Icon(Icons.Default.Remove, "Meno") }
                    Text("$temperature°", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { if (temperature < 32) temperature++ }) { Icon(Icons.Default.Add, "Più") }
                }
            }
        }
        Text("Modalità", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
        }
        Text("Ventola", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fanSpeeds.forEach { f -> FilterChip(selected = fanSpeed == f, onClick = { fanSpeed = f }, label = { Text(f) }) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Swing", style = MaterialTheme.typography.labelLarge)
            Switch(checked = swing, onCheckedChange = { swing = it })
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AssistChip(onClick = { }) { Icon(Icons.Default.Bedtime, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Sleep") }
            AssistChip(onClick = { }) { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Turbo") }
            AssistChip(onClick = { }) { Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Timer") }
        }
    }
}

private fun sampleNecCode(name: String, command: Long): IrCodeEntity = IrCodeEntity(
    name = name, displayName = name, brand = "Samsung", deviceType = "TV",
    protocol = "NEC", frequency = 38000, address = 0xE0E0, command = command,
    category = "Samsung-TV", tags = "Samsung,TV,NEC", source = "sample"
)
