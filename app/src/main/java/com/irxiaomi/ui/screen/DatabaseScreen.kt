package com.irxiaomi.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irxiaomi.db.IrCodeEntity

/**
 * Schermata di esplorazione del database codici IR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    onBack: () -> Unit,
    codes: List<IrCodeEntity> = emptyList(),
    totalCodes: Int = 0,
    onSearch: (String) -> Unit = {},
    onCodeClick: (IrCodeEntity) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showFilter by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Database Codici") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { showFilter = !showFilter }) { Icon(Icons.Default.FilterList, "Filtri") }
                    IconButton(onClick = onImport) { Icon(Icons.Default.FileUpload, "Importa") }
                    IconButton(onClick = onExport) { Icon(Icons.Default.FileDownload, "Esporta") }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Barra ricerca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; onSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Cerca marca, modello, comando...") },
                leadingIcon = { Icon(Icons.Default.Search, "Cerca") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; onSearch("") }) {
                            Icon(Icons.Default.Clear, "Cancella")
                        }
                    }
                },
                singleLine = true
            )

            // Statistiche
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$totalCodes codici", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (searchQuery.isNotEmpty()) {
                    Text("${codes.size} risultati", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Lista codici
            if (codes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storage, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("Nessun codice trovato", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Importa dal database LIRC o creane di nuovi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onImport) {
                            Icon(Icons.Default.FileUpload, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Importa codici")
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(codes, key = { it.id }) { code ->
                        CodeListItem(code = code, onClick = { onCodeClick(code) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeListItem(code: IrCodeEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Icona tipo
            Icon(
                when (code.deviceType) {
                    "TV" -> Icons.Default.Tv
                    "AC" -> Icons.Default.AcUnit
                    "AUDIO" -> Icons.Default.Speaker
                    else -> Icons.Default.DevicesOther
                },
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(code.displayName.ifEmpty { code.name }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(code.brand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(code.protocol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (code.isVerified) {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text("${code.frequency / 1000}kHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
