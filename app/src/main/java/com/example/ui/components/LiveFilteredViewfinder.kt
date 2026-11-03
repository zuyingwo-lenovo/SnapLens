package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.FilterEngine
import com.example.model.AspectRatioPreset
import com.example.model.FilterAdjustments
import com.example.model.FlashModePreset
import com.example.model.FrameStyle
import com.example.model.OpticalFilter
import com.example.model.StudioScene
import com.example.model.ViewfinderMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LeicaAmber
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

@SuppressLint("RestrictedApi")
@Composable
fun LiveFilteredViewfinder(
    viewfinderMode: ViewfinderMode,
    hasCameraPermission: Boolean,
    isFrontLens: Boolean,
    flashMode: FlashModePreset,
    aspectRatio: AspectRatioPreset,
    showGrid: Boolean,
    showDateStamp: Boolean,
    frameStyle: FrameStyle,
    isSplitCompareEnabled: Boolean,
    splitFraction: Float,
    selectedFilter: OpticalFilter,
    adjustments: FilterAdjustments,
    activeStudioScene: StudioScene,
    studioBitmap: Bitmap?,
    shutterPulseCount: Int,
    onSplitFractionChange: (Float) -> Unit,
    onSwipeFilter: (Int) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onSwitchToStudioCanvas: () -> Unit,
    onLatestFrameUpdated: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var liveCameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraHardwareAvailable by remember { mutableStateOf(true) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.3f) }
    val shutterFlashAlpha = remember { Animatable(0f) }

    // Trigger shutter flash animation when shutterPulseCount increments
    LaunchedEffect(shutterPulseCount) {
        if (shutterPulseCount > 0) {
            shutterFlashAlpha.snapTo(0.88f)
            shutterFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
    }

    // Animate tap-to-focus reticle
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            focusScale.snapTo(1.45f)
            focusScale.animateTo(1.0f, tween(220, easing = FastOutSlowInEasing))
            kotlinx.coroutines.delay(900)
            focusPoint = null
        }
    }

    // Bind CameraX ImageAnalysis for real-time frame streaming when in LIVE_CAMERA mode
    DisposableEffect(viewfinderMode, hasCameraPermission, isFrontLens, flashMode) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var cameraProvider: ProcessCameraProvider? = null

        if (viewfinderMode == ViewfinderMode.LIVE_CAMERA && hasCameraPermission) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    provider.unbindAll()

                    val desiredLens = if (isFrontLens) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }

                    val selector = if (provider.hasCamera(
                            CameraSelector.Builder().requireLensFacing(desiredLens).build()
                        )
                    ) {
                        CameraSelector.Builder().requireLensFacing(desiredLens).build()
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(960, 1280),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            val rawBmp = imageProxy.toBitmap()
                            val rotated = FilterEngine.rotateAndMirrorBitmap(
                                source = rawBmp,
                                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                                mirrorHorizontal = isFrontLens
                            )
                            liveCameraBitmap = rotated
                            onLatestFrameUpdated(rotated)
                            cameraHardwareAvailable = true
                        } catch (e: Exception) {
                            // Keep previous frame on transient error
                        } finally {
                            imageProxy.close()
                        }
                    }

                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        imageAnalysis
                    )
                    camera.cameraControl.enableTorch(flashMode == FlashModePreset.TORCH)
                } catch (e: Exception) {
                    cameraHardwareAvailable = false
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            analysisExecutor.shutdown()
        }
    }

    // Active bitmap to render in the viewfinder
    val activeSourceBitmap: Bitmap? = remember(
        viewfinderMode,
        hasCameraPermission,
        liveCameraBitmap,
        studioBitmap
    ) {
        if (viewfinderMode == ViewfinderMode.LIVE_CAMERA && hasCameraPermission && liveCameraBitmap != null) {
            liveCameraBitmap
        } else {
            studioBitmap
        }
    }

    LaunchedEffect(activeSourceBitmap) {
        if (activeSourceBitmap != null) {
            onLatestFrameUpdated(activeSourceBitmap)
        }
    }

    val combinedMatrixArray = remember(selectedFilter, adjustments) {
        FilterEngine.buildCombinedColorMatrix(selectedFilter, adjustments)
    }
    val composeColorFilter = remember(combinedMatrixArray) {
        ColorFilter.colorMatrix(ColorMatrix(combinedMatrixArray))
    }

    val targetRatio = aspectRatio.ratioWidth / aspectRatio.ratioHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(targetRatio.coerceIn(0.56f, 1.0f), matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF06070B))
                .border(1.dp, Color(0xFF282E42), RoundedCornerShape(24.dp))
                .testTag("camera_viewfinder_box")
                .pointerInput(isSplitCompareEnabled) {
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (isSplitCompareEnabled) {
                                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                                val nextFraction = (change.position.x / widthPx).coerceIn(0.08f, 0.92f)
                                onSplitFractionChange(nextFraction)
                            } else {
                                accumulatedDrag += dragAmount
                            }
                        },
                        onDragEnd = {
                            if (!isSplitCompareEnabled) {
                                if (accumulatedDrag > 90f) {
                                    onSwipeFilter(-1)
                                } else if (accumulatedDrag < -90f) {
                                    onSwipeFilter(1)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        focusPoint = tapOffset
                    }
                }
        ) {
            val viewWidthPx = constraints.maxWidth.toFloat()

            if (activeSourceBitmap != null) {
                val imageBitmap = remember(activeSourceBitmap) { activeSourceBitmap.asImageBitmap() }

                if (isSplitCompareEnabled) {
                    // Base Layer: Unfiltered Original
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Original Unfiltered Viewfinder",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Clipped Layer: Real-Time Filtered Output on the right of the split line (or left)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val splitX = size.width * splitFraction
                                clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Live Filtered Viewfinder",
                            contentScale = ContentScale.Crop,
                            colorFilter = composeColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                        OpticalEffectsOverlay(
                            filter = selectedFilter,
                            adjustments = adjustments
                        )
                    }
                } else {
                    // Full-frame Real-Time Filtered Viewfinder
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Live Filtered Viewfinder",
                        contentScale = ContentScale.Crop,
                        colorFilter = composeColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    OpticalEffectsOverlay(
                        filter = selectedFilter,
                        adjustments = adjustments
                    )
                }
            }

            // Anamorphic Cinema Scope Letterbox Preview Bars
            if (frameStyle == FrameStyle.CINEMA_SCOPE) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.10f)
                            .background(ObsidianBlack.copy(alpha = 0.92f))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "ANAMORPHIC 2.35:1  •  ${selectedFilter.shortCode}  •  ${selectedFilter.isoTag}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            color = LeicaAmber.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.10f)
                            .background(ObsidianBlack.copy(alpha = 0.92f))
                    )
                }
            }

            // Polaroid Instant Film Preview Frame Border
            if (frameStyle == FrameStyle.POLAROID) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(12.dp, Color(0xFFF6F2EA), RoundedCornerShape(20.dp))
                )
            }

            // Rule of Thirds Grid & Corner Viewfinder Brackets
            if (showGrid) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val gridColor = Color.White.copy(alpha = 0.22f)

                    // Thirds lines
                    drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1.dp.toPx())

                    // Center crosshair
                    val cx = w / 2f
                    val cy = h / 2f
                    val arm = 10.dp.toPx()
                    drawLine(LeicaAmber.copy(alpha = 0.55f), Offset(cx - arm, cy), Offset(cx + arm, cy), 1.5.dp.toPx())
                    drawLine(LeicaAmber.copy(alpha = 0.55f), Offset(cx, cy - arm), Offset(cx, cy + arm), 1.5.dp.toPx())
                }
            }

            // Split-Screen Loupe Divider Handle & Labels
            if (isSplitCompareEnabled) {
                val splitOffsetPx = (viewWidthPx * splitFraction).roundToInt()
                Box(modifier = Modifier.fillMaxSize()) {
                    // Vertical Divider Line
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(splitOffsetPx - 1, 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(LeicaAmber)
                    )
                    // Center Pill Handle
                    Surface(
                        shape = CircleShape,
                        color = ObsidianBlack.copy(alpha = 0.88f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LeicaAmber),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(splitOffsetPx - 20.dp.roundToPx(), 0) }
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = "Split Slider Handle",
                                tint = LeicaAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Left / Right comparison badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = ObsidianBlack.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ORIGINAL RAW",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = LeicaAmber.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = selectedFilter.shortCode,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = ObsidianBlack,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Tap-to-Focus Reticle
            focusPoint?.let { pt ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = 28.dp.toPx() * focusScale.value
                    drawCircle(
                        color = LeicaAmber,
                        radius = r,
                        center = pt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = LeicaAmber.copy(alpha = 0.35f),
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                }
            }

            // Bottom HUD Telemetry & Retro Quartz Date Stamp inside Viewfinder
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Left: Active Filter Badge + ISO Telemetry
                Surface(
                    color = ObsidianBlack.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, selectedFilter.accentColor.copy(alpha = 0.55f)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(selectedFilter.accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${selectedFilter.shortCode} • ${(adjustments.intensity * 100).roundToInt()}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

                // Right: Live Retro Quartz 35mm Date Stamp
                if (showDateStamp) {
                    val stampStr = remember {
                        SimpleDateFormat("''yy MM dd  HH:mm", Locale.US).format(Date())
                    }
                    Text(
                        text = stampStr,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFFF9100),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(ObsidianBlack.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Floating Camera Permission / Live Lens Banner when in LIVE_CAMERA mode without permission
            AnimatedVisibility(
                visible = viewfinderMode == ViewfinderMode.LIVE_CAMERA && !hasCameraPermission,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Surface(
                    color = ObsidianBlack.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LeicaAmber.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = LeicaAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Camera Access",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Previewing real-time LUTs on ${activeStudioScene.title}. Enable camera permission for live hardware lens feed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB0B8C8)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRequestCameraPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LeicaAmber,
                                    contentColor = ObsidianBlack
                                ),
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enable Live Lens", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = onSwitchToStudioCanvas,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF232838),
                                    contentColor = OpticalCyan
                                ),
                                modifier = Modifier.testTag("switch_studio_canvas_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Studio Scenes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Shutter Capture Flash Overlay
            if (shutterFlashAlpha.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = shutterFlashAlpha.value))
                )
            }
        }
    }
}

/**
 * Renders the real-time optical dual-tone tint, prism light leak, radial lens vignette,
 * and 35mm film grain over the viewfinder at 60fps.
 */
@Composable
private fun OpticalEffectsOverlay(
    filter: OpticalFilter,
    adjustments: FilterAdjustments
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Dual-tone split color overlay
        val overlayAlpha = (filter.overlayIntensity * adjustments.intensity).coerceIn(0f, 0.7f)
        if (overlayAlpha > 0.01f && (filter.overlayStartArgb != 0 || filter.overlayEndArgb != 0)) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(filter.overlayStartArgb),
                        Color(filter.overlayEndArgb)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                ),
                alpha = overlayAlpha,
                blendMode = BlendMode.Screen
            )
        }

        // 2. Prism Light Leak
        val leak = adjustments.lightLeak.coerceIn(0f, 1f)
        if (leak > 0.02f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF5F14).copy(alpha = leak * 0.78f),
                        Color(0xFFFFBE28).copy(alpha = leak * 0.48f),
                        Color(0xFFFF2878).copy(alpha = leak * 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.12f, h * 0.22f),
                    radius = max(w, h) * 0.75f
                ),
                blendMode = BlendMode.Screen
            )
        }

        // 3. Radial Optical Lens Vignette
        val vig = adjustments.vignette.coerceIn(0f, 1f)
        if (vig > 0.02f) {
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.42f to Color.Transparent,
                        0.78f to Color(0xFF08090E).copy(alpha = vig * 0.35f),
                        1.0f to Color(0xFF040508).copy(alpha = vig * 0.88f)
                    ),
                    center = Offset(w / 2f, h / 2f),
                    radius = max(w, h) * 0.72f
                )
            )
        }

        // 4. Real-Time 35mm Silver-Halide Film Grain
        val grain = adjustments.filmGrain.coerceIn(0f, 1f)
        if (grain > 0.02f) {
            val rng = Random(77)
            val count = 240
            val alpha = (grain * 0.26f).coerceIn(0.03f, 0.30f)
            for (i in 0 until count) {
                val gx = rng.nextFloat() * w
                val gy = rng.nextFloat() * h
                val speckColor = if (i % 2 == 0) {
                    Color.White.copy(alpha = alpha)
                } else {
                    Color.Black.copy(alpha = alpha)
                }
                drawCircle(
                    color = speckColor,
                    radius = 1.4.dp.toPx(),
                    center = Offset(gx, gy)
                )
            }
        }
    }
}
