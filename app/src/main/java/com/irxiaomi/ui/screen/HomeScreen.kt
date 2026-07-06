package com.irxiaomi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Schermata principale: dashboard con accesso rapido a tutte le funzionalità.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    databaseSize: Int = 0,
    isSeeding: Boolean = false,
    seedCount: Int = 0,
    irReady: Boolean = true,
    onNavigateToRemote: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    onNavigateToLearning: () -> Unit,
    onNavigateToClone: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IRXiaomi", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Telecomando Universale IR",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Controlla tutti i tuoi dispositivi con un tap",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            DashboardCard(
                title = "Telecomando",
                subtitle = "Controlla TV, AC, decoder e altro",
                icon = Icons.Default.Tv,
                onClick = onNavigateToRemote
            )
            DashboardCard(
                title = "Database Codici",
                subtitle = "Sfoglia e cerca tra migliaia di codici IR",
                icon = Icons.Default.Storage,
                onClick = onNavigateToDatabase
            )
            DashboardCard(
                title = "Apprendimento",
                subtitle = "Impara nuovi codici dal tuo telecomando",
                icon = Icons.Default.Sensors,
                onClick = onNavigateToLearning
            )
            DashboardCard(
                title = "Clonazione",
                subtitle = "Clona, genera varianti e trova codici mancanti",
                icon = Icons.Default.ContentCopy,
                onClick = onNavigateToClone
            )

            Spacer(modifier = Modifier.weight(1f))

            // Status footer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Status IR", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (irReady) "Pronto" else "Non disponibile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (irReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("DB Codici", style = MaterialTheme.typography.labelLarge)
                        if (isSeeding) {
                            Text("Generazione... $seedCount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary)
                        } else {
                            Text("$databaseSize comandi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
