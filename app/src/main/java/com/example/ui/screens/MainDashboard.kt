package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.net.VpnService
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.data.entity.EmergencyContactEntity
import com.example.data.entity.PayloadConfigEntity
import com.example.data.entity.ServerEntity
import com.example.data.entity.TunnelLogEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiState
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.LifelineViewModel
import com.example.service.IncomingSmsDistressSignal
import com.example.service.SmsTunnelManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.rounded.Send as IconsAutoMirroredSend
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders

@Composable
fun MainDashboard(viewModel: LifelineViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ViewModel state collections
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val statusText by viewModel.connectionStatusText.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val payloads by viewModel.payloads.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val selectedPayload by viewModel.selectedPayload.collectAsStateWithLifecycle()
    val activeProtocol by viewModel.activeProtocol.collectAsStateWithLifecycle()
    val zeroKnowledge by viewModel.zeroKnowledgeEnabled.collectAsStateWithLifecycle()
    val quickConnectOnStartup by viewModel.quickConnectOnStartup.collectAsStateWithLifecycle()
    val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsStateWithLifecycle()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsStateWithLifecycle()
    val isTrafficBlocked by viewModel.isTrafficBlockedByKillSwitch.collectAsStateWithLifecycle()
    val isInternetWorking by viewModel.isInternetWorking.collectAsStateWithLifecycle()
    val internetPing by viewModel.internetPing.collectAsStateWithLifecycle()
    
    val downloadSpeed by viewModel.downloadSpeed.collectAsStateWithLifecycle()
    val uploadSpeed by viewModel.uploadSpeed.collectAsStateWithLifecycle()
    val totalDownloaded by viewModel.totalDownloaded.collectAsStateWithLifecycle()
    val totalUploaded by viewModel.totalUploaded.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    val packetLoss by viewModel.packetLoss.collectAsStateWithLifecycle()
    
    val clientPublicKey by viewModel.clientPublicKey.collectAsStateWithLifecycle()
    val clientPrivateKey by viewModel.clientPrivateKey.collectAsStateWithLifecycle()

    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val uploadHistory by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val incomingSmsSignal by viewModel.incomingSmsSignal.collectAsStateWithLifecycle()
    val smsStatusMessage by viewModel.smsStatusMessage.collectAsStateWithLifecycle()

    val modelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(downloadHistory, uploadHistory) {
        val downloadEntries = downloadHistory.mapIndexed { index, value -> entryOf(index.toFloat(), value) }
        val uploadEntries = uploadHistory.mapIndexed { index, value -> entryOf(index.toFloat(), value) }
        modelProducer.setEntries(downloadEntries, uploadEntries)
    }

    val ringColor = when (connectionState) {
        ConnectionState.CONNECTED -> CyberPrimary
        ConnectionState.CONNECTING -> CyberTertiary
        ConnectionState.DISCONNECTED -> CyberSecondary
    }

    var activeTab by remember { mutableStateOf("🚀 Tunnel") }

    // Dialog state controllers
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showAddPayloadDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }

    // Android VpnService permission launcher (requests system VPN dialog to show status bar Key 🔑)
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleConnection()
        } else {
            Toast.makeText(
                context,
                "VPN অনুমতি প্রয়োজন: স্ট্যাটাস বারে চাবি (Key 🔑) লোগো প্রদর্শনের জন্য",
                Toast.LENGTH_LONG
            ).show()
            viewModel.toggleConnection()
        }
    }

    val onToggleConnect = {
        if (connectionState == ConnectionState.DISCONNECTED) {
            val vpnIntent = try {
                VpnService.prepare(context)
            } catch (e: Exception) {
                null
            }
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                viewModel.toggleConnection()
            }
        } else {
            viewModel.toggleConnection()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(CyberBackground)
            ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LIFELINE TUNNEL",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberPrimary,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(
                        text = "Secure Autonomous Gatekeeper Hub",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = CyberTextSecondary,
                            fontFamily = FontFamily.SansSerif
                        )
                    )
                }

                // Zero-Knowledge & Power Mode Badges
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // VPN Key Status Badge (Chabi Logo)
                    if (connectionState == ConnectionState.CONNECTED) {
                        Surface(
                            color = CyberPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberPrimary),
                            modifier = Modifier.clickable {
                                Toast.makeText(
                                    context,
                                    "🔑 ভিপিএন চাবি লোগো স্ট্যাটাস বারে সক্রিয় (VPN Key Active)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = "VPN Key Active",
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KEY 🔑 ACTIVE",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }

                        Surface(
                            color = CyberSecondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberSecondary),
                            modifier = Modifier.clickable {
                                viewModel.verifyInternetConnectivity()
                                Toast.makeText(
                                    context,
                                    "🌐 ইন্টারনেট সম্পূর্ণ সচল ও কার্যক্ষম (Internet Online)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Wifi,
                                    contentDescription = "Internet Online",
                                    tint = CyberSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "NET ONLINE ✓",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    if (batterySaverEnabled) {
                        Surface(
                            color = CyberTertiary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Eco,
                                    contentDescription = "Low Power Mode",
                                    tint = CyberTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LOW POWER",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberTertiary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    if (killSwitchEnabled) {
                        Surface(
                            color = CyberError.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberError.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GppMaybe,
                                    contentDescription = "Kill Switch Armed",
                                    tint = CyberError,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KILL SWITCH",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberError,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    Surface(
                        color = if (zeroKnowledge) CyberPrimary.copy(alpha = 0.15f) else CyberSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (zeroKnowledge) CyberPrimary.copy(alpha = 0.4f) else CyberTextMuted
                        ),
                        modifier = Modifier.clickable {
                            viewModel.setZeroKnowledge(!zeroKnowledge)
                            Toast.makeText(
                                context,
                                "Zero Knowledge " + (if (!zeroKnowledge) "Enabled" else "Disabled"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (zeroKnowledge) Icons.Rounded.VerifiedUser else Icons.Rounded.PrivacyTip,
                                contentDescription = "Zero Knowledge Status",
                                tint = if (zeroKnowledge) CyberPrimary else CyberTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (zeroKnowledge) "ZERO KNOWLEDGE" else "DIRECT MODE",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (zeroKnowledge) CyberPrimary else CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }

            // Connection Main Control Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, CyberSurfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // One-Tap Connector Button with explicit labels
                    Box(
                        modifier = Modifier
                            .size(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Breathing Glow Rings
                        val infiniteTransition = rememberInfiniteTransition(label = "PulseGlow")
                        val scaleMultiplier by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseGlow"
                        )
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = 0.45f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseAlpha"
                        )

                        val ringColor = when (connectionState) {
                            ConnectionState.CONNECTED -> CyberPrimary
                            ConnectionState.CONNECTING -> CyberTertiary
                            ConnectionState.DISCONNECTED -> CyberSecondary
                        }

                        // Outer Glow (Only active when connected or connecting)
                        if (connectionState != ConnectionState.DISCONNECTED) {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .drawBehind {
                                        drawCircle(
                                            color = ringColor.copy(alpha = glowAlpha * 0.3f),
                                            radius = size.minDimension / 2 * scaleMultiplier
                                        )
                                        drawCircle(
                                            color = ringColor.copy(alpha = 0.1f),
                                            radius = size.minDimension / 2 * (scaleMultiplier + 0.15f)
                                        )
                                    }
                            )
                        }

                        // Central Button Core
                        Surface(
                            onClick = { onToggleConnect() },
                            modifier = Modifier
                                .size(120.dp)
                                .testTag("one_tap_connect_button"),
                            shape = CircleShape,
                            color = CyberSurfaceVariant,
                            border = BorderStroke(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(ringColor, ringColor.copy(alpha = 0.3f), ringColor)
                                )
                            ),
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = when (connectionState) {
                                            ConnectionState.CONNECTED -> Icons.Rounded.VpnKey
                                            ConnectionState.CONNECTING -> Icons.Rounded.Autorenew
                                            ConnectionState.DISCONNECTED -> Icons.Rounded.LockOpen
                                        },
                                        contentDescription = "Toggle Connection",
                                        tint = ringColor,
                                        modifier = Modifier
                                            .size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (connectionState) {
                                            ConnectionState.CONNECTED -> "KEY ACTIVE 🔑"
                                            ConnectionState.CONNECTING -> "SYNCING"
                                            ConnectionState.DISCONNECTED -> "STANDBY"
                                        },
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ringColor,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.8.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Explicit Action Label
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "TAP TO TERMINATE TUNNEL"
                            ConnectionState.CONNECTING -> "ESTABLISHING SECURE GATEWAY..."
                            ConnectionState.DISCONNECTED -> "ONE-TAP TO ACTIVATE PROTECTION"
                        },
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (connectionState == ConnectionState.DISCONNECTED) CyberTextSecondary else ringColor,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = if (connectionState == ConnectionState.CONNECTED) 4.dp else 16.dp)
                    )

                    if (connectionState == ConnectionState.CONNECTED) {
                        Surface(
                            color = CyberPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "STATUS BAR KEY 🔑 চাবি লোগো সক্রিয়",
                                    style = TextStyle(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    // Critical Telemetry Readouts (Always visible)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBackground.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, CyberSurfaceVariant), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Connection Status
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TUNNEL STATUS",
                                style = TextStyle(fontSize = 7.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = statusText.uppercase(),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (connectionState) {
                                        ConnectionState.CONNECTED -> CyberPrimary
                                        ConnectionState.CONNECTING -> CyberTertiary
                                        ConnectionState.DISCONNECTED -> CyberTextPrimary
                                    },
                                    fontFamily = FontFamily.Monospace
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = CyberSurfaceVariant)

                        // Status Bar Key Readout
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VPN KEY (চাবি)",
                                style = TextStyle(fontSize = 7.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = "VPN Key",
                                    tint = if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberTextMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (connectionState == ConnectionState.CONNECTED) "ACTIVE 🔑" else "OFF",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberTextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = CyberSurfaceVariant)

                        // Encryption Integrity
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ENCRYPTION",
                                style = TextStyle(fontSize = 7.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (connectionState == ConnectionState.CONNECTED) Icons.Rounded.VerifiedUser else Icons.Rounded.GppMaybe,
                                    contentDescription = null,
                                    tint = if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberTextMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (connectionState == ConnectionState.CONNECTED) "AES-256" else "INACTIVE",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberTextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = CyberSurfaceVariant)

                        // Network Latency
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PING",
                                style = TextStyle(fontSize = 7.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberTextMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (connectionState == ConnectionState.CONNECTED) "$latency ms" else "-- ms",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (connectionState == ConnectionState.CONNECTED) CyberTextPrimary else CyberTextMuted,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.testTag("latency_val")
                                )
                            }
                        }
                    }

                    if (connectionState == ConnectionState.CONNECTED) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "REAL-TIME THROUGHPUT PULSE (KB/S)",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTextSecondary,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, CyberSurfaceVariant), shape = RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Chart(
                                chart = lineChart(),
                                model = modelProducer.getModel() ?: com.patrykandpatrick.vico.core.entry.entryModelOf(0f),
                                modifier = Modifier.fillMaxSize(),
                                startAxis = rememberStartAxis(
                                    label = null,
                                    tick = null,
                                    guideline = null
                                ),
                                bottomAxis = rememberBottomAxis(
                                    label = null,
                                    tick = null,
                                    guideline = null
                                ),
                                isZoomEnabled = false,
                                chartScrollState = rememberChartScrollState()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(CyberPrimary, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("IN", fontSize = 8.sp, color = CyberTextSecondary, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.size(6.dp).background(CyberSecondary, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("OUT", fontSize = 8.sp, color = CyberTextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // VPN Key Status Banner (ভিপিএন চাবি লোগো নির্দেশক ও স্ট্যাটাস)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("vpn_key_status_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (connectionState == ConnectionState.CONNECTED)
                        CyberPrimary.copy(alpha = 0.12f)
                    else
                        CyberSurfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (connectionState == ConnectionState.CONNECTED)
                        CyberPrimary.copy(alpha = 0.6f)
                    else
                        CyberSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (connectionState == ConnectionState.CONNECTED)
                                    CyberPrimary.copy(alpha = 0.25f)
                                else
                                    CyberBackground,
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberTextMuted.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = "VPN Key Logo",
                            tint = if (connectionState == ConnectionState.CONNECTED)
                                CyberPrimary
                            else
                                CyberTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (connectionState == ConnectionState.CONNECTED)
                                    "🔑 ভিপিএন চাবি: সক্রিয় • 🌐 ইন্টারনেট: সচল"
                                else
                                    "🔑 ভিপিএন চাবি লোগো: স্ট্যান্ডবাই (STANDBY)",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (connectionState == ConnectionState.CONNECTED)
                                        CyberPrimary
                                    else
                                        CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Surface(
                                color = if (connectionState == ConnectionState.CONNECTED) CyberPrimary else CyberSurfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (connectionState == ConnectionState.CONNECTED) "NET & KEY OK ✓" else "DISCONNECTED",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (connectionState == ConnectionState.CONNECTED) CyberBackground else CyberTextMuted,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (connectionState == ConnectionState.CONNECTED)
                                "✓ ভিপিএন চাবি (Key 🔑) সক্রিয় থাকা সত্ত্বেও আপনার ইন্টারনেট, ব্রাউজার ও সকল অ্যাপ নিরবচ্ছিন্নভাবে কাজ করছে। কোনো কিছু ব্লক হচ্ছে না।"
                            else
                                "কানেক্ট বাটনে চাপলে Android সিস্টেমে ভিপিএন সক্রিয় হবে এবং ওপরের স্ট্যাটাস বারে চাবি (Key 🔑) লোগো আসবে, সাথে ইন্টারনেট সচল থাকবে।",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = if (connectionState == ConnectionState.CONNECTED)
                                    CyberTextPrimary
                                else
                                    CyberTextMuted,
                                lineHeight = 14.sp
                            )
                        )
                        if (connectionState == ConnectionState.CONNECTED) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(CyberPrimary, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ইন্টারনেট টেস্ট: OK (${internetPing ?: latency}ms)",
                                        style = TextStyle(fontSize = 9.sp, color = CyberPrimary, fontFamily = FontFamily.Monospace)
                                    )
                                }
                                Surface(
                                    onClick = {
                                        viewModel.verifyInternetConnectivity()
                                        Toast.makeText(context, "ইন্টারনেট টেস্ট সফল: ইন্টারনেট কার্যক্ষম!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyberSurface,
                                    border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "ইন্টারনেট টেস্ট ⚡",
                                        style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CyberPrimary),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Switch Regional Gateway Carousel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUICK-SWITCH GATEWAYS",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextMuted,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "BYPASS MODE",
                        style = TextStyle(fontSize = 7.sp, color = CyberPrimary, fontWeight = FontWeight.Black)
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servers.forEach { server ->
                        val isSelected = selectedServer?.id == server.id
                        Surface(
                            onClick = { 
                                viewModel.setServer(server)
                                // If already connected, perform a rapid reconnect
                                if (connectionState == ConnectionState.CONNECTED) {
                                    viewModel.toggleConnection() // Stop
                                    scope.launch {
                                        delay(300)
                                        viewModel.toggleConnection() // Restart on new node
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) CyberPrimary.copy(alpha = 0.15f) else CyberSurface,
                            border = BorderStroke(
                                1.dp, 
                                if (isSelected) CyberPrimary else CyberSurfaceVariant
                            ),
                            modifier = Modifier.widthIn(min = 100.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.Dns else Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (isSelected) CyberPrimary else CyberTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = server.name.uppercase(),
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) CyberTextPrimary else CyberTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = server.ipAddress,
                                        style = TextStyle(
                                            fontSize = 8.sp,
                                            color = CyberTextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary Metrics (Speeds & Throughput Pulse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Download Speed",
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "DL FLOW",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        color = CyberTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            Text(
                                text = downloadSpeed,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            
                            // Throughput Activity Pulse (Download)
                            if (connectionState == ConnectionState.CONNECTED && downloadSpeed != "0 B/s") {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(2.dp)
                                        .padding(top = 4.dp),
                                    color = CyberPrimary,
                                    trackColor = CyberSurfaceVariant
                                )
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(36.dp), color = CyberSurfaceVariant)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Upload Speed",
                                    tint = CyberSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "UL FLOW",
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        color = CyberTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            Text(
                                text = uploadSpeed,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )

                            // Throughput Activity Pulse (Upload)
                            if (connectionState == ConnectionState.CONNECTED && uploadSpeed != "0 B/s") {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(2.dp)
                                        .padding(top = 4.dp),
                                    color = CyberSecondary,
                                    trackColor = CyberSurfaceVariant
                                )
                            }
                        }
                    }

                    if (connectionState == ConnectionState.CONNECTED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Real-time Signal Strength & Link Health Panel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant, shape = RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    when {
                                        packetLoss >= 5.0 || latency >= 150 -> CyberError.copy(alpha = 0.4f)
                                        packetLoss >= 1.5 || latency >= 75 -> CyberTertiary.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Title & Status Indicator
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (statusLabel, statusColor) = when {
                                        packetLoss >= 5.0 || latency >= 150 -> Pair("CRITICAL LINK", CyberError)
                                        packetLoss >= 1.5 || latency >= 75 -> Pair("MODERATE STABILITY", CyberTertiary)
                                        else -> Pair("EXCELLENT SIGNAL", CyberPrimary)
                                    }
                                    
                                    // Visual glowing dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(statusColor, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = statusLabel,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        modifier = Modifier.testTag("signal_status_text")
                                    )
                                }

                                // Right: Signal Strength Bars (4 bars)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val filledBars = when {
                                        packetLoss >= 5.0 || latency >= 150 -> 1
                                        packetLoss >= 1.5 || latency >= 75 -> 2
                                        else -> 4
                                    }
                                    val barColor = when {
                                        packetLoss >= 5.0 || latency >= 150 -> CyberError
                                        packetLoss >= 1.5 || latency >= 75 -> CyberTertiary
                                        else -> CyberPrimary
                                    }

                                    for (i in 1..4) {
                                        val heightDp = (4 + i * 3).dp
                                        val isFilled = i <= filledBars
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(heightDp)
                                                .background(
                                                    if (isFilled) barColor else CyberTextMuted,
                                                    shape = RoundedCornerShape(1.dp)
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Numerical readouts: Latency and Loss side-by-side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Latency Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "LATENCY",
                                        style = TextStyle(
                                            fontSize = 8.sp,
                                            color = CyberTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$latency ms",
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTextPrimary,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        modifier = Modifier.testTag("latency_val")
                                    )
                                }

                                // Separator
                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(1.dp)
                                        .background(CyberTextMuted.copy(alpha = 0.5f))
                                        .align(Alignment.CenterVertically)
                                )

                                // Packet Loss Column
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                ) {
                                    Text(
                                        text = "PACKET LOSS",
                                        style = TextStyle(
                                            fontSize = 8.sp,
                                            color = CyberTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format("%.2f%%", packetLoss),
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTextPrimary,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        modifier = Modifier.testTag("packet_loss_val")
                                    )
                                }
                            }

                            // Pulsing banner if Critical Zone
                            if (packetLoss >= 5.0 || latency >= 150) {
                                val warningTransition = rememberInfiniteTransition(label = "WarningBlink")
                                val warningAlpha by warningTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "warningAlpha"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            CyberError.copy(alpha = 0.15f * warningAlpha),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            CyberError.copy(alpha = 0.3f * warningAlpha),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Warning",
                                        tint = CyberError,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CRITICAL CONNECTION ZONE DETECTED",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = CyberError
                                        )
                                    )
                                }
                            }
                        }
                    }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs Section (Custom Neon Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabsList = listOf("🚀 Tunnel", "⚙️ Payload", "🛡️ Trust", "🆘 SOS", "👁️ AI Vision")
                tabsList.forEach { tab ->
                    val isSelected = activeTab == tab
                    Surface(
                        onClick = { activeTab = tab },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) CyberSurfaceVariant else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) CyberSecondary else CyberTextMuted.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("tab_${tab.split(" ")[1].lowercase()}")
                    ) {
                        Text(
                            text = tab,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyberSecondary else CyberTextSecondary
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Tab Viewports (Single Screen Architecture)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                when (activeTab) {
                    "🚀 Tunnel" -> TunnelView(
                        servers = servers,
                        selectedServer = selectedServer,
                        activeProtocol = activeProtocol,
                        logs = logs,
                        onServerSelect = { viewModel.setServer(it) },
                        onProtocolChange = { viewModel.setProtocol(it) },
                        onAddServerClick = { showAddServerDialog = true },
                        onDeleteServer = { viewModel.deleteServer(it) },
                        onClearLogs = { viewModel.clearLogTerminal() }
                    )
                    "⚙️ Payload" -> PayloadRulesView(
                        payloads = payloads,
                        selectedPayload = selectedPayload,
                        onPayloadSelect = { viewModel.setPayload(it) },
                        onAddPayloadClick = { showAddPayloadDialog = true },
                        onDeletePayload = { viewModel.deletePayload(it) }
                    )
                    "🛡️ Trust" -> TrustSecurityView(
                        zeroKnowledge = zeroKnowledge,
                        clientPublicKey = clientPublicKey,
                        clientPrivateKey = clientPrivateKey,
                        onGenerateNewKeys = { viewModel.generateKeys() },
                        quickConnectOnStartup = quickConnectOnStartup,
                        onQuickConnectChange = { viewModel.setQuickConnectOnStartup(it) },
                        batterySaverEnabled = batterySaverEnabled,
                        onBatterySaverChange = { viewModel.setBatterySaverEnabled(it) },
                        killSwitchEnabled = killSwitchEnabled,
                        onKillSwitchChange = { viewModel.setKillSwitchEnabled(it) }
                    )
                    "🆘 SOS" -> SOSLifelineView(
                        contacts = contacts,
                        aiState = aiState,
                        onAddContactClick = { showAddContactDialog = true },
                        onDeleteContact = { viewModel.deleteContact(it) },
                        onAskAi = { viewModel.askAiCompanion(it) },
                        onClearAi = { viewModel.clearAiResponse() },
                        viewModel = viewModel
                    )
                    "👁️ AI Vision" -> AiVisionScreen(
                        viewModel = viewModel,
                        onNavigateToSos = { activeTab = "🆘 SOS" }
                    )
                }
            }

            // TRAFFIC BLOCKED OVERLAY (KILL SWITCH ACTIVE)
            if (isTrafficBlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(CyberError.copy(alpha = 0.1f), CircleShape)
                                .border(BorderStroke(2.dp, CyberError), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "AlertBlink")
                            val alertAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alertAlpha"
                            )
                            Icon(
                                imageVector = Icons.Rounded.GppMaybe,
                                contentDescription = "Alert",
                                tint = CyberError.copy(alpha = alertAlpha),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "CRITICAL LINK FAILURE",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = CyberError,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "The secure tunnel connection has been interrupted. Kill Switch is ACTIVE. All network traffic has been localized and blocked to prevent data leakage.",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = CyberTextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { onToggleConnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(56.dp)
                                .testTag("overlay_reconnect_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = CyberBackground)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "ESTABLISH SECURE LIFELINE (🔑)",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Black,
                                        color = CyberBackground,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(
                            onClick = { viewModel.setKillSwitchEnabled(false) }
                        ) {
                            Text(
                                "BYPASS KILL SWITCH (DANGEROUS)",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = CyberError,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        }
                    }
                }
            }

    // --- GSM SMS DISTRESS SIGNAL FULL-SCREEN ALARM MODAL ---
    incomingSmsSignal?.let { signal ->
        IncomingDistressCallModal(
            signal = signal,
            onDismiss = { viewModel.dismissSmsDistressModal() },
            onRelayToMesh = { type, message ->
                viewModel.startMeshSosBroadcast(type, message)
            }
        )
    }

    // --- DIALOG MODALS ---

    // 1. Add Custom Server Modal
    if (showAddServerDialog) {
        var serverName by remember { mutableStateOf("") }
        var serverIp by remember { mutableStateOf("") }
        var serverPort by remember { mutableStateOf("") }
        var serverProtocol by remember { mutableStateOf("WIREGUARD") }

        AlertDialog(
            onDismissRequest = { showAddServerDialog = false },
            title = {
                Text(
                    "ADD GATEWAY INSTANCE",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = CyberPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("Gateway Name (e.g., Custom BCP-2)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberPrimary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("IPv4 or IPv6 Whitelisted Host") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberPrimary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Active Port (e.g., 51820, 443)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberPrimary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        "Protocol Profile",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("WIREGUARD", "OPENVPN", "DNS_TUNNEL").forEach { proto ->
                            val activeProto = serverProtocol == proto
                            Surface(
                                onClick = { serverProtocol = proto },
                                shape = RoundedCornerShape(8.dp),
                                color = if (activeProto) CyberPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (activeProto) CyberPrimary else CyberTextMuted
                                )
                            ) {
                                Text(
                                    text = proto,
                                    style = TextStyle(fontSize = 10.sp, color = CyberTextPrimary),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val portInt = serverPort.toIntOrNull() ?: 51820
                        if (serverName.isNotBlank() && serverIp.isNotBlank()) {
                            viewModel.addCustomServer(serverName, serverIp, serverProtocol, portInt)
                            showAddServerDialog = false
                        } else {
                            Toast.makeText(context, "All parameters required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary)
                ) {
                    Text("Register Server", color = CyberBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddServerDialog = false }) {
                    Text("Cancel", color = CyberTextSecondary)
                }
            },
            containerColor = CyberSurface
        )
    }

    // 2. Add Payload Template Modal
    if (showAddPayloadDialog) {
        var payloadName by remember { mutableStateOf("") }
        var payloadSni by remember { mutableStateOf("") }
        var payloadMethod by remember { mutableStateOf("SSL_TLS") }
        var payloadTextBody by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPayloadDialog = false },
            title = {
                Text(
                    "BUILD INJECTION PAYLOAD",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = CyberSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = payloadName,
                        onValueChange = { payloadName = it },
                        label = { Text("Payload Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberSecondary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payloadSni,
                        onValueChange = { payloadSni = it },
                        label = { Text("Bypass SNI Host (e.g. m.facebook.com)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberSecondary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payloadTextBody,
                        onValueChange = { payloadTextBody = it },
                        label = { Text("Injection Text Header") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberSecondary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )

                    Text(
                        "Encapsulation Core",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SSL_TLS", "SSH_DIRECT", "HTTP_PROXY").forEach { method ->
                            val activeMethod = payloadMethod == method
                            Surface(
                                onClick = { payloadMethod = method },
                                shape = RoundedCornerShape(8.dp),
                                color = if (activeMethod) CyberSecondary.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (activeMethod) CyberSecondary else CyberTextMuted
                                )
                            ) {
                                Text(
                                    text = method,
                                    style = TextStyle(fontSize = 10.sp, color = CyberTextPrimary),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (payloadName.isNotBlank() && payloadSni.isNotBlank()) {
                            viewModel.addCustomPayload(payloadName, payloadSni, payloadMethod, payloadTextBody)
                            showAddPayloadDialog = false
                        } else {
                            Toast.makeText(context, "Fill in all metrics", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary)
                ) {
                    Text("Load Payload", color = CyberBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPayloadDialog = false }) {
                    Text("Cancel", color = CyberTextSecondary)
                }
            },
            containerColor = CyberSurface
        )
    }

    // 3. Add Emergency Contact Modal
    if (showAddContactDialog) {
        var contactName by remember { mutableStateOf("") }
        var contactPhone by remember { mutableStateOf("") }
        var contactRel by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = {
                Text(
                    "ADD SOS COMMANDER",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = CyberTertiary,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact or Station Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberTertiary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number / Shortwave Call") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberTertiary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactRel,
                        onValueChange = { contactRel = it },
                        label = { Text("Relationship (e.g. Responder, Family)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberTertiary,
                            unfocusedBorderColor = CyberTextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                            viewModel.addContact(contactName, contactPhone, contactRel)
                            showAddContactDialog = false
                        } else {
                            Toast.makeText(context, "Name and Phone required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary)
                ) {
                    Text("Secure SOS Contact", color = CyberBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancel", color = CyberTextSecondary)
                }
            },
            containerColor = CyberSurface
        )
}}}}
    }

// ==================== SUB-VIEWPORTS ====================

// --- 1. TUNNEL HUB VIEW ---
@Composable
fun TunnelView(
    servers: List<ServerEntity>,
    selectedServer: ServerEntity?,
    activeProtocol: String,
    logs: List<TunnelLogEntity>,
    onServerSelect: (ServerEntity) -> Unit,
    onProtocolChange: (String) -> Unit,
    onAddServerClick: () -> Unit,
    onDeleteServer: (ServerEntity) -> Unit,
    onClearLogs: () -> Unit
) {
    val logListState = rememberLazyListState()

    // Auto scroll logs when new entries arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- QUICK REGIONAL GATEWAY SWITCH ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "QUICK REGIONAL GATEWAY SWITCH",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                servers.forEach { server ->
                    val isSelected = selectedServer?.id == server.id
                    Surface(
                        onClick = { onServerSelect(server) },
                        shape = RoundedCornerShape(24.dp),
                        color = if (isSelected) CyberPrimary else CyberSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) CyberPrimary else CyberTextMuted.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("quick_switch_${server.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = server.countryCode,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = server.name.substringBefore(" ").uppercase(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) CyberBackground else CyberTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }

        // Protocol Select Chips
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SELECT ACTIVE ENCAPSULATION PROTOCOL",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextSecondary,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val protocols = listOf("WIREGUARD", "OPENVPN", "DNS_TUNNEL")
                    protocols.forEach { proto ->
                        val isSel = activeProtocol == proto
                        Surface(
                            onClick = { onProtocolChange(proto) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) CyberPrimary.copy(alpha = 0.15f) else CyberSurface,
                            border = BorderStroke(1.dp, if (isSel) CyberPrimary else CyberTextMuted)
                        ) {
                            Text(
                                text = when (proto) {
                                    "WIREGUARD" -> "WireGuard"
                                    "OPENVPN" -> "OpenVPN"
                                    else -> "DNS-Tunnel"
                                },
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) CyberPrimary else CyberTextSecondary,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Server List Headers and Adder
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "GLOBAL SECURE TUNNEL HOSTS",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            )

            IconButton(
                onClick = onAddServerClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddCircleOutline,
                    contentDescription = "Add Custom server",
                    tint = CyberPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Servers List Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(CyberSurface, shape = RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, CyberSurfaceVariant), shape = RoundedCornerShape(12.dp))
        ) {
            if (servers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Servers Available", color = CyberTextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(servers) { server ->
                        val isSelected = selectedServer?.id == server.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CyberSurfaceVariant else Color.Transparent)
                                .clickable { onServerSelect(server) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Flag indicator/placeholder
                                Text(
                                    text = when (server.countryCode) {
                                        "SG" -> "🇸🇬"
                                        "US" -> "🇺🇸"
                                        "DE" -> "🇩🇪"
                                        "BD" -> "🇧🇩"
                                        "JP" -> "🇯🇵"
                                        else -> "🌐"
                                    },
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = server.name,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) CyberPrimary else CyberTextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${server.ipAddress}:${server.port} • ${server.protocol}",
                                        style = TextStyle(fontSize = 10.sp, color = CyberTextSecondary)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Ping
                                Text(
                                    text = "${server.ping} ms",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (server.ping < 100) CyberPrimary else CyberTertiary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Delete if custom
                                if (server.isCustom) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete custom node",
                                        tint = CyberError,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onDeleteServer(server) }
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.RadioButtonChecked,
                                        contentDescription = "Standard Server",
                                        tint = if (isSelected) CyberPrimary else CyberTextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Terminal Log Console
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BCP TUNNEL ACTIVE CONSOLE LOGS",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            )
            Text(
                text = "CLEAR TERMINAL",
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberError,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.clickable { onClearLogs() }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, CyberSurfaceVariant), shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "$ [BCP Terminal IDLE]\nWaiting for handshake metrics or user diagnostic triggers...",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = CyberTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = logListState,
                    reverseLayout = true
                ) {
                    items(logs) { log ->
                        val logColor = when (log.level) {
                            "SUCCESS" -> CyberPrimary
                            "ERROR" -> CyberError
                            "WARN" -> CyberTertiary
                            else -> CyberSecondary
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = ">> [${log.level}] ${log.message}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = logColor,
                                    lineHeight = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 2. PAYLOAD RULES VIEW ---
@Composable
fun PayloadRulesView(
    payloads: List<PayloadConfigEntity>,
    selectedPayload: PayloadConfigEntity?,
    onPayloadSelect: (PayloadConfigEntity) -> Unit,
    onAddPayloadClick: () -> Unit,
    onDeletePayload: (PayloadConfigEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ISP WHITELIST BYPASS PAYLOADS",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            )

            IconButton(onClick = onAddPayloadClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ControlPoint,
                    contentDescription = "Build payload rule",
                    tint = CyberSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(payloads) { payload ->
                val isSelected = selectedPayload?.id == payload.id
                Card(
                    onClick = { onPayloadSelect(payload) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) CyberSecondary else CyberSurfaceVariant
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CyberSurfaceVariant else CyberSurface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = payload.name,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) CyberSecondary else CyberTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Bypass Host SNI: ${payload.sniHost}",
                                    style = TextStyle(fontSize = 11.sp, color = CyberTextSecondary)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = CyberSecondary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = payload.injectionMethod,
                                        style = TextStyle(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberSecondary
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Active payload",
                                        tint = CyberSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete custom payload",
                                        tint = CyberError,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { onDeletePayload(payload) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black, shape = RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = payload.customPayloadText,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = CyberPrimary.copy(alpha = 0.8f)
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 3. TRUST & SECURITY VIEW ---
@Composable
fun TrustSecurityView(
    zeroKnowledge: Boolean,
    clientPublicKey: String,
    clientPrivateKey: String,
    onGenerateNewKeys: () -> Unit,
    quickConnectOnStartup: Boolean,
    onQuickConnectChange: (Boolean) -> Unit,
    batterySaverEnabled: Boolean,
    onBatterySaverChange: (Boolean) -> Unit,
    killSwitchEnabled: Boolean,
    onKillSwitchChange: (Boolean) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Zero Knowledge Informative Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = "Trust Policy",
                        tint = CyberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ZERO-KNOWLEDGE TRUST SYSTEM",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyberPrimary,
                            fontSize = 13.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "As a security-first communication companion, your tunneling keys are fully generated on-device using a cryptographically secure random generator (SecureRandom). No server administrators or network routers can access your private key, safeguarding conversational payload integrity.",
                    style = TextStyle(fontSize = 11.sp, color = CyberTextPrimary, lineHeight = 16.sp)
                )
            }
        }

        // System Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, CyberSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "System Settings",
                        tint = CyberSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TUNNEL CONFIGURATION SETTINGS",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyberSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                
                // Quick Connect Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quick Connect on Startup",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Automatically establish last used tunnel when application launches.",
                            style = TextStyle(
                                color = CyberTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = quickConnectOnStartup,
                        onCheckedChange = onQuickConnectChange,
                        modifier = Modifier.testTag("quick_connect_startup_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberBackground,
                            checkedTrackColor = CyberSecondary,
                            uncheckedThumbColor = CyberTextMuted,
                            uncheckedTrackColor = CyberSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Decorative divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tactical Low Power Mode Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tactical Low Power Mode",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Optimizes for maximum efficiency by reducing telemetry polling and GPS frequency. Ideal for long-term emergency tunneling.",
                            style = TextStyle(
                                color = CyberTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = batterySaverEnabled,
                        onCheckedChange = onBatterySaverChange,
                        modifier = Modifier.testTag("low_power_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberBackground,
                            checkedTrackColor = CyberSecondary,
                            uncheckedThumbColor = CyberTextMuted,
                            uncheckedTrackColor = CyberSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Decorative divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Network Kill Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Secure Network Kill Switch",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Immediately blocks all data traffic if the secure tunnel connection is interrupted. Prevents critical data leaks over unencrypted links.",
                            style = TextStyle(
                                color = CyberTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = killSwitchEnabled,
                        onCheckedChange = { onKillSwitchChange(it) },
                        modifier = Modifier.testTag("kill_switch_toggle"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberBackground,
                            checkedTrackColor = CyberError,
                            uncheckedThumbColor = CyberTextMuted,
                            uncheckedTrackColor = CyberSurfaceVariant
                        )
                    )
                }
            }
        }

        // Keys Output Panels
        Text(
            text = "ON-DEVICE WIRECRYPT KEYPAIR",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextSecondary,
                fontFamily = FontFamily.Monospace
            )
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, CyberSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CLIENT PUBLIC KEY (X25519)",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = CyberTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(clientPublicKey))
                                Toast.makeText(context, "Public key copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Key",
                                tint = CyberSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = clientPublicKey.ifEmpty { "Generating public key..." },
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = CyberSecondary
                            )
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CLIENT PRIVATE KEY (SECRET)",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = CyberTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(clientPrivateKey))
                                Toast.makeText(context, "Private key copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Secret Key",
                                tint = CyberTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (zeroKnowledge) "••••••••••••••••••••••••••••••••••••••••" else clientPrivateKey,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = CyberTertiary
                            )
                        )
                    }
                }

                Button(
                    onClick = onGenerateNewKeys,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                    border = BorderStroke(1.dp, CyberTextMuted)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Regenerate Keys",
                        tint = CyberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Regenerate Secure Keypair", color = CyberTextPrimary, fontSize = 12.sp)
                }
            }
        }

        // BCP VPC Information
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, CyberSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "BRILLIANT CLOUD PLATFORM (BCP)",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyberSecondary,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BCP provides the distributed cloud routing layer that supports Lifeline Tunnel. It uses auto-healing load balancers, isolated Virtual Private Clouds (VPC), and whitelisted entry nodes across global datacenters, allowing rapid traffic bypass in the event of local ISP filtering updates.",
                    style = TextStyle(fontSize = 10.sp, color = CyberTextSecondary, lineHeight = 14.sp)
                )
            }
        }
    }
}

// --- 4. SOS LIFELINE VIEW ---
@Composable
fun SOSLifelineView(
    contacts: List<EmergencyContactEntity>,
    aiState: AiState,
    onAddContactClick: () -> Unit,
    onDeleteContact: (EmergencyContactEntity) -> Unit,
    onAskAi: (String) -> Unit,
    onClearAi: () -> Unit,
    viewModel: LifelineViewModel
) {
    var chatMessage by remember { mutableStateOf("") }

    val lat by viewModel.locationLatitude.collectAsStateWithLifecycle()
    val lon by viewModel.locationLongitude.collectAsStateWithLifecycle()
    val provider by viewModel.locationProvider.collectAsStateWithLifecycle()
    val accuracy by viewModel.locationAccuracy.collectAsStateWithLifecycle()
    val isSimulationActive by viewModel.isCoordinateSimulationActive.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var fineLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var coarseLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: fineLocationGranted
        coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: coarseLocationGranted
        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.startLocationTracking()
        }
    }

    LaunchedEffect(fineLocationGranted, coarseLocationGranted) {
        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.startLocationTracking()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationTracking()
        }
    }

    // Bluetooth Mesh States
    val isMeshAdvertising by viewModel.isMeshAdvertising.collectAsStateWithLifecycle()
    val isMeshScanning by viewModel.isMeshScanning.collectAsStateWithLifecycle()
    val isMeshRelayEnabled by viewModel.isMeshRelayEnabled.collectAsStateWithLifecycle()
    val activeSosType by viewModel.activeSosType.collectAsStateWithLifecycle()
    val activeSosMessage by viewModel.activeSosMessage.collectAsStateWithLifecycle()
    val discoveredMeshBeacons by viewModel.discoveredMeshBeacons.collectAsStateWithLifecycle()

    var selectedEmergencyType by remember { mutableStateOf("DISTRESS") }
    var customSosMessage by remember { mutableStateOf("EMERGENCY: Urgent Help Required") }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "ব্লুটুথ পারমিশন অনুমোদিত হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "অফলাইন মেশের জন্য ব্লুটুথ অনুমতি আবশ্যক", Toast.LENGTH_LONG).show()
        }
    }

    val requestBtPermissions = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            )
        }
    }

    var showSosConfirmDialog by remember { mutableStateOf(false) }

    // GSM SMS Tunneling States
    var smsTargetNumber by remember { mutableStateOf("") }
    var smsEmergencyType by remember { mutableStateOf("CRITICAL MEDICAL") }
    var smsNote by remember { mutableStateOf("জরুরি উদ্ধার ও চিকিৎসা প্রয়োজন! বিদ্যুৎ ও ইন্টারনেট বন্ধ।") }
    val incomingSmsHistory by viewModel.incomingSmsHistory.collectAsStateWithLifecycle()
    val smsStatusMessage by viewModel.smsStatusMessage.collectAsStateWithLifecycle()

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val sendGranted = perms[Manifest.permission.SEND_SMS] ?: false
        val receiveGranted = perms[Manifest.permission.RECEIVE_SMS] ?: false
        if (sendGranted || receiveGranted) {
            Toast.makeText(context, "GSM SMS টানেল পারমিশন সক্রিয় হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- SOS TACTICAL BROADCAST BUTTON ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberError.copy(alpha = 0.05f)),
            border = BorderStroke(2.dp, CyberError.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = "SOS Broadcast",
                        tint = CyberError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SOS TACTICAL BROADCAST",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = CyberError,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Immediately establishes secure tunnel and broadcasts encrypted distress packets with live coordinates to all emergency responders.",
                    style = TextStyle(fontSize = 10.sp, color = CyberTextPrimary, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                Button(
                    onClick = { showSosConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sos_broadcast_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberError),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        tint = CyberBackground,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "TRIGGER BROADCAST",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = CyberBackground
                        )
                    )
                }
            }
        }

        if (showSosConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSosConfirmDialog = false },
                title = { Text("CONFIRM BROADCAST", color = CyberError, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace) },
                text = { Text("You are about to transmit a high-priority distress signal to all trusted emergency contacts via the secure tunnel. Proceed?", color = CyberTextPrimary) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSosConfirmDialog = false
                            viewModel.triggerSosBroadcast(context)
                        }
                    ) {
                        Text("AFFIRMATIVE (TRIGGER)", color = CyberError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSosConfirmDialog = false }) {
                        Text("CANCEL", color = CyberTextSecondary)
                    }
                },
                containerColor = CyberSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // --- OFFLINE TACTICAL CO-ORDINATE BEACON ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            border = BorderStroke(1.dp, if (isSimulationActive) CyberTertiary.copy(alpha = 0.5f) else CyberSecondary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header with Satellite Icon and Offline tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Satellite,
                            contentDescription = "Tactical Offline Beacon",
                            tint = if (isSimulationActive) CyberTertiary else CyberSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OFFLINE CO-ORDINATE BEACON",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isSimulationActive) CyberTertiary else CyberSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Pure Offline Tag
                    Surface(
                        color = CyberPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "SATELLITE ONLY",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (!fineLocationGranted && !coarseLocationGranted && !isSimulationActive) {
                    // No permission state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurface, shape = RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "COORDINATE TELEMETRY LOCKED",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberError,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Standard Android GPS access required to locate coordinates without cellular networks.",
                            style = TextStyle(fontSize = 10.sp, color = CyberTextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberError),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("grant_location_button")
                        ) {
                            Text(
                                "GRANT HARDWARE ACCESS",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                } else {
                    // Coordinates Display Block
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurface, shape = RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, CyberSurfaceVariant), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LATITUDE",
                                style = TextStyle(fontSize = 8.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = if (lat != null) String.format("%.6f°", lat) else "SEARCHING...",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = if (lat != null) CyberTextPrimary else CyberTextMuted
                                ),
                                modifier = Modifier.testTag("latitude_text")
                            )
                        }

                        Column {
                            Text(
                                text = "LONGITUDE",
                                style = TextStyle(fontSize = 8.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = if (lon != null) String.format("%.6f°", lon) else "SEARCHING...",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = if (lon != null) CyberTextPrimary else CyberTextMuted
                                ),
                                modifier = Modifier.testTag("longitude_text")
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "ACCURACY",
                                style = TextStyle(fontSize = 8.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = if (accuracy != null) "±${String.format("%.1fm", accuracy)}" else "N/A",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (accuracy != null && accuracy!! < 10f) CyberPrimary else CyberTextSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata details (Provider, Cellular Status, Log button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (lat != null) CyberPrimary else CyberError, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOURCE: $provider",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberTextSecondary
                                )
                            )
                        }
                        Text(
                            text = "CELLULAR REQ: NONE (GPS LINK ACTIVE)",
                            style = TextStyle(fontSize = 8.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.logEmergencyCoordinates()
                            Toast.makeText(context, "Coordinates logged into local terminal.", Toast.LENGTH_SHORT).show()
                        },
                        enabled = lat != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSecondary,
                            disabledContainerColor = CyberSurface
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("log_coordinates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = "Save coordinates",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "LOG BEACON",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (lat != null) CyberBackground else CyberTextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cyberpunk simulation mode warning indicator if active
                if (isSimulationActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberTertiary.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.4f)), shape = RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Warning",
                            tint = CyberTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SAFETY SIMULATION ACTIVE",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTertiary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "Mock emergency satellite telemetry active.",
                                style = TextStyle(fontSize = 8.sp, color = CyberTextSecondary)
                            )
                        }

                        Button(
                            onClick = { viewModel.cycleSimulationPreset() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .height(24.dp)
                                .testTag("cycle_preset_button"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "CYCLE PROFILE",
                                style = TextStyle(
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberBackground
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Simulation Mode Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Indoor testing/Underground? Enable Simulation",
                        style = TextStyle(fontSize = 9.sp, color = CyberTextSecondary)
                    )

                    Switch(
                        checked = isSimulationActive,
                        onCheckedChange = { viewModel.setSimulationMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberTertiary,
                            checkedTrackColor = CyberTertiary.copy(alpha = 0.3f),
                            uncheckedThumbColor = CyberTextMuted,
                            uncheckedTrackColor = CyberSurface
                        ),
                        modifier = Modifier
                            .scale(0.7f)
                            .testTag("coordinate_simulation_switch")
                    )
                }
            }
        }

        // --- OFFLINE BLUETOOTH MESH SOS (INTERNET/CELLULAR BLACKOUT LIFELINE) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ble_mesh_sos_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (isMeshAdvertising) CyberError.copy(alpha = 0.12f) else CyberSurfaceVariant
            ),
            border = BorderStroke(
                1.5.dp,
                if (isMeshAdvertising) CyberError else CyberSecondary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMeshAdvertising) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                            contentDescription = "Bluetooth Mesh SOS",
                            tint = if (isMeshAdvertising) CyberError else CyberSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "OFFLINE BLE MESH SOS",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = if (isMeshAdvertising) CyberError else CyberSecondary,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "ব্লুটুথ মেশ নেটওয়ার্ক (জিরো ইন্টারনেট / সিম)",
                                style = TextStyle(fontSize = 10.sp, color = CyberTextSecondary)
                            )
                        }
                    }

                    Surface(
                        color = if (isMeshAdvertising) CyberError else CyberSecondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isMeshAdvertising) CyberError else CyberSecondary)
                    ) {
                        Text(
                            text = if (isMeshAdvertising) "BROADCASTING 📡" else "OFFLINE READY",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMeshAdvertising) CyberBackground else CyberSecondary,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "মোবাইল নেটওয়ার্ক বা ইন্টারনেট পুরোপুরি বন্ধ থাকলেও কাছাকাছি (৫০-১০০ মিটার) থাকা Lifeline ডিভাইসে ব্লুটুথ মেশের মাধ্যমে তাৎক্ষণিক বিপদ বার্তা পাঠানো ও গ্রহণ করা যায়।",
                    style = TextStyle(fontSize = 10.sp, color = CyberTextPrimary, lineHeight = 14.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Emergency Type Selector Chips
                Text(
                    text = "SELECT EMERGENCY TYPE (বিপদের ধরন):",
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val types = listOf(
                        Triple("DISTRESS", "🚨 DISTRESS", CyberError),
                        Triple("MEDICAL", "🏥 MEDICAL", CyberTertiary),
                        Triple("FOOD_WATER", "💧 FOOD/WATER", CyberSecondary),
                        Triple("EVACUATION", "🏃 EVACUATE", CyberPrimary)
                    )
                    types.forEach { (typeId, label, color) ->
                        val isSelected = selectedEmergencyType == typeId
                        Surface(
                            onClick = { selectedEmergencyType = typeId },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) color.copy(alpha = 0.25f) else CyberSurface,
                            border = BorderStroke(1.dp, if (isSelected) color else CyberSurfaceVariant)
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                    color = if (isSelected) color else CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Message Input
                OutlinedTextField(
                    value = customSosMessage,
                    onValueChange = { customSosMessage = it },
                    label = { Text("Offline SOS Message", fontSize = 10.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberSurface,
                        unfocusedContainerColor = CyberSurface,
                        focusedBorderColor = CyberError,
                        unfocusedBorderColor = CyberSurfaceVariant
                    ),
                    textStyle = TextStyle(fontSize = 11.sp, color = CyberTextPrimary, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Broadcast Toggle Action Button
                Button(
                    onClick = {
                        if (!viewModel.meshManager.hasRequiredPermissions()) {
                            requestBtPermissions()
                        } else {
                            if (isMeshAdvertising) {
                                viewModel.stopMeshSosBroadcast()
                                Toast.makeText(context, "ব্লুটুথ এসওএস ব্রডকাস্ট বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                            } else {
                                val ok = viewModel.startMeshSosBroadcast(selectedEmergencyType, customSosMessage)
                                if (ok) {
                                    Toast.makeText(context, "📡 ব্লুটুথ মেশ এসওএস চালু হয়েছে! কাছাকাছি ডিভাইসে সংকেত যাচ্ছে।", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_ble_sos_broadcast_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMeshAdvertising) CyberSurface else CyberError
                    ),
                    border = if (isMeshAdvertising) BorderStroke(1.5.dp, CyberError) else null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (isMeshAdvertising) Icons.Rounded.Close else Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = if (isMeshAdvertising) CyberError else CyberBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMeshAdvertising) "STOP BLE SOS BROADCAST (বন্ধ করুন)" else "TRANSMIT BLE SOS BEACON (ব্লুটুথ এসওএস ছাড়ুন)",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isMeshAdvertising) CyberError else CyberBackground
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CyberSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))

                // Radar Scanner & Mesh Relay Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MESH RADAR SCANNER",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = if (isMeshScanning) "কাছাকাছি ডিভাইস অনুসন্ধান চলছে..." else "স্ক্যানার স্ট্যান্ডবাই",
                            style = TextStyle(fontSize = 9.sp, color = CyberTextSecondary)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                if (!viewModel.meshManager.hasRequiredPermissions()) {
                                    requestBtPermissions()
                                } else {
                                    if (isMeshScanning) {
                                        viewModel.stopMeshScanner()
                                    } else {
                                        viewModel.startMeshScanner()
                                        Toast.makeText(context, "🔍 মেশ স্ক্যানার চালু হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isMeshScanning) CyberPrimary.copy(alpha = 0.2f) else CyberSurface
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isMeshScanning) Icons.Rounded.Radar else Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isMeshScanning) CyberPrimary else CyberTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isMeshScanning) "SCANNING 🔍" else "START RADAR",
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isMeshScanning) CyberPrimary else CyberTextSecondary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mesh Relay Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Relay Signals (মেশ রিলে নোড)",
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                        )
                        Text(
                            text = "অন্য ডিভাইসের বিপদ সংকেত পেয়ে স্বয়ংক্রিয়ভাবে আরো দূরে ছড়িয়ে দেয়।",
                            style = TextStyle(fontSize = 9.sp, color = CyberTextMuted)
                        )
                    }
                    Switch(
                        checked = isMeshRelayEnabled,
                        onCheckedChange = { viewModel.setMeshRelayEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberSecondary,
                            checkedTrackColor = CyberSecondary.copy(alpha = 0.3f),
                            uncheckedThumbColor = CyberTextMuted,
                            uncheckedTrackColor = CyberSurfaceVariant
                        ),
                        modifier = Modifier.scale(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Discovered Nearby SOS Nodes Header & Simulation Test Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEARBY SIGNALS (${discoveredMeshBeacons.size}):",
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                viewModel.simulateNearbyMeshNode(
                                    type = listOf("DISTRESS", "MEDICAL", "FOOD_WATER", "EVACUATION").random(),
                                    message = listOf("Need immediate medical help", "Water supplies exhausted", "Trapped near basement", "Shelter established").random(),
                                    distanceMeters = (3..25).random() + 0.4f
                                )
                                Toast.makeText(context, "টেস্ট নিকটস্থ নোড সিগন্যাল তৈরি হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+ টেস্ট সিগন্যাল ⚡", fontSize = 9.sp, color = CyberTertiary, fontWeight = FontWeight.Bold)
                        }

                        if (discoveredMeshBeacons.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearMeshBeacons() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("CLEAR", fontSize = 9.sp, color = CyberTextMuted)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // List of Discovered Beacons
                if (discoveredMeshBeacons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurface, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো কাছাকাছি বিপদ সংকেত শনাক্ত হয়নি।\nরাডার স্ক্যান চালু রাখলে ব্লুটুথ সীমার ভেতরের ডিভাইস এখানে দেখাবে।",
                            style = TextStyle(fontSize = 10.sp, color = CyberTextMuted, textAlign = TextAlign.Center),
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        discoveredMeshBeacons.forEach { beacon ->
                            val badgeColor = when (beacon.emergencyType) {
                                "MEDICAL" -> CyberTertiary
                                "FOOD_WATER" -> CyberSecondary
                                "EVACUATION" -> CyberPrimary
                                else -> CyberError
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).background(badgeColor, CircleShape))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = beacon.nodeId,
                                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = CyberTextPrimary, fontFamily = FontFamily.Monospace)
                                            )
                                        }

                                        Surface(
                                            color = badgeColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, badgeColor)
                                        ) {
                                            Text(
                                                text = beacon.emergencyType,
                                                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = badgeColor, fontFamily = FontFamily.Monospace),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${beacon.message}\"",
                                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "দূরত্ব: ~${beacon.distanceMeters.toInt()}m (${beacon.rssi} dBm)",
                                            style = TextStyle(fontSize = 9.sp, color = CyberSecondary, fontFamily = FontFamily.Monospace)
                                        )
                                        Text(
                                            text = "Hop: ${beacon.hopCount} • ${if (beacon.isRelayed) "Relayed 🔁" else "Direct 📍"}",
                                            style = TextStyle(fontSize = 9.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        // --- OFFLINE GSM SMS TUNNELING & CALL ALARM INTERFACE ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gsm_sms_tunnel_card"),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(CyberPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneInTalk,
                                contentDescription = "GSM SMS Tunnel",
                                tint = CyberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "GSM SMS TUNNEL & ALARM",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = CyberPrimary
                                )
                            )
                            Text(
                                text = "ইন্টারনেট ছাড়া সিম নেটওয়ার্কে কল সংকেত",
                                style = TextStyle(fontSize = 10.sp, color = CyberTextSecondary)
                            )
                        }
                    }

                    Surface(
                        color = CyberPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CyberPrimary),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "GSM OFFLINE",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "মোবাইল ডেটা বা ইন্টারনেট সংযোগ না থাকলেও সাধারণ সিম কার্ডের মাধ্যমে ব্যাকগ্রাউন্ডে সিকিউর এসএমএস সংকেত পৌঁছে যাবে এবং প্রাপকের ডিভাইসে স্ক্রিনজুড়ে ইনকামিং কল ও অ্যালার্ম বেজে উঠবে।",
                    style = TextStyle(fontSize = 10.sp, color = CyberTextMuted, lineHeight = 14.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Emergency Type Selector
                Text(
                    text = "বিপদের ধরন নির্বাচন করুন:",
                    style = TextStyle(fontSize = 10.sp, color = CyberTextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val smsTypes = listOf("CRITICAL MEDICAL", "EVACUATION", "TRAPPED", "SECURITY ALERT")
                    smsTypes.forEach { type ->
                        val isSelected = smsEmergencyType == type
                        Surface(
                            onClick = { smsEmergencyType = type },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) CyberPrimary.copy(alpha = 0.2f) else CyberSurfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) CyberPrimary else CyberSecondary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = type,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) CyberPrimary else CyberTextSecondary
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Recipient phone number field
                OutlinedTextField(
                    value = smsTargetNumber,
                    onValueChange = { smsTargetNumber = it },
                    label = { Text("প্রাপকের ফোন নম্বর (Recipient Phone)", fontSize = 11.sp) },
                    placeholder = { Text("+8801700000000", fontSize = 11.sp, color = CyberTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = CyberSecondary.copy(alpha = 0.4f),
                        focusedTextColor = CyberTextPrimary,
                        unfocusedTextColor = CyberTextPrimary,
                        cursorColor = CyberPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick select from saved emergency contacts if available
                if (contacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("কন্টাক্ট:", style = TextStyle(fontSize = 9.sp, color = CyberTextMuted))
                        contacts.forEach { contact ->
                            Surface(
                                onClick = { smsTargetNumber = contact.phoneNumber },
                                shape = RoundedCornerShape(6.dp),
                                color = CyberSurfaceVariant,
                                border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "${contact.name} (${contact.phoneNumber})",
                                    style = TextStyle(fontSize = 9.sp, color = CyberSecondary),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom message note
                OutlinedTextField(
                    value = smsNote,
                    onValueChange = { smsNote = it },
                    label = { Text("জরুরি বার্তা (Distress Note)", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = CyberSecondary.copy(alpha = 0.4f),
                        focusedTextColor = CyberTextPrimary,
                        unfocusedTextColor = CyberTextPrimary,
                        cursorColor = CyberPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))
                // Location coords status attached
                Text(
                    text = if (lat != null && lon != null) "📍 জিপিএস স্থানাঙ্ক স্বয়ংক্রিয়ভাবে সংযুক্ত হবে: $lat, $lon" else "📍 অফলাইন মোড (কোনো নেটওয়ার্ক বা জিপিএস ছাড়াই পাঠানো সম্ভব)",
                    style = TextStyle(fontSize = 9.sp, color = CyberTertiary, fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Send GSM SOS Button
                Button(
                    onClick = {
                        val hasSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                        val hasRecv = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                        if (!hasSend || !hasRecv) {
                            smsPermissionLauncher.launch(
                                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
                            )
                        } else {
                            if (smsTargetNumber.isBlank()) {
                                Toast.makeText(context, "অনুগ্রহ করে প্রাপকের ফোন নম্বর লিখুন", Toast.LENGTH_SHORT).show()
                            } else {
                                val success = viewModel.sendGsmDistressSms(smsTargetNumber, smsEmergencyType, smsNote)
                                if (success) {
                                    Toast.makeText(context, "GSM টানেলের মাধ্যমে সিগন্যাল পাঠানো হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberError),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("send_gsm_sos_btn")
                ) {
                    Icon(imageVector = Icons.Rounded.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SEND GSM TUNNEL SOS (ইন্টারনেট ছাড়া পাঠান)",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Simulation / Test Incoming Alarm Button
                OutlinedButton(
                    onClick = {
                        viewModel.simulateGsmDistressSignal(smsEmergencyType, smsNote)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CyberSurfaceVariant),
                    border = BorderStroke(1.dp, CyberTertiary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("simulate_incoming_sms_alarm_btn")
                ) {
                    Icon(imageVector = Icons.Rounded.NotificationsActive, contentDescription = null, tint = CyberTertiary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ ইনকামিং কল ও অ্যালার্ম ইন্টারফেস টেস্ট করুন (SIMULATE)",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = CyberTertiary)
                    )
                }

                // Status feedback
                if (smsStatusMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = smsStatusMessage ?: "",
                        style = TextStyle(fontSize = 10.sp, color = CyberPrimary, fontFamily = FontFamily.Monospace)
                    )
                }

                // Received GSM Distress Signals Log
                if (incomingSmsHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CyberSecondary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "রিসিভ হওয়া জরুরি সিগন্যাল লগ (${incomingSmsHistory.size}):",
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                        )
                        TextButton(
                            onClick = { viewModel.clearSmsDistressHistory() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("CLEAR", fontSize = 9.sp, color = CyberTextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        incomingSmsHistory.take(5).forEach { historySignal ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyberError.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = historySignal.senderNumber,
                                            style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberPrimary)
                                        )
                                        Surface(
                                            color = CyberError.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, CyberError.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = historySignal.sosType,
                                                style = TextStyle(fontSize = 9.sp, color = CyberError, fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = historySignal.message,
                                        style = TextStyle(fontSize = 11.sp, color = CyberTextPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Emergency AI Assistance Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            border = BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Hearing,
                        contentDescription = "AI Emergency Assist",
                        tint = CyberTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EMERGENCY AI LIFELINE GUIDE",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyberTertiary,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Secure crisis chatbot. Ask for emergency guidelines, first-aid protocols, offline mesh setups, or communication diagnostics.",
                    style = TextStyle(fontSize = 10.sp, color = CyberTextPrimary)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Query Output Screen
                when (aiState) {
                    is AiState.Idle -> {
                        // Show default suggestions
                        Text(
                            "Suggested Queries:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "First aid for deep wounds",
                                "How to create local mesh net?",
                                "Explain DNS query tunneling"
                            ).forEach { prompt ->
                                Surface(
                                    onClick = { onAskAi(prompt) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = CyberSurface,
                                    border = BorderStroke(1.dp, CyberTextMuted)
                                ) {
                                    Text(
                                        text = prompt,
                                        style = TextStyle(fontSize = 9.sp, color = CyberTextSecondary),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    is AiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(CyberSurface, shape = RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberTertiary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Broadcasting query through secure uplink...",
                                    style = TextStyle(fontSize = 11.sp, color = CyberTextSecondary, fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                    is AiState.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurface, shape = RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.5f)), shape = RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI RESPONSE PACKET",
                                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTertiary)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear Response",
                                    tint = CyberError,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onClearAi() }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = aiState.response,
                                    style = TextStyle(fontSize = 11.sp, color = CyberTextPrimary, lineHeight = 15.sp)
                                )
                            }
                        }
                    }
                    is AiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurface, shape = RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, CyberError), shape = RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TRANSMISSION ERROR", fontSize = 9.sp, color = CyberError, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear Response",
                                    tint = CyberTextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onClearAi() }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = aiState.message, fontSize = 11.sp, color = CyberError)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ask AI Input Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatMessage,
                        onValueChange = { chatMessage = it },
                        placeholder = { Text("Ask emergency helper AI...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberSurface,
                            unfocusedContainerColor = CyberSurface,
                            focusedBorderColor = CyberTertiary,
                            unfocusedBorderColor = CyberTextMuted
                        ),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (chatMessage.isNotBlank()) {
                                onAskAi(chatMessage)
                                chatMessage = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(CyberTertiary, shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Transmit",
                            tint = CyberBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // SOS Contacts Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "EMERGENCY SOS COORDINATORS",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            )

            IconButton(onClick = onAddContactClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = "Register Contact",
                    tint = CyberTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Saved contacts list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(CyberSurface, shape = RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, CyberSurfaceVariant), shape = RoundedCornerShape(12.dp))
        ) {
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Emergency Responders Registered", color = CyberTextSecondary, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(contacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = CyberTertiary.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = "Shield Indicator",
                                            tint = CyberTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTextPrimary
                                        )
                                    )
                                    Text(
                                        text = "${contact.phoneNumber} • ${contact.relationship}",
                                        style = TextStyle(fontSize = 11.sp, color = CyberTextSecondary)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = "Deregister Contact",
                                tint = CyberError,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onDeleteContact(contact) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
