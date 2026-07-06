package com.irxiaomi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.irxiaomi.db.IrCodeEntity
import com.irxiaomi.learning.AudioIrLearner
import com.irxiaomi.ui.screen.*
import com.irxiaomi.ui.theme.IRXiaomiTheme
import kotlinx.coroutines.launch

/**
 * Activity principale con navigazione Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IRXiaomiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    // Colleziona stati
    val databaseSize by viewModel.databaseSize.collectAsState()
    val irManagerInfo by viewModel.irManagerInfo.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val learnerState by viewModel.learner.state.collectAsState(initial = AudioIrLearner.LearningState())

    val isSeeding by viewModel.isSeeding.collectAsState()
    val seedCount by viewModel.seedProgress.collectAsState()
    val irReady by viewModel.irReady.collectAsState()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                databaseSize = databaseSize,
                isSeeding = isSeeding,
                seedCount = seedCount,
                irReady = irReady,
                onNavigateToRemote = { navController.navigate("remote") },
                onNavigateToDatabase = { navController.navigate("database") },
                onNavigateToLearning = { navController.navigate("learning") },
                onNavigateToClone = { navController.navigate("clone") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("remote") {
            val scope = rememberCoroutineScope()
            var brands by remember { mutableStateOf<List<String>>(emptyList()) }
            var selectedBrand by remember { mutableStateOf("Samsung") }

            // Carica marche dal database al primo avvio
            LaunchedEffect(Unit) {
                val allBrands = viewModel.codeDao.getAllBrands()
                brands = allBrands.ifEmpty {
                    listOf("Samsung", "LG", "Sony", "Panasonic", "Philips", "Toshiba",
                        "Hisense", "TCL", "Sharp", "Hitachi", "Daikin", "Mitsubishi",
                        "Haier", "Gree", "Midea", "Xiaomi", "Huawei", "Bose",
                        "Yamaha", "Denon", "Onkyo", "Roku")
                }
                if (selectedBrand !in brands) selectedBrand = brands.first()
            }

            RemoteScreen(
                currentBrand = selectedBrand,
                availableBrands = brands,
                onBrandChange = { selectedBrand = it },
                onBack = { navController.popBackStack() },
                onSendCode = { code -> viewModel.sendCode(code) }
            )
        }

        composable("database") {
            DatabaseScreen(
                onBack = { navController.popBackStack() },
                codes = searchResults,
                totalCodes = databaseSize,
                onSearch = { query -> viewModel.searchCodes(query) },
                onCodeClick = { code -> viewModel.sendCode(code) },
                onImport = { viewModel.importLirc() },
                onExport = { viewModel.exportDatabase() }
            )
        }

        composable("learning") {
            val scope = rememberCoroutineScope()
            LearningScreen(
                onBack = { navController.popBackStack() },
                learnerState = learnerState,
                onStartLearning = { viewModel.learner.startLearning() },
                onStopLearning = { viewModel.learner.stopLearning() },
                onSaveCode = { name, protocol ->
                    val signal = learnerState.decodedDevices.firstOrNull()
                    if (signal != null) {
                        val entity = IrCodeEntity(
                            name = name,
                            displayName = name,
                            brand = "Appreso",
                            deviceType = "OTHER",
                            protocol = protocol,
                            frequency = signal.frequency,
                            pattern = IrCodeEntity.patternToString(signal.rawPattern),
                            address = signal.address,
                            command = signal.command,
                            source = "learned",
                            notes = "Appreso via jack audio"
                        )
                        scope.launch {
                            viewModel.codeDao.insert(entity)
                            viewModel.refreshDatabaseSize()
                        }
                    }
                },
                onTestCode = {
                    val signal = learnerState.decodedDevices.firstOrNull()
                    if (signal != null) {
                        viewModel.transmitRaw(signal.frequency, signal.rawPattern)
                    }
                }
            )
        }

        composable("clone") {
            val scope = rememberCoroutineScope()
            CloneScreen(
                onBack = { navController.popBackStack() },
                onFindMissing = {
                    viewModel.findMissingCodes("Samsung", "TV")
                },
                onCloneCode = { src, tgt ->
                    viewModel.cloneCodes(src, tgt, "TV")
                },
                onGenerateVariants = {
                    scope.launch {
                        val codes = viewModel.codeDao.getByBrandAndDevice("Samsung", "TV")
                        val first = codes.firstOrNull()
                        if (first != null) {
                            val variants = viewModel.variantGenerator.generateAddressShifts(first)
                            viewModel.codeDao.insertAll(variants)
                            viewModel.refreshDatabaseSize()
                        }
                    }
                },
                onExportCodes = { viewModel.exportDatabase() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                irManagerInfo = irManagerInfo,
                databaseSize = databaseSize,
                onImportLirc = { viewModel.importLirc() },
                onSyncServer = { viewModel.syncRemote() },
                onClearDatabase = { viewModel.clearDatabase() },
                onExportDatabase = { viewModel.exportDatabase() },
                onSeedDatabase = { viewModel.seedDatabase() },
                isSeeding = isSeeding,
                seedCount = seedCount
            )
        }
    }
}
