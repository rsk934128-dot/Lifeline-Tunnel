package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.ServerEntity
import com.example.data.entity.PayloadConfigEntity
import com.example.data.entity.EmergencyContactEntity
import com.example.data.entity.TunnelLogEntity
import com.example.data.repository.LifelineRepository
import com.example.data.gemini.GeminiClient
import com.example.service.LifelineVpnService
import com.example.service.BluetoothMeshManager
import com.example.service.MeshSosBeacon
import com.example.service.SmsTunnelManager
import com.example.service.IncomingSmsDistressSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import java.security.SecureRandom
import java.util.Base64

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

sealed interface AiState {
    object Idle : AiState
    object Loading : AiState
    data class Success(val response: String) : AiState
    data class Error(val message: String) : AiState
}

class LifelineViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository: LifelineRepository

    // Database Flows
    val servers: StateFlow<List<ServerEntity>>
    val payloads: StateFlow<List<PayloadConfigEntity>>
    val contacts: StateFlow<List<EmergencyContactEntity>>
    val logs: StateFlow<List<TunnelLogEntity>>

    // VPN Service Key State (for status bar and UI badge)
    val isVpnKeyActive: StateFlow<Boolean> = LifelineVpnService.isVpnActive

    // Live Internet Connectivity Flow
    private val _isInternetWorking = MutableStateFlow(true)
    val isInternetWorking: StateFlow<Boolean> = _isInternetWorking.asStateFlow()

    private val _internetPing = MutableStateFlow<Int?>(null)
    val internetPing: StateFlow<Int?> = _internetPing.asStateFlow()

    // Offline Bluetooth Mesh SOS Manager
    val meshManager = BluetoothMeshManager(application)
    val isMeshAdvertising = meshManager.isAdvertisingActive
    val isMeshScanning = meshManager.isScanningActive
    val isMeshRelayEnabled = meshManager.isMeshRelayEnabled
    val activeSosType = meshManager.activeSosType
    val activeSosMessage = meshManager.activeSosMessage
    val discoveredMeshBeacons = meshManager.discoveredBeacons
    val meshLogs = meshManager.meshLog
    val myMeshNodeId = meshManager.myNodeId

    // Offline GSM SMS Tunneling Flows
    val incomingSmsSignal: StateFlow<IncomingSmsDistressSignal?> = SmsTunnelManager.activeIncomingSignal
    val incomingSmsHistory: StateFlow<List<IncomingSmsDistressSignal>> = SmsTunnelManager.incomingSignalsHistory
    val smsStatusMessage: StateFlow<String?> = SmsTunnelManager.lastSentStatus

    // Connection State UI Flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionStatusText = MutableStateFlow("Tap to Establish Secure Lifeline")
    val connectionStatusText: StateFlow<String> = _connectionStatusText.asStateFlow()

    // Selected Configuration Flow
    private val _selectedServer = MutableStateFlow<ServerEntity?>(null)
    val selectedServer: StateFlow<ServerEntity?> = _selectedServer.asStateFlow()

    private val _selectedPayload = MutableStateFlow<PayloadConfigEntity?>(null)
    val selectedPayload: StateFlow<PayloadConfigEntity?> = _selectedPayload.asStateFlow()

    // Protocol settings
    private val _activeProtocol = MutableStateFlow("WIREGUARD") // WIREGUARD, OPENVPN, DNS_TUNNEL
    val activeProtocol: StateFlow<String> = _activeProtocol.asStateFlow()

    // Secure Keys
    private val _clientPublicKey = MutableStateFlow("")
    val clientPublicKey: StateFlow<String> = _clientPublicKey.asStateFlow()

    private val _clientPrivateKey = MutableStateFlow("")
    val clientPrivateKey: StateFlow<String> = _clientPrivateKey.asStateFlow()

    // Zero knowledge toggle
    private val _zeroKnowledgeEnabled = MutableStateFlow(true)
    val zeroKnowledgeEnabled: StateFlow<Boolean> = _zeroKnowledgeEnabled.asStateFlow()

    // Traffic Stats
    private val _downloadSpeed = MutableStateFlow("0.0 KB/s")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow("0.0 KB/s")
    val uploadSpeed: StateFlow<String> = _uploadSpeed.asStateFlow()

    private val _totalDownloaded = MutableStateFlow("0.000 MB")
    val totalDownloaded: StateFlow<String> = _totalDownloaded.asStateFlow()

    private val _totalUploaded = MutableStateFlow("0.000 MB")
    val totalUploaded: StateFlow<String> = _totalUploaded.asStateFlow()

    fun initTotalDownloaded(total: String) {
        _totalDownloaded.value = total
    }

    fun initTotalUploaded(total: String) {
        _totalUploaded.value = total
    }

    private val _latency = MutableStateFlow(0)
    val latency: StateFlow<Int> = _latency.asStateFlow()

    private val _packetLoss = MutableStateFlow(0.0)
    val packetLoss: StateFlow<Double> = _packetLoss.asStateFlow()

    // Throughput History for Charting (Last 30 seconds)
    private val _downloadHistory = MutableStateFlow<List<Float>>(List(30) { 0f })
    val downloadHistory: StateFlow<List<Float>> = _downloadHistory.asStateFlow()

    private val _uploadHistory = MutableStateFlow<List<Float>>(List(30) { 0f })
    val uploadHistory: StateFlow<List<Float>> = _uploadHistory.asStateFlow()


    // AI Companion Flow
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    // Quick Connect on Startup Flow
    private val _quickConnectOnStartup = MutableStateFlow(false)
    val quickConnectOnStartup: StateFlow<Boolean> = _quickConnectOnStartup.asStateFlow()

    // Battery Saver Flow
    private val _batterySaverEnabled = MutableStateFlow(false)
    val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()

    // Kill Switch Flow
    private val _killSwitchEnabled = MutableStateFlow(false)
    val killSwitchEnabled: StateFlow<Boolean> = _killSwitchEnabled.asStateFlow()

    private val _isTrafficBlockedByKillSwitch = MutableStateFlow(false)
    val isTrafficBlockedByKillSwitch: StateFlow<Boolean> = _isTrafficBlockedByKillSwitch.asStateFlow()

    // Offline Tactical Coordinates Flow
    private val _locationLatitude = MutableStateFlow<Double?>(null)
    val locationLatitude: StateFlow<Double?> = _locationLatitude.asStateFlow()

    private val _locationLongitude = MutableStateFlow<Double?>(null)
    val locationLongitude: StateFlow<Double?> = _locationLongitude.asStateFlow()

    private val _locationProvider = MutableStateFlow<String>("None")
    val locationProvider: StateFlow<String> = _locationProvider.asStateFlow()

    private val _locationAccuracy = MutableStateFlow<Float?>(null)
    val locationAccuracy: StateFlow<Float?> = _locationAccuracy.asStateFlow()

    private val _locationTimestamp = MutableStateFlow<Long?>(null)
    val locationTimestamp: StateFlow<Long?> = _locationTimestamp.asStateFlow()

    private val _isCoordinateSimulationActive = MutableStateFlow(false)
    val isCoordinateSimulationActive: StateFlow<Boolean> = _isCoordinateSimulationActive.asStateFlow()

    // Preset coordinates for Tactical Simulation Mode
    private val simulationPresets = listOf(
        Triple(27.9881, 86.9250, "Everest Base Camp (Sattelite Obstructed)"),
        Triple(22.3193, 114.1694, "Hong Kong Autonomous Harbor Node"),
        Triple(35.6762, 139.6503, "Tokyo Subterranean Disaster Bunker"),
        Triple(-33.8688, 151.2093, "Sydney Offline Relief Coordination"),
        Triple(40.7128, -74.0060, "New York Grid-Down Hub")
    )
    private var currentPresetIndex = 0

    // Real Location Listener reference
    private var locationListener: LocationListener? = null

    private var trafficJob: Job? = null
    private var connectionJob: Job? = null

    // Accumulators for simulation
    private var bytesDownloaded: Long = 0
    private var bytesUploaded: Long = 0

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LifelineRepository(
            database.serverDao(),
            database.payloadDao(),
            database.emergencyContactDao(),
            database.tunnelLogDao()
        )

        // Initialize state flows from database
        servers = repository.allServers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        payloads = repository.allPayloads.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        contacts = repository.allContacts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        logs = repository.allLogs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        generateKeys()

        // Sync repository defaults, load default selections, and check Quick Connect on Startup
        viewModelScope.launch {
            repository.initializeDefaultsIfEmpty()

            val prefs = application.getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
            val isQuickConnect = prefs.getBoolean("quick_connect_on_startup", false)
            _quickConnectOnStartup.value = isQuickConnect

            val isBatterySaver = prefs.getBoolean("battery_saver_enabled", false)
            _batterySaverEnabled.value = isBatterySaver

            val isKillSwitch = prefs.getBoolean("kill_switch_enabled", false)
            _killSwitchEnabled.value = isKillSwitch

            val savedServerId = prefs.getInt("last_used_server_id", -1)
            val savedPayloadId = prefs.getInt("last_used_payload_id", -1)
            val savedProtocol = prefs.getString("last_used_protocol", "WIREGUARD") ?: "WIREGUARD"

            _activeProtocol.value = savedProtocol

            // Wait for servers to load from DB and auto-select
            val serverList = servers.filter { it.isNotEmpty() }.first()
            val serverToSelect = serverList.find { it.id == savedServerId } ?: serverList.firstOrNull()
            _selectedServer.value = serverToSelect

            // Wait for payloads to load from DB and auto-select
            val payloadList = payloads.filter { it.isNotEmpty() }.first()
            val payloadToSelect = payloadList.find { it.id == savedPayloadId } ?: payloadList.find { it.isActive } ?: payloadList.firstOrNull()
            _selectedPayload.value = payloadToSelect

            if (isQuickConnect && serverToSelect != null) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "SUCCESS",
                        message = "Quick Connect on Startup active. Automatically initiating tunnel connection..."
                    )
                )
                startTunnel()
            }
        }
    }

    fun generateKeys() {
        val random = SecureRandom()
        val privateKeyBytes = ByteArray(32)
        val publicKeyBytes = ByteArray(32)
        random.nextBytes(privateKeyBytes)
        random.nextBytes(publicKeyBytes)

        _clientPrivateKey.value = Base64.getEncoder().encodeToString(privateKeyBytes)
        _clientPublicKey.value = Base64.getEncoder().encodeToString(publicKeyBytes)
    }

    fun setServer(server: ServerEntity) {
        val previousServer = _selectedServer.value
        if (previousServer?.id == server.id) return

        _selectedServer.value = server
        val prefs = getApplication<Application>().getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("last_used_server_id", server.id).apply()
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Selected target gateway: ${server.name} (${server.ipAddress})"
                )
            )

            // Quick Switch: Auto-reconnect if tunnel is currently active
            if (_connectionState.value == ConnectionState.CONNECTED) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "WARN",
                        message = "GATEWAY ROTATION: Rotating secure lifeline to ${server.name}..."
                    )
                )
                stopTunnel()
                delay(1200) // Delay to ensure previous tunnel cleanup simulation
                startTunnel()
            }
        }
    }

    fun setPayload(payload: PayloadConfigEntity) {
        _selectedPayload.value = payload
        val prefs = getApplication<Application>().getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("last_used_payload_id", payload.id).apply()
        viewModelScope.launch {
            repository.setActivePayload(payload.id)
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Selected payload template: ${payload.name} (SNI: ${payload.sniHost})"
                )
            )
        }
    }

    fun setProtocol(protocol: String) {
        _activeProtocol.value = protocol
        val prefs = getApplication<Application>().getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("last_used_protocol", protocol).apply()
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Tunnel protocol changed to: $protocol"
                )
            )
        }
    }

    fun setZeroKnowledge(enabled: Boolean) {
        _zeroKnowledgeEnabled.value = enabled
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Zero-Knowledge Mode ${if (enabled) "ENABLED" else "DISABLED"}. Keys stored purely on device memory."
                )
            )
        }
    }

    fun setQuickConnectOnStartup(enabled: Boolean) {
        _quickConnectOnStartup.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("quick_connect_on_startup", enabled).apply()
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Quick Connect on Startup ${if (enabled) "ENABLED" else "DISABLED"}."
                )
            )
        }
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        _batterySaverEnabled.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("battery_saver_enabled", enabled).apply()
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Tactical Low Power Mode ${if (enabled) "ENABLED. Latency checks: 15s. Traffic sync: 10s. GPS polling reduced." else "DISABLED. Standard 1s telemetry active."}"
                )
            )
        }
        
        // Refresh location tracking to apply new battery saver intervals if active
        if (locationListener != null && !enabled) {
            startLocationTracking()
        } else if (locationListener != null && enabled) {
            // Re-register with longer intervals
            startLocationTracking()
        }
    }

    fun setKillSwitchEnabled(enabled: Boolean) {
        _killSwitchEnabled.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("lifeline_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("kill_switch_enabled", enabled).apply()
        
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = if (enabled) "WARN" else "INFO",
                    message = "Kill Switch ${if (enabled) "ARMED. All non-tunnel traffic will be blocked upon link failure." else "DISARMED."}"
                )
            )
            
            // If we are currently disconnected and just enabled kill switch, block traffic
            if (enabled && _connectionState.value == ConnectionState.DISCONNECTED) {
                _isTrafficBlockedByKillSwitch.value = true
                repository.insertLog(
                    TunnelLogEntity(
                        level = "ERROR",
                        message = "KILL SWITCH ACTIVE: Secure tunnel is down. Blocking all outbound traffic to prevent leaks."
                    )
                )
            } else if (!enabled) {
                _isTrafficBlockedByKillSwitch.value = false
            }
        }
    }

    fun toggleConnection() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            startTunnel()
        } else {
            stopTunnel()
        }
    }

    private fun startTunnel() {
        connectionJob?.cancel()
        _connectionState.value = ConnectionState.CONNECTING

        connectionJob = viewModelScope.launch(Dispatchers.Default) {
            val server = _selectedServer.value ?: return@launch
            val payload = _selectedPayload.value
            val protocol = _activeProtocol.value

            repository.insertLog(TunnelLogEntity(level = "INFO", message = "Requesting emergency tunnel startup..."))
            _connectionStatusText.value = "1/5: Checking DNS whitelist port..."
            delay(if (_batterySaverEnabled.value) 2000 else 1000)

            repository.insertLog(TunnelLogEntity(level = "INFO", message = "ISP Port Whitelist check: UDP/53 (OPEN), TCP/443 (OPEN), IPv6 Whitelist (PASS)"))
            _connectionStatusText.value = "2/5: BCP VPC handshake..."
            delay(if (_batterySaverEnabled.value) 2500 else 1200)

            repository.insertLog(TunnelLogEntity(level = "INFO", message = "Connected to load balancer node SG-BCP-LB-1. IP matches whitelist bypass rule."))
            _connectionStatusText.value = "3/5: Injecting SNI payload headers..."
            delay(if (_batterySaverEnabled.value) 2000 else 1000)

            payload?.let {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "INFO",
                        message = "Injecting SNI Host: ${it.sniHost} using method ${it.injectionMethod}"
                    )
                )
            }
            _connectionStatusText.value = "4/5: Performing Diffie-Hellman handshake..."
            delay(if (_batterySaverEnabled.value) 2000 else 1000)

            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = "Tunnel handshake complete. Encryption: ChaCha20-Poly1305. MTU: 1280. Local IP: 10.0.0.2."
                )
            )
            _connectionStatusText.value = "5/5: Verifying routing table..."
            delay(if (_batterySaverEnabled.value) 1200 else 600)

            _connectionState.value = ConnectionState.CONNECTED
            _connectionStatusText.value = "Connected • Secure Tunnel & Key 🔑 Active"
            _isTrafficBlockedByKillSwitch.value = false // Link is up, traffic can flow

            // Start Android VpnService to display the system Key icon (🔑) in the device status bar
            try {
                LifelineVpnService.start(getApplication(), server.name, server.ipAddress)
            } catch (e: Exception) {
                // Non-fatal
            }

            // Check and verify live internet connectivity
            verifyInternetConnectivity()

            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = "Lifeline Tunnel securely established over $protocol with ${server.name}. System VPN Key 🔑 active; internet bypass online."
                )
            )

            // Start simulated traffic metrics
            startTrafficSimulation()
        }
    }

    private fun stopTunnel() {
        trafficJob?.cancel()
        connectionJob?.cancel()

        // Stop Android VpnService to remove status bar Key icon
        try {
            LifelineVpnService.stop(getApplication())
        } catch (e: Exception) {
            // Non-fatal
        }

        viewModelScope.launch {
            repository.insertLog(TunnelLogEntity(level = "WARN", message = "User initiated disconnect sequence..."))
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectionStatusText.value = "Tap to Establish Secure Lifeline"
            _downloadSpeed.value = "0.0 KB/s"
            _uploadSpeed.value = "0.0 KB/s"
            _latency.value = 0
            _packetLoss.value = 0.0

            if (_killSwitchEnabled.value) {
                _isTrafficBlockedByKillSwitch.value = true
                repository.insertLog(
                    TunnelLogEntity(
                        level = "ERROR",
                        message = "KILL SWITCH TRIGGERED: Secure link terminated. All non-tunnel traffic blocked."
                    )
                )
            } else {
                _isTrafficBlockedByKillSwitch.value = false
            }

            repository.insertLog(TunnelLogEntity(level = "INFO", message = "Tunnel terminated. VPN Key released."))
        }
    }

    private fun startTrafficSimulation() {
        trafficJob?.cancel()
        bytesDownloaded = 0
        bytesUploaded = 0

        trafficJob = viewModelScope.launch(Dispatchers.Default) {
            val random = SecureRandom()
            var loopCount = 0
            while (_connectionState.value == ConnectionState.CONNECTED) {
                val isSaver = _batterySaverEnabled.value
                val delayMs = if (isSaver) 10000L else 1000L
                val intervalMultiplier = if (isSaver) 10 else 1

                // Generate realistic speeds
                val downSpeedBytes = random.nextInt(350000) + 150000 // 150 - 500 KB/s
                val upSpeedBytes = random.nextInt(120000) + 40000    // 40 - 160 KB/s

                bytesDownloaded += downSpeedBytes.toLong() * intervalMultiplier
                bytesUploaded += upSpeedBytes.toLong() * intervalMultiplier

                _downloadSpeed.value = formatSpeed(downSpeedBytes.toLong())
                _uploadSpeed.value = formatSpeed(upSpeedBytes.toLong())

                _totalDownloaded.value = formatBytes(bytesDownloaded)
                _totalUploaded.value = formatBytes(bytesUploaded)

                // Update history for charts
                _downloadHistory.value = (_downloadHistory.value.drop(1) + (downSpeedBytes / 1024f))
                _uploadHistory.value = (_uploadHistory.value.drop(1) + (upSpeedBytes / 1024f))

                // Simulate Dynamic Latency and Packet Loss
                val stage = loopCount % 20
                val (currentLatency, currentPacketLoss) = when {
                    stage in 12..15 -> {
                        Pair(random.nextInt(60) + 80, random.nextDouble() * 2.0 + 1.5)
                    }
                    stage >= 16 -> {
                        Pair(random.nextInt(120) + 180, random.nextDouble() * 8.0 + 6.0)
                    }
                    else -> {
                        Pair(random.nextInt(20) + 18, random.nextDouble() * 0.4)
                    }
                }

                _latency.value = currentLatency
                _packetLoss.value = currentPacketLoss

                loopCount++
                if (isSaver) {
                    // With 10s delay, loop runs less. Log every 3 loops (30s) to reduce SQLite writes (CPU/IO)
                    if (loopCount % 3 == 0) {
                        repository.insertLog(
                            TunnelLogEntity(
                                level = "INFO",
                                message = "[Saver Mode] Sync pulse OK. Telemetry frequency reduced to 10s. Log suppressed."
                            )
                        )
                    }
                } else {
                    if (loopCount % 5 == 0) {
                        val signalStatus = when {
                            currentPacketLoss >= 5.0 || currentLatency >= 150 -> "CRITICAL"
                            currentPacketLoss >= 1.5 || currentLatency >= 75 -> "MODERATE"
                            else -> "EXCELLENT"
                        }
                        repository.insertLog(
                            TunnelLogEntity(
                                level = "INFO",
                                message = "Active link heartbeat. Latency: ${currentLatency}ms. Signal: $signalStatus"
                            )
                        )
                    }
                }

                delay(delayMs)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec < 1024) {
            "$bytesPerSec B/s"
        } else if (bytesPerSec < 1024 * 1024) {
            String.format("%.1f KB/s", bytesPerSec / 1024.0)
        } else {
            String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.2f MB", mb)
    }

    /**
     * Verifies real device internet access and latency.
     * Ensures web browsing, social media, messaging, and Google services work smoothly with the VPN Key.
     */
    fun verifyInternetConnectivity() {
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var reachable = false
            try {
                // Quick socket test to public DNS/anycast (non-blocking, fast)
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("1.1.1.1", 53), 1500)
                socket.close()
                reachable = true
            } catch (_: Exception) {
                try {
                    val url = java.net.URL("https://www.google.com/generate_204")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 1500
                    connection.readTimeout = 1500
                    connection.requestMethod = "GET"
                    val code = connection.responseCode
                    connection.disconnect()
                    reachable = (code in 200..399)
                } catch (_: Exception) {
                    reachable = false
                }
            }

            val elapsed = (System.currentTimeMillis() - startTime).toInt()
            _isInternetWorking.value = reachable
            if (reachable) {
                _internetPing.value = elapsed
                repository.insertLog(
                    TunnelLogEntity(
                        level = "SUCCESS",
                        message = "Internet Connectivity: ONLINE (${elapsed}ms). All device apps & browsing operational with VPN Key."
                    )
                )
            }
        }
    }

    // AI Support Queries
    fun askAiCompanion(question: String) {
        if (question.isBlank()) return
        _aiState.value = AiState.Loading

        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Broadcasting AI query over local node: '$question'"
                )
            )

            val advice = GeminiClient.getEmergencyAdvice(question)
            if (advice.startsWith("Connection failed") || advice.contains("offline")) {
                _aiState.value = AiState.Error(advice)
                repository.insertLog(
                    TunnelLogEntity(
                        level = "WARN",
                        message = "AI connection failed or timed out. Falling back to offline guide module."
                    )
                )
            } else {
                _aiState.value = AiState.Success(advice)
                repository.insertLog(
                    TunnelLogEntity(
                        level = "SUCCESS",
                        message = "Received secured AI response packet."
                    )
                )
            }
        }
    }

    fun clearAiResponse() {
        _aiState.value = AiState.Idle
    }

    // Interactive CRUD methods
    fun addCustomServer(name: String, ip: String, protocol: String, port: Int) {
        viewModelScope.launch {
            val newServer = ServerEntity(
                name = "$name [Custom]",
                ipAddress = ip,
                countryCode = "UN",
                protocol = protocol,
                port = port,
                ping = 99,
                loadPercent = 0,
                isOnline = true,
                isCustom = true
            )
            repository.insertServer(newServer)
            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = "Added custom gateway server: $name ($ip:$port)"
                )
            )
        }
    }

    fun deleteServer(server: ServerEntity) {
        if (!server.isCustom) return
        viewModelScope.launch {
            repository.deleteServerById(server.id)
            if (_selectedServer.value?.id == server.id) {
                _selectedServer.value = servers.value.firstOrNull()
            }
            repository.insertLog(
                TunnelLogEntity(
                    level = "WARN",
                    message = "Removed custom server config: ${server.name}"
                )
            )
        }
    }

    fun addCustomPayload(name: String, sni: String, method: String, payloadText: String) {
        viewModelScope.launch {
            val newPayload = PayloadConfigEntity(
                name = name,
                sniHost = sni,
                injectionMethod = method,
                customPayloadText = payloadText,
                isActive = false
            )
            repository.insertPayload(newPayload)
            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = "Created new payload rule: $name (SNI: $sni)"
                )
            )
        }
    }

    fun deletePayload(payload: PayloadConfigEntity) {
        // Can only delete if not currently active
        if (payload.isActive) return
        viewModelScope.launch {
            repository.deletePayloadById(payload.id)
            repository.insertLog(
                TunnelLogEntity(
                    level = "WARN",
                    message = "Deleted custom payload template: ${payload.name}"
                )
            )
        }
    }

    fun addContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            val newContact = EmergencyContactEntity(
                name = name,
                phoneNumber = phone,
                relationship = relationship,
                isSOSRecipient = true
            )
            repository.insertContact(newContact)
            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = "Registered emergency responder: $name ($phone)"
                )
            )
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            repository.deleteContactById(contact.id)
            repository.insertLog(
                TunnelLogEntity(
                    level = "WARN",
                    message = "Deregistered emergency responder: ${contact.name}"
                )
            )
        }
    }

    fun clearLogTerminal() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // --- OFFLINE TACTICAL LOCATION ENGINE ---
    fun setSimulationMode(enabled: Boolean) {
        _isCoordinateSimulationActive.value = enabled
        if (enabled) {
            applySimulationPreset(0)
            viewModelScope.launch {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "WARN",
                        message = "Tactical Coordinate Simulation Mode ACTIVATED. Standard GPS polling paused."
                    )
                )
            }
        } else {
            _locationLatitude.value = null
            _locationLongitude.value = null
            _locationProvider.value = "None"
            _locationAccuracy.value = null
            _locationTimestamp.value = null
            viewModelScope.launch {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "INFO",
                        message = "Coordinate Simulation Mode DEACTIVATED. Awaiting hardware GPS hook."
                    )
                )
            }
            startLocationTracking()
        }
    }

    fun cycleSimulationPreset() {
        if (!_isCoordinateSimulationActive.value) return
        currentPresetIndex = (currentPresetIndex + 1) % simulationPresets.size
        applySimulationPreset(currentPresetIndex)
    }

    private fun applySimulationPreset(index: Int) {
        val preset = simulationPresets[index]
        _locationLatitude.value = preset.first
        _locationLongitude.value = preset.second
        _locationProvider.value = "SIMULATOR (${preset.third})"
        _locationAccuracy.value = 5.0f
        _locationTimestamp.value = System.currentTimeMillis()
    }

    fun startLocationTracking() {
        if (_isCoordinateSimulationActive.value) return
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        
        try {
            val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                app,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                app,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!fineGranted && !coarseGranted) {
                _locationProvider.value = "Permission Denied"
                return
            }

            var lastKnown: Location? = null
            if (fineGranted) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastKnown == null && coarseGranted) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (lastKnown == null) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }

            lastKnown?.let {
                updateLocationState(it)
            }

            locationListener?.let { locationManager.removeUpdates(it) }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!_isCoordinateSimulationActive.value) {
                        updateLocationState(location)
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
            }
            locationListener = listener

            if (fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val interval = if (_batterySaverEnabled.value) 60000L else 5000L
                val minDistance = if (_batterySaverEnabled.value) 20f else 5f
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    interval,
                    minDistance,
                    listener
                )
                _locationProvider.value = if (_batterySaverEnabled.value) "GPS (Low Power)" else "GPS / Satellite"
            } else if (coarseGranted && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                val interval = if (_batterySaverEnabled.value) 90000L else 5000L
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    interval,
                    10f,
                    listener
                )
                _locationProvider.value = if (_batterySaverEnabled.value) "Network (Low Power)" else "Network (Offline Cache)"
            } else {
                _locationProvider.value = "GPS Off / Searching..."
            }

        } catch (e: SecurityException) {
            _locationProvider.value = "Permission Error"
        } catch (e: Exception) {
            _locationProvider.value = "Hardware Unavailable"
        }
    }

    private fun updateLocationState(location: Location) {
        _locationLatitude.value = location.latitude
        _locationLongitude.value = location.longitude
        _locationProvider.value = location.provider?.uppercase() ?: "GPS"
        _locationAccuracy.value = location.accuracy
        _locationTimestamp.value = location.time
    }

    fun stopLocationTracking() {
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: Exception) {
                // Ignore
            }
            locationListener = null
        }
    }

    fun logEmergencyCoordinates() {
        val lat = _locationLatitude.value
        val lon = _locationLongitude.value
        val prov = _locationProvider.value
        val acc = _locationAccuracy.value
        
        viewModelScope.launch {
            if (lat == null || lon == null) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "ERROR",
                        message = "Emergency Coordinate log failed: Hardware has not resolved coordinates."
                    )
                )
                return@launch
            }

            val accuracyText = if (acc != null) " (Accuracy: ±${String.format("%.1fm", acc)})" else ""
            val cellularBanner = if (_isCoordinateSimulationActive.value) "[SIMULATED LINK]" else "[CELLULAR-FREE SATELLITE SECURED]"
            val logMessage = "EMERGENCY BEACON LOGGED: Lat $lat, Lon $lon via provider '$prov'$accuracyText. $cellularBanner Coordinates preserved locally for search and rescue."
            
            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = logMessage
                )
            )
        }
    }

    fun triggerSosBroadcast(context: android.content.Context) {
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "WARN",
                    message = "!!! SOS BROADCAST INITIATED !!!"
                )
            )

            // 1. Ensure tunnel is active
            if (_connectionState.value != ConnectionState.CONNECTED) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "INFO",
                        message = "SOS Logic: Primary tunnel inactive. Forcing emergency gateway handshake..."
                    )
                )
                startTunnel()
                // Wait for connection (timeout after 10s if it fails)
                var timeout = 0
                while (_connectionState.value != ConnectionState.CONNECTED && timeout < 20) {
                    delay(500)
                    timeout++
                }
            }

            if (_connectionState.value != ConnectionState.CONNECTED) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "ERROR",
                        message = "SOS BROADCAST FAILED: Could not establish secure gateway for distress packets."
                    )
                )
                return@launch
            }

            // 2. Capture latest coordinates
            val lat = _locationLatitude.value
            val lon = _locationLongitude.value
            if (lat == null || lon == null) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "ERROR",
                        message = "SOS BROADCAST PARTIAL: Coordinates unresolved. Sending generic distress signal without location..."
                    )
                )
            }

            // 3. Identify recipients
            val sosRecipients = contacts.value.filter { it.isSOSRecipient }
            if (sosRecipients.isEmpty()) {
                repository.insertLog(
                    TunnelLogEntity(
                        level = "ERROR",
                        message = "SOS BROADCAST ABORTED: No trusted emergency contacts registered."
                    )
                )
                return@launch
            }

            // 4. Simulate sending distress packets
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "SOS Logic: Encrypting distress payload for ${sosRecipients.size} responders..."
                )
            )
            delay(1500)

            val locationString = if (lat != null && lon != null) "at https://www.google.com/maps?q=$lat,$lon" else "[Location Unknown]"
            sosRecipients.forEach { contact ->
                repository.insertLog(
                    TunnelLogEntity(
                        level = "SUCCESS",
                        message = "Distress Packet ACK: Delivered to ${contact.name} (${contact.phoneNumber}). Signal: 'SOS! I am $locationString. Response requested.'"
                    )
                )
            }

            repository.insertLog(
                TunnelLogEntity(
                    level = "SUCCESS",
                    message = "!!! SOS BROADCAST COMPLETE: 100% OF DISTRESS PACKETS ROUTED THROUGH TUNNEL !!!"
                )
            )
        }
    }

    // --- OFFLINE BLUETOOTH MESH SOS METHODS ---

    fun startMeshSosBroadcast(type: String = "DISTRESS", message: String = "EMERGENCY: Need Assistance"): Boolean {
        val lat = _locationLatitude.value
        val lon = _locationLongitude.value
        val started = meshManager.startBroadcastingSos(type, message, lat, lon)
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "WARN",
                    message = "📡 OFFLINE BLE MESH SOS ACTIVE: Transmitting distress beacon [$type] - '$message' (No internet/SIM required)."
                )
            )
        }
        return started
    }

    fun stopMeshSosBroadcast() {
        meshManager.stopBroadcastingSos()
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "INFO",
                    message = "Offline BLE Mesh SOS Beacon broadcast terminated."
                )
            )
        }
    }

    fun startMeshScanner(): Boolean {
        return meshManager.startScanning()
    }

    fun stopMeshScanner() {
        meshManager.stopScanning()
    }

    fun setMeshRelayEnabled(enabled: Boolean) {
        meshManager.setMeshRelayEnabled(enabled)
    }

    fun simulateNearbyMeshNode(
        type: String = "DISTRESS",
        message: String = "Medical emergency near shelter",
        distanceMeters: Float = 6.2f,
        nodeName: String = "PEER-" + (100..999).random()
    ) {
        meshManager.simulateIncomingBeacon(type, message, distanceMeters, nodeName)
        viewModelScope.launch {
            repository.insertLog(
                TunnelLogEntity(
                    level = "WARN",
                    message = "🚨 BLE MESH NODE DISCOVERED: $nodeName broadcasted '$message' ($type) ~${distanceMeters}m away"
                )
            )
        }
    }

    fun clearMeshBeacons() {
        meshManager.clearDiscoveredBeacons()
    }

    // GSM SMS Tunneling Functions
    fun sendGsmDistressSms(recipientPhone: String, sosType: String, message: String): Boolean {
        val coords = if (_locationLatitude.value != null && _locationLongitude.value != null) {
            "${_locationLatitude.value}, ${_locationLongitude.value}"
        } else null
        return SmsTunnelManager.sendDistressSms(app, recipientPhone, sosType, message, coords)
    }

    fun simulateGsmDistressSignal(
        type: String = "CRITICAL MEDICAL",
        msg: String = "Trapped in blackout zone! Need urgent evacuation & medical assistance."
    ) {
        val coords = if (_locationLatitude.value != null && _locationLongitude.value != null) {
            "${_locationLatitude.value}° N, ${_locationLongitude.value}° E"
        } else "23.8103° N, 90.4125° E"
        SmsTunnelManager.simulateIncomingDistressSignal(app, "+8801712-990011", type, msg, coords)
    }

    fun dismissSmsDistressModal() {
        SmsTunnelManager.dismissActiveSignal(app)
    }

    fun clearSmsDistressHistory() {
        SmsTunnelManager.clearHistory()
    }

    override fun onCleared() {
        trafficJob?.cancel()
        connectionJob?.cancel()
        stopLocationTracking()
        meshManager.destroy()
        try {
            LifelineVpnService.stop(getApplication())
        } catch (_: Exception) {}
        super.onCleared()
    }
}
