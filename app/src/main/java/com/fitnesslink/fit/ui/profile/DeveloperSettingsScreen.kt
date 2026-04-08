package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiConfiguration
import com.fitnesslink.fit.network.ApiEnvironment
import com.fitnesslink.fit.network.ApiService
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.SyncStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedEnv by remember { mutableStateOf(ApiConfiguration.environment) }
    var ngrokUrl by remember { mutableStateOf(ApiConfiguration.ngrokUrl) }
    var connectionStatus by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    val isConnected by NetworkMonitor.isConnected.collectAsState()
    val syncStatus by SyncManager.status.collectAsState()
    val lastSync by SyncManager.lastSyncDate.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Environment selection
            Text("API Environment", style = MaterialTheme.typography.titleMedium)
            ApiEnvironment.entries.forEach { env ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = selectedEnv == env,
                        onClick = {
                            selectedEnv = env
                            ApiConfiguration.setEnvironment(env)
                        }
                    )
                    Text(
                        env.name,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // NGROK URL
            if (selectedEnv == ApiEnvironment.NGROK) {
                OutlinedTextField(
                    value = ngrokUrl,
                    onValueChange = { ngrokUrl = it },
                    label = { Text("NGROK URL") },
                    placeholder = { Text("https://abc123.ngrok-free.app") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { ApiConfiguration.setNgrokUrl(ngrokUrl) },
                    enabled = ngrokUrl.isNotBlank()
                ) {
                    Text("Save NGROK URL")
                }
            }

            HorizontalDivider()

            // Connection test
            Text("Connection Test", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    isTesting = true
                    connectionStatus = ""
                    scope.launch {
                        try {
                            ApiClient.userApi.getMe()
                            connectionStatus = "Success - Connected to server"
                        } catch (e: Exception) {
                            connectionStatus = "Failed: ${e.message}"
                        }
                        isTesting = false
                    }
                },
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Test Connection")
            }
            if (connectionStatus.isNotEmpty()) {
                Text(
                    connectionStatus,
                    color = if ("Success" in connectionStatus) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // Status
            Text("Status", style = MaterialTheme.typography.titleMedium)
            StatusRow("Network", if (isConnected) "Connected" else "Offline")
            StatusRow("Sync", when (syncStatus) {
                SyncStatus.IDLE -> "Idle"
                SyncStatus.SYNCING -> "Syncing..."
                SyncStatus.ERROR -> "Error"
                SyncStatus.COMPLETE -> "Complete"
            })
            StatusRow("Last Sync", lastSync?.let { dateFormat.format(it) } ?: "Never")
            StatusRow("Core API", ApiConfiguration.baseUrl(ApiService.CORE))
            StatusRow("Nutrition API", ApiConfiguration.baseUrl(ApiService.NUTRITION))

            HorizontalDivider()

            // Actions
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { scope.launch { SyncManager.performFullSync() } }) {
                Text("Force Sync Now")
            }
            OutlinedButton(
                onClick = { DatabaseManager.clearUserData() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear Local Cache")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
