package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AiVisionEngine
import com.example.service.HazardLevel
import com.example.service.VisionDetectionResult
import com.example.ui.theme.*
import com.example.ui.viewmodel.LifelineViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun AiVisionScreen(
    viewModel: LifelineViewModel,
    onNavigateToSos: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val visionEngine = remember { AiVisionEngine(context) }
    DisposableEffect(visionEngine) {
        onDispose {
            visionEngine.clear()
        }
    }

    val isProcessing by visionEngine.isProcessing.collectAsStateWithLifecycle()
    val latestResult by visionEngine.latestResult.collectAsStateWithLifecycle()
    val isTorchOn by visionEngine.isTorchOn.collectAsStateWithLifecycle()
    val isSpeaking by visionEngine.isSpeaking.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isLiveCameraReady by remember { mutableStateOf(false) }

    // HUD scanner animation
    val infiniteTransition = rememberInfiniteTransition(label = "hud_scan")
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_offset"
    )

    // Pulse animation for critical alerts
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_vision_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Top Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyberPrimary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = "AI Vision",
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AI VISION AGENT",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = CyberPrimary
                                    )
                                )
                                Text(
                                    text = "কম্পিউটার ভিশন ও বিপদ সনাক্তকারী ক্যামেরা",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = CyberTextSecondary
                                    )
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "OFFLINE AI",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ক্যামেরা দ্বারা আগুন, ধোঁয়া, ধ্বংসাবশেষ, আঘাত, বা বিপজ্জনক তার স্বয়ংক্রিয়ভাবে সনাক্ত করে জীবনরক্ষাকারী দিকনির্দেশনা প্রদান করে। ইন্টারনেট ছাড়াও অন-ডিভাইস অ্যালগরিদম কাজ করে।",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = CyberTextMuted,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }

        // Camera Viewfinder Box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    2.dp,
                    if (latestResult?.hazardLevel == HazardLevel.CRITICAL) CyberError
                    else if (latestResult?.hazardLevel == HazardLevel.CAUTION) CyberTertiary
                    else CyberPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture = capture

                                    try {
                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            capture
                                        )
                                        isLiveCameraReady = true
                                        // Torch sync
                                        camera.cameraControl.enableTorch(isTorchOn)
                                    } catch (_: Exception) {
                                        isLiveCameraReady = false
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Request Permission Screen Inside Viewfinder
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CyberSurfaceVariant)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideocamOff,
                                contentDescription = null,
                                tint = CyberSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "CAMERA PERMISSION REQUIRED",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CyberTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ক্যামেরা দিয়ে চারপাশের পরিবেশ ও বিপদ নিরীক্ষণ করার জন্য পারমিশন অনুমোদন করুন।",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = CyberTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.Camera, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ALLOW CAMERA", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Futuristic HUD Scanning Overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Corner Brackets
                        val bracketLen = 36.dp.toPx()
                        val strokeW = 3.dp.toPx()
                        val hudColor = Color(0xFF00FFCC).copy(alpha = 0.7f)

                        // Top Left
                        drawLine(hudColor, Offset(16.dp.toPx(), 16.dp.toPx()), Offset(16.dp.toPx() + bracketLen, 16.dp.toPx()), strokeW)
                        drawLine(hudColor, Offset(16.dp.toPx(), 16.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx() + bracketLen), strokeW)

                        // Top Right
                        drawLine(hudColor, Offset(canvasWidth - 16.dp.toPx(), 16.dp.toPx()), Offset(canvasWidth - 16.dp.toPx() - bracketLen, 16.dp.toPx()), strokeW)
                        drawLine(hudColor, Offset(canvasWidth - 16.dp.toPx(), 16.dp.toPx()), Offset(canvasWidth - 16.dp.toPx(), 16.dp.toPx() + bracketLen), strokeW)

                        // Bottom Left
                        drawLine(hudColor, Offset(16.dp.toPx(), canvasHeight - 16.dp.toPx()), Offset(16.dp.toPx() + bracketLen, canvasHeight - 16.dp.toPx()), strokeW)
                        drawLine(hudColor, Offset(16.dp.toPx(), canvasHeight - 16.dp.toPx()), Offset(16.dp.toPx(), canvasHeight - 16.dp.toPx() - bracketLen), strokeW)

                        // Bottom Right
                        drawLine(hudColor, Offset(canvasWidth - 16.dp.toPx(), canvasHeight - 16.dp.toPx()), Offset(canvasWidth - 16.dp.toPx() - bracketLen, canvasHeight - 16.dp.toPx()), strokeW)
                        drawLine(hudColor, Offset(canvasWidth - 16.dp.toPx(), canvasHeight - 16.dp.toPx()), Offset(canvasWidth - 16.dp.toPx(), canvasHeight - 16.dp.toPx() - bracketLen), strokeW)

                        // Central Target Crosshair
                        val centerX = canvasWidth / 2f
                        val centerY = canvasHeight / 2f
                        val crosshairSize = 22.dp.toPx()
                        drawLine(hudColor.copy(alpha = 0.5f), Offset(centerX - crosshairSize, centerY), Offset(centerX + crosshairSize, centerY), 2.dp.toPx())
                        drawLine(hudColor.copy(alpha = 0.5f), Offset(centerX, centerY - crosshairSize), Offset(centerX, centerY + crosshairSize), 2.dp.toPx())
                        drawCircle(hudColor.copy(alpha = 0.3f), radius = 28.dp.toPx(), center = Offset(centerX, centerY), style = Stroke(1.5.dp.toPx()))

                        // Moving Laser Scan Line
                        val lineY = canvasHeight * scanLineOffset
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF00FFCC).copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, lineY),
                            end = Offset(canvasWidth, lineY),
                            strokeWidth = 3.dp.toPx()
                        )
                    }

                    // Top Bar HUD Indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(if (isLiveCameraReady) CyberPrimary else CyberTertiary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isLiveCameraReady) "LIVE 30FPS" else "STANDBY",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = CyberSecondary
                                    )
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Flashlight / Torch toggle
                            IconButton(
                                onClick = {
                                    visionEngine.toggleTorch()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(if (isTorchOn) CyberTertiary else Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .border(1.dp, CyberSecondary.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                    contentDescription = "Torch",
                                    tint = if (isTorchOn) Color.Black else CyberTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Switch Camera (Front / Back)
                            IconButton(
                                onClick = {
                                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    else CameraSelector.DEFAULT_BACK_CAMERA
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .border(1.dp, CyberSecondary.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cameraswitch,
                                    contentDescription = "Switch Camera",
                                    tint = CyberTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Processing Loading Spinner
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CyberPrimary, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "AI NEURAL VISION PROCESSING...",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = CyberPrimary
                                    )
                                )
                                Text(
                                    text = "দৃশ্যমান ফ্রেম ও বিপদ মূল্যায়ন হচ্ছে",
                                    style = TextStyle(fontSize = 11.sp, color = CyberTextSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Action Controls (Capture + Test Presets)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Main Capture Button
                    Button(
                        onClick = {
                            val capture = imageCapture
                            if (capture != null && hasCameraPermission) {
                                val executor = Executors.newSingleThreadExecutor()
                                capture.takePicture(
                                    executor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bitmap = image.toBitmap()
                                            image.close()
                                            scope.launch {
                                                visionEngine.analyzeBitmap(bitmap)
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            // Fallback simulated scan
                                            scope.launch {
                                                val dummy = createMockBitmap(HazardLevel.CAUTION)
                                                visionEngine.analyzeBitmap(dummy)
                                            }
                                        }
                                    }
                                )
                            } else {
                                // Fallback simulated scan if no physical camera or permission
                                scope.launch {
                                    val dummy = createMockBitmap(HazardLevel.CRITICAL)
                                    visionEngine.analyzeBitmap(dummy)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("ai_vision_capture_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SCAN SCENE & DETECT HAZARDS",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "দ্রুত টেস্ট সিনারিও সিমুলেশন (Test Offline Situations):",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberTextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val bm = createMockBitmap(HazardLevel.CRITICAL)
                                    visionEngine.analyzeBitmap(bm)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberError.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔥 আগুন / ক্ষত", color = CyberError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val bm = createMockBitmap(HazardLevel.CAUTION)
                                    visionEngine.analyzeBitmap(bm)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚠️ ভাঙা তার", color = CyberTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val bm = createMockBitmap(HazardLevel.SAFE)
                                    visionEngine.analyzeBitmap(bm)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("✓ নিরাপদ পথ", color = CyberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Detection Results Card
        item {
            val result = latestResult
            if (result != null) {
                val badgeColor = when (result.hazardLevel) {
                    HazardLevel.CRITICAL -> CyberError
                    HazardLevel.CAUTION -> CyberTertiary
                    HazardLevel.SAFE -> CyberPrimary
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, badgeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Badge Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(badgeColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (result.hazardLevel) {
                                        HazardLevel.CRITICAL -> "🚨 CRITICAL HAZARD"
                                        HazardLevel.CAUTION -> "⚠️ CAUTION DETECTED"
                                        HazardLevel.SAFE -> "✓ SAFE PASSAGE"
                                    },
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = badgeColor
                                    )
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${result.confidence}% CONFIDENCE",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = badgeColor
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = result.titleBn,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary
                            )
                        )
                        Text(
                            text = result.title,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Detected Item Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            result.detectedItems.forEach { itemTag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "#$itemTag",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CyberSecondary.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "লাইফলাইন নির্দেশাবলী (Action Steps):",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        result.instructionsBn.forEachIndexed { idx, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(badgeColor.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = step,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            color = CyberTextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 17.sp
                                        )
                                    )
                                    if (idx < result.instructions.size) {
                                        Text(
                                            text = result.instructions[idx],
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                color = CyberTextMuted
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Deep Analysis output if requested
                        if (result.deepAnalysis != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Psychology,
                                            contentDescription = null,
                                            tint = CyberSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "DEEP GEMINI AI TRIAGE",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = CyberSecondary
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = result.deepAnalysis,
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = CyberTextPrimary,
                                            lineHeight = 16.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Audio & Emergency Integration Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Text to Speech
                            Button(
                                onClick = {
                                    if (isSpeaking) {
                                        visionEngine.stopSpeaking()
                                    } else {
                                        val text = result.instructionsBn.joinToString(". ")
                                        visionEngine.speakInstructions("${result.titleBn}। $text")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSpeaking) CyberError else CyberSurface
                                ),
                                border = BorderStroke(1.dp, if (isSpeaking) CyberError else CyberSecondary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = if (isSpeaking) Color.White else CyberSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSpeaking) "STOP AUDIO" else "VOICE GUIDE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSpeaking) Color.White else CyberSecondary
                                )
                            }

                            // Deep AI Gemini Analysis
                            Button(
                                onClick = {
                                    scope.launch {
                                        visionEngine.performDeepGeminiInspection("${result.title}: ${result.detectedItems.joinToString(", ")}")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, CyberSecondary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = CyberSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DEEP AI (GEMINI)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // One tap broadcast to Mesh and GSM SMS Tunnel
                        Button(
                            onClick = {
                                // Start BLE Mesh SOS and open SOS tab
                                viewModel.startMeshSosBroadcast(
                                    type = if (result.hazardLevel == HazardLevel.CRITICAL) "CRITICAL_HAZARD" else "HAZARD_ALERT",
                                    message = "${result.titleBn}: ${result.detectedItems.joinToString(", ")}"
                                )
                                onNavigateToSos()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CellTower,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BROADCAST HAZARD VIA MESH & SMS TUNNEL",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.Black
                                )
                            )
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DocumentScanner,
                            contentDescription = null,
                            tint = CyberSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "NO DETECTION YET",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CyberTextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ক্যামেরা দিয়ে স্ক্যান করুন অথবা উপরের টেস্ট বাটন চাপুন।",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = CyberTextMuted,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun createMockBitmap(hazard: HazardLevel): Bitmap {
    val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    when (hazard) {
        HazardLevel.CRITICAL -> {
            // Paint fiery red/orange background and bright spots
            paint.color = android.graphics.Color.rgb(220, 40, 20)
            canvas.drawRect(0f, 0f, 320f, 240f, paint)
            paint.color = android.graphics.Color.rgb(255, 230, 80)
            canvas.drawCircle(160f, 120f, 50f, paint)
        }
        HazardLevel.CAUTION -> {
            // High contrast broken line / dark with spark lines
            paint.color = android.graphics.Color.rgb(40, 40, 40)
            canvas.drawRect(0f, 0f, 320f, 240f, paint)
            paint.color = android.graphics.Color.rgb(255, 240, 100)
            paint.strokeWidth = 8f
            canvas.drawLine(20f, 20f, 300f, 220f, paint)
            canvas.drawLine(20f, 220f, 300f, 20f, paint)
        }
        HazardLevel.SAFE -> {
            // Neutral green / clear pathway
            paint.color = android.graphics.Color.rgb(20, 90, 60)
            canvas.drawRect(0f, 0f, 320f, 240f, paint)
            paint.color = android.graphics.Color.rgb(80, 200, 140)
            canvas.drawRect(80f, 0f, 240f, 240f, paint)
        }
    }
    return bitmap
}
