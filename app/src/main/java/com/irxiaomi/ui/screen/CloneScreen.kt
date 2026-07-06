package com.irxiaomi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Schermata di clonazione e generazione varianti codici IR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneScreen(
    onBack: () -> Unit,
    onFindMissing: () -> Unit = {},
    onCloneCode: (String, String) -> Unit = { _, _ -> },
    onGenerateVariants: () -> Unit = {},
    onExportCodes: () -> Unit = {}
) {
    var sourceBrand by remember { mutableStateOf("") }
    var targetBrand by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf("TV") }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Clona", "Varianti", "Mancanti")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clonazione Codici") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Indietro") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> CloneTab(sourceBrand, targetBrand, deviceType, onCloneCode, onExportCodes)
                1 -> VariantsTab(onGenerateVariants)
                2 -> MissingTab(sourceBrand, deviceType, onFindMissing)
            }
        }
    }
}

@Composable
private fun CloneTab(
    sourceBrand: String, targetBrand: String, deviceType: String,
    onCloneCode: (String, String) -> Unit,
    onExport: () -> Unit
) {
    var srcBrand by remember { mutableStateOf(sourceBrand) }
    var tgtBrand by remember { mutableStateOf(targetBrand) }
    var devType by remember { mutableStateOf(deviceType) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Clona codici da un brand all'altro", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                OutlinedTextField(value = srcBrand, onValueChange = { srcBrand = it },
                    label = { Text("Brand sorgente") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Tv, null) })

                OutlinedTextField(value = tgtBrand, onValueChange = { tgtBrand = it },
                    label = { Text("Brand destinazione") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) })

                OutlinedTextField(value = devType, onValueChange = { devType = it },
                    label = { Text("Tipo dispositivo") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.DevicesOther, null) })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCloneCode(srcBrand, tgtBrand) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clona")
                    }
                    OutlinedButton(onClick = onExport) {
                        Icon(Icons.Default.FileDownload, null)
                    }
                }
            }
        }

        // Lista codici sorgente disponibili (placeholder)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Codici sorgente ($srcBrand - $devType)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("Nessun codice trovato. Usa 'Trova mancanti' per generare.", 
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VariantsTab(onGenerateVariants: () -> Unit) {
    var protocol by remember { mutableStateOf("NEC") }
    var address by remember { mutableStateOf("0xE0E0") }
    var numVariants by remember { mutableStateOf(4) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Genera varianti automatiche", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Crea varianti di codici cambiando indirizzo, protocollo o comandi standard.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(value = protocol, onValueChange = { protocol = it },
                    label = { Text("Protocollo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = address, onValueChange = { address = it },
                    label = { Text("Indirizzo base (es. 0xE0E0)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Numero varianti:", style = MaterialTheme.typography.bodyMedium)
                    var sliderValue by remember { mutableStateOf(numVariants.toFloat()) }
                    Slider(value = sliderValue, onValueChange = { sliderValue = it; numVariants = it.toInt() },
                        valueRange = 1f..20f, steps = 19, modifier = Modifier.weight(1f))
                    Text("$numVariants")
                }

                Button(onClick = onGenerateVariants, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Genera Varianti")
                }
            }
        }
    }
}

@Composable
private fun MissingTab(brand: String, deviceType: String, onFindMissing: () -> Unit) {
    var selectedBrand by remember { mutableStateOf(brand) }
    var selectedDevice by remember { mutableStateOf(deviceType) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Trova codici mancanti", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Analizza il database e genera i codici IR mancanti per un brand/tipo dispositivo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(value = selectedBrand, onValueChange = { selectedBrand = it },
                    label = { Text("Brand") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) })

                OutlinedTextField(value = selectedDevice, onValueChange = { selectedDevice = it },
                    label = { Text("Tipo dispositivo") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.DevicesOther, null) })

                Button(onClick = onFindMissing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FindReplace, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Trova Mancanti")
                }
            }
        }

        // Risultati (placeholder)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Risultati analisi", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("Esegui 'Trova Mancanti' per vedere i risultati.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
