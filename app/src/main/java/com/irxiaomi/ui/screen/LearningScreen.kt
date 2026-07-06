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
import com.irxiaomi.learning.AudioIrLearner

/**
 * Schermata per apprendimento nuovi codici IR tramite jack audio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(
    onBack: () -> Unit,
    learnerState: AudioIrLearner.LearningState = AudioIrLearner.LearningState(),
    onStartLearning: () -> Unit = {},
    onStopLearning: () -> Unit = {},
    onSaveCode: (String, String) -> Unit = { _, _ -> },
    onTestCode: () -> Unit = {}
) {
    var codeName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apprendimento IR") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Istruzioni
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column {
                        Text("Come funziona", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "1. Collega un fototransistor IR al jack audio\n" +
                            "2. Punta il telecomando verso il microfono\n" +
                            "3. Premi 'Impara' e poi il tasto sul telecomando\n" +
                            "4. L'app decodifica il segnale e lo salva",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Stato
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Indicatore visivo
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(40.dp),
                        color = if (learnerState.signalDetected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (learnerState.isRecording) 8.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (learnerState.isRecording) Icons.Default.Mic else Icons.Default.Sensors,
                                null,
                                modifier = Modifier.size(40.dp),
                                tint = if (learnerState.signalDetected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(learnerState.message, style = MaterialTheme.typography.bodyMedium)

                    if (learnerState.isRecording) {
                        LinearProgressIndicator(
                            progress = { learnerState.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }

            // Pulsanti azione
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!learnerState.isRecording) {
                    Button(
                        onClick = onStartLearning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Impara")
                    }
                } else {
                    Button(
                        onClick = onStopLearning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ferma")
                    }
                }

                OutlinedButton(
                    onClick = onTestCode,
                    enabled = learnerState.rawSignal != null
                ) {
                    Icon(Icons.Default.PlayCircle, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Test")
                }
            }

            // Risultato decodifica
            if (learnerState.decodedDevices.isNotEmpty()) {
                val signal = learnerState.decodedDevices.first()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Segnale rilevato", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        InfoRow("Protocollo", signal.protocol)
                        InfoRow("Frequenza", "${signal.frequency / 1000} kHz")
                        if (signal.address != null) InfoRow("Indirizzo", "0x${signal.address.toInt().toHex()}")
                        if (signal.command != null) InfoRow("Comando", "0x${signal.command.toInt().toHex()}")
                        InfoRow("Qualità", "${(signal.quality * 100).toInt()}%")
                        InfoRow("Pattern", "${signal.rawPattern.size} burst")

                        // Input nome e salva
                        OutlinedTextField(
                            value = codeName,
                            onValueChange = { codeName = it },
                            label = { Text("Nome comando (es. Power)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = { onSaveCode(codeName, signal.protocol); codeName = "" },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = codeName.isNotBlank()
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Salva nel Database")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun Int.toHex(): String = String.format("%04X", this)
