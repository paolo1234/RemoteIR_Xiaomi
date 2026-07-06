package com.irxiaomi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Schermata impostazioni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    irManagerInfo: Map<String, Any> = emptyMap(),
    databaseSize: Int = 0,
    onImportLirc: () -> Unit = {},
    onSyncServer: () -> Unit = {},
    onClearDatabase: () -> Unit = {},
    onExportDatabase: () -> Unit = {},
    onSeedDatabase: () -> Unit = {},
    isSeeding: Boolean = false,
    seedCount: Int = 0
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Indietro") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Sezione: Dispositivo IR
            SettingsSectionHeader("Dispositivo IR")
            SettingsInfoItem("Driver", irManagerInfo["name"]?.toString() ?: "ConsumerIrManager")
            SettingsInfoItem("Supportato", irManagerInfo["supported"]?.toString() ?: "sconosciuto")

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Sezione: Database
            SettingsSectionHeader("Database")
            SettingsInfoItem("Codici totali", "$databaseSize")
            SettingsActionItem("Importa database LIRC", Icons.Default.FileUpload, onImportLirc)
            SettingsActionItem("Genera database seed (5000+ codici)", Icons.Default.AutoAwesome, onSeedDatabase)
            if (isSeeding) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text("Generazione in corso...", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (seedCount > 0) {
                Text("$seedCount codici generati!", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
            SettingsActionItem("Esporta database", Icons.Default.FileDownload, onExportDatabase)
            SettingsActionItem("Sincronizza server", Icons.Default.Sync, onSyncServer)

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Sezione: Apprendimento
            SettingsSectionHeader("Apprendimento")
            SettingsInfoItem("Metodo", "Jack audio (fototransistor TSOP)")
            SettingsInfoItem("Sample rate", "192 kHz")
            SettingsInfoItem("Protocolli supportati", "NEC, Samsung, Sony, RC5, RC6")

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Sezione: Info
            SettingsSectionHeader("Informazioni")
            SettingsInfoItem("Versione app", "1.0.0")
            SettingsInfoItem("API IR", "ConsumerIrManager + Xiaomi MIUI + Sysfs")
            SettingsInfoItem("Formato DB", "Room (SQLite) + Server remoto")

            Spacer(Modifier.height(16.dp))

            // Pulsante pericoloso
            OutlinedButton(
                onClick = onClearDatabase,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Cancella tutto il database")
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text(
                "IRXiaomi v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsActionItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
