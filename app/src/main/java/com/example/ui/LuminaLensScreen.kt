package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.VoiceAssistantUiState
import com.example.model.FlashModePreset
import com.example.model.StudioScene
import com.example.model.ViewfinderMode
import com.example.ui.components.DarkroomGalleryScreen
import com.example.ui.components.FilterCarouselDeck
import com.example.ui.components.LiveFilteredViewfinder
import com.example.ui.components.OpticalFineTuneDrawer
import com.example.ui.components.QuickShareStudioDialog
import com.example.ui.components.VoiceCommandDrawer
import com.example.ui.theme.DarkroomElevated
import com.example.ui.theme.DarkroomSurface
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.HalideOrange
import com.example.ui.theme.IvoryText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LeicaAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun LuminaLensScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Initialize procedural studio scenes & sample darkroom captures once
    LaunchedEffect(Unit) {
        viewModel.initializeStudioAssets(context)
    }

    // Track CAMERA runtime permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    // Track RECORD_AUDIO runtime permission for Voice Camera Commands
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        if (granted) {
            viewModel.setVoiceDrawerOpen(true)
        }
    }

    // Zero-permission Android Photo Picker for importing user photos into real-time filter studio
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhotoFromUri(context, uri)
        }
    }

    // Check initial camera & audio permissions on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val camGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onCameraPermissionResult(camGranted)

                val micGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onAudioPermissionResult(micGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-dismiss transient toast banner
    LaunchedEffect(state.statusMessage) {
        if (state.statusMessage != null) {
            delay(3200)
            viewModel.clearStatusMessage()
        }
    }

    // Latest raw frame shown in the viewfinder for instant capture & multimodal voice inspection
    var latestViewfinderBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val activeStudioBitmap = state.studioSceneBitmaps[state.activeStudioScene]
        ?: state.studioSceneBitmaps[StudioScene.TOKYO_NEON]

    // Full-Screen Darkroom Gallery View
    if (state.isGalleryOpen) {
        DarkroomGalleryScreen(
            photos = state.capturedPhotos,
            onBack = { viewModel.setGalleryOpen(false) },
            onSelectPhoto = { photo ->
                viewModel.openShareModal(photo)
            },
            onSharePhoto = { photo ->
                viewModel.sharePhotoNow(context, photo)
            },
            onSavePhoto = { photo ->
                viewModel.savePhotoToDevice(context, photo)
            },
            onToggleFavorite = { id ->
                viewModel.togglePhotoFavorite(id)
            },
            onDeletePhoto = { id ->
                viewModel.deletePhoto(id)
            }
        )
    } else {
        // Main Camera & Real-Time LUT Studio View
        Surface(
            color = ObsidianBlack,
            modifier = Modifier
                .fillMaxSize()
                .testTag("lumina_camera_root")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Top Camera HUD & Quick Optical + Voice Controls
                    TopOpticalHudBar(
                        viewfinderMode = state.viewfinderMode,
                        flashMode = state.flashMode,
                        aspectRatioLabel = state.aspectRatio.label,
                        showGrid = state.showGrid,
                        showDateStamp = state.showDateStamp,
                        isSplitCompare = state.isSplitCompareEnabled,
                        isVoiceDrawerOpen = state.isVoiceDrawerOpen,
                        isVoiceActive = state.voiceState is VoiceAssistantUiState.Listening ||
                            state.voiceState is VoiceAssistantUiState.Processing,
                        onSelectMode = { mode ->
                            if (mode == ViewfinderMode.LIVE_CAMERA && !state.hasCameraPermission) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            viewModel.setViewfinderMode(mode)
                        },
                        onToggleVoiceDrawer = {
                            if (!state.hasAudioPermission && !state.isVoiceDrawerOpen) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            viewModel.toggleVoiceDrawer()
                        },
                        onCycleFlash = { viewModel.cycleFlashMode() },
                        onCycleAspectRatio = { viewModel.cycleAspectRatio() },
                        onToggleGrid = { viewModel.toggleGrid() },
                        onToggleDateStamp = { viewModel.toggleDateStamp() },
                        onToggleSplitCompare = { viewModel.toggleSplitCompare() }
                    )

                    // 2. Center Live-Filtered Viewfinder
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        LiveFilteredViewfinder(
                            viewfinderMode = state.viewfinderMode,
                            hasCameraPermission = state.hasCameraPermission,
                            isFrontLens = state.isFrontLens,
                            flashMode = state.flashMode,
                            aspectRatio = state.aspectRatio,
                            showGrid = state.showGrid,
                            showDateStamp = state.showDateStamp,
                            frameStyle = state.selectedFrameStyle,
                            isSplitCompareEnabled = state.isSplitCompareEnabled,
                            splitFraction = state.splitFraction,
                            selectedFilter = state.selectedFilter,
                            adjustments = state.adjustments,
                            activeStudioScene = state.activeStudioScene,
                            studioBitmap = activeStudioBitmap,
                            shutterPulseCount = state.shutterPulseCount,
                            onSplitFractionChange = { viewModel.updateSplitFraction(it) },
                            onSwipeFilter = { delta -> viewModel.stepFilterByOffset(delta) },
                            onRequestCameraPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onSwitchToStudioCanvas = {
                                viewModel.setViewfinderMode(ViewfinderMode.STUDIO_CANVAS)
                            },
                            onLatestFrameUpdated = { bmp ->
                                latestViewfinderBitmap = bmp
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Status Toast inside Viewfinder top area
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.statusMessage != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Surface(
                                color = ObsidianBlack.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, LeicaAmber.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = state.statusMessage.orEmpty(),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = LeicaAmber,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // 3A. Collapsible Gemini Live Voice Camera OS Drawer
                    AnimatedVisibility(
                        visible = state.isVoiceDrawerOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        VoiceCommandDrawer(
                            voiceState = state.voiceState,
                            voiceHistory = state.voiceHistory,
                            hasAudioPermission = state.hasAudioPermission,
                            isTtsEnabled = state.isTtsEnabled,
                            lastSpokenReply = state.lastSpokenReply,
                            onRequestAudioPermission = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onToggleTts = { viewModel.toggleVoiceTts() },
                            onUpdateListeningState = { listening, partial, amp ->
                                viewModel.updateVoiceListeningState(listening, partial, amp)
                            },
                            onSubmitTextVoiceCommand = { commandText ->
                                viewModel.processVoiceCommand(
                                    context = context,
                                    spokenText = commandText,
                                    recordedWavBytes = null,
                                    viewfinderBitmap = latestViewfinderBitmap ?: activeStudioBitmap
                                )
                            },
                            onSubmitRecordedAudioCommand = { wavBytes ->
                                viewModel.processVoiceCommand(
                                    context = context,
                                    spokenText = null,
                                    recordedWavBytes = wavBytes,
                                    viewfinderBitmap = latestViewfinderBitmap ?: activeStudioBitmap
                                )
                            },
                            onCloseDrawer = { viewModel.setVoiceDrawerOpen(false) }
                        )
                    }

                    // 3B. Collapsible Real-Time Optical Fine-Tuning Drawer
                    AnimatedVisibility(
                        visible = state.isFineTuneDrawerOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OpticalFineTuneDrawer(
                            adjustments = state.adjustments,
                            selectedParam = state.selectedAdjustmentParam,
                            onSelectParam = { viewModel.selectAdjustmentParam(it) },
                            onValueChange = { param, value ->
                                viewModel.updateAdjustmentValue(param, value)
                            },
                            onResetAll = { viewModel.resetAdjustments() }
                        )
                    }

                    // 4. Real-Time Filter Carousel Deck
                    FilterCarouselDeck(
                        viewfinderMode = state.viewfinderMode,
                        activeStudioScene = state.activeStudioScene,
                        selectedCategory = state.selectedCategory,
                        visibleFilters = state.visibleFilters,
                        selectedFilter = state.selectedFilter,
                        favoriteFilterIds = state.favoriteFilterIds,
                        selectedFrameStyle = state.selectedFrameStyle,
                        thumbnailBitmap = state.filterThumbnailBitmap,
                        onSelectStudioScene = { viewModel.selectStudioScene(it) },
                        onImportDevicePhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSelectCategory = { viewModel.selectCategory(it) },
                        onSelectFilter = { viewModel.selectFilter(it) },
                        onToggleFavoriteFilter = { viewModel.toggleFavoriteFilter(it) },
                        onSelectFrameStyle = { viewModel.selectFrameStyle(it) }
                    )

                    // 5. Bottom Camera Shutter, Voice Trigger & Instant Share Deck
                    BottomShutterDeck(
                        latestPhoto = state.capturedPhotos.firstOrNull(),
                        photoCount = state.capturedPhotos.size,
                        activeFilterAccent = state.selectedFilter.accentColor,
                        isFineTuneOpen = state.isFineTuneDrawerOpen,
                        isVoiceDrawerOpen = state.isVoiceDrawerOpen,
                        viewfinderMode = state.viewfinderMode,
                        isCapturing = state.isCapturing,
                        onOpenGallery = { viewModel.setGalleryOpen(true) },
                        onQuickShareLatest = {
                            state.capturedPhotos.firstOrNull()?.let { latest ->
                                viewModel.sharePhotoNow(context, latest)
                            }
                        },
                        onCaptureShutter = {
                            val bmpToCapture = latestViewfinderBitmap ?: activeStudioBitmap
                            if (bmpToCapture != null) {
                                viewModel.captureAndProcessBitmap(context, bmpToCapture)
                            }
                        },
                        onToggleVoice = {
                            if (!state.hasAudioPermission && !state.isVoiceDrawerOpen) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            viewModel.toggleVoiceDrawer()
                        },
                        onToggleFineTune = { viewModel.toggleFineTuneDrawer() },
                        onFlipOrImport = {
                            if (state.viewfinderMode == ViewfinderMode.LIVE_CAMERA) {
                                viewModel.toggleCameraLens()
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    // Post-Capture / Selected Photo Quick Share & Framing Modal
    state.activeSharePhoto?.let { sharePhoto ->
        QuickShareStudioDialog(
            photo = sharePhoto,
            onDismiss = { viewModel.dismissShareModal() },
            onShareNow = { photo, note ->
                viewModel.sharePhotoNow(context, photo, note)
            },
            onSaveToDevice = { photo ->
                viewModel.savePhotoToDevice(context, photo)
            },
            onUpdateFrameAndCaption = { photo, frameStyle, caption ->
                viewModel.updatePhotoFrameAndCaption(context, photo, frameStyle, caption)
            },
            onToggleFavorite = { id ->
                viewModel.togglePhotoFavorite(id)
            },
            onDeletePhoto = { id ->
                viewModel.deletePhoto(id)
            }
        )
    }
}

@Composable
private fun TopOpticalHudBar(
    viewfinderMode: ViewfinderMode,
    flashMode: FlashModePreset,
    aspectRatioLabel: String,
    showGrid: Boolean,
    showDateStamp: Boolean,
    isSplitCompare: Boolean,
    isVoiceDrawerOpen: Boolean,
    isVoiceActive: Boolean,
    onSelectMode: (ViewfinderMode) -> Unit,
    onToggleVoiceDrawer: () -> Unit,
    onCycleFlash: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleDateStamp: () -> Unit,
    onToggleSplitCompare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher Pill (LIVE OPTICS vs STUDIO CANVAS)
            Surface(
                color = DarkroomSurface,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, GunmetalBorder),
                modifier = Modifier.testTag("mode_switcher_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(3.dp)
                ) {
                    ViewfinderMode.entries.forEach { mode ->
                        val active = mode == viewfinderMode
                        Surface(
                            color = if (active) LeicaAmber else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clickable { onSelectMode(mode) }
                                .testTag("mode_button_${mode.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (mode == ViewfinderMode.LIVE_CAMERA) {
                                        Icons.Default.CameraAlt
                                    } else {
                                        Icons.Default.PhotoLibrary
                                    },
                                    contentDescription = null,
                                    tint = if (active) ObsidianBlack else MutedSilver,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = if (active) ObsidianBlack else MutedSilver
                                )
                            }
                        }
                    }
                }
            }

            // Right HUD Controls: Voice AI Button, Split Compare, Date Stamp, Grid, Aspect Ratio, Flash
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gemini Live Voice AI Trigger Pill
                HudIconPill(
                    active = isVoiceDrawerOpen || isVoiceActive,
                    activeColor = if (isVoiceActive) HalideOrange else OpticalCyan,
                    onClick = onToggleVoiceDrawer,
                    testTag = "voice_hud_toggle_button"
                ) {
                    Icon(
                        imageVector = if (isVoiceActive) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Voice Camera Control",
                        tint = if (isVoiceDrawerOpen || isVoiceActive) ObsidianBlack else OpticalCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Split-Screen Before/After Loupe Toggle
                HudIconPill(
                    active = isSplitCompare,
                    activeColor = OpticalCyan,
                    onClick = onToggleSplitCompare,
                    testTag = "split_compare_toggle_button"
                ) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = "Compare Before and After",
                        tint = if (isSplitCompare) ObsidianBlack else IvoryText,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Retro Quartz Date Stamp Toggle
                HudIconPill(
                    active = showDateStamp,
                    activeColor = LeicaAmber,
                    onClick = onToggleDateStamp,
                    testTag = "date_stamp_toggle_button"
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Toggle Date Stamp",
                        tint = if (showDateStamp) ObsidianBlack else IvoryText,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Rule of Thirds Grid Toggle
                HudIconPill(
                    active = showGrid,
                    activeColor = LeicaAmber,
                    onClick = onToggleGrid,
                    testTag = "grid_toggle_button"
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Toggle Grid",
                        tint = if (showGrid) ObsidianBlack else IvoryText,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Aspect Ratio Button
                HudIconPill(
                    active = false,
                    activeColor = LeicaAmber,
                    onClick = onCycleAspectRatio,
                    testTag = "aspect_ratio_button"
                ) {
                    Text(
                        text = aspectRatioLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = IvoryText
                    )
                }

                // Flash Mode Button
                HudIconPill(
                    active = flashMode != FlashModePreset.OFF,
                    activeColor = LeicaAmber,
                    onClick = onCycleFlash,
                    testTag = "flash_toggle_button"
                ) {
                    val icon = when (flashMode) {
                        FlashModePreset.OFF -> Icons.Default.FlashOff
                        FlashModePreset.AUTO -> Icons.Default.FlashAuto
                        FlashModePreset.ON -> Icons.Default.FlashOn
                        FlashModePreset.TORCH -> Icons.Default.Highlight
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Flash Mode ${flashMode.label}",
                        tint = if (flashMode != FlashModePreset.OFF) ObsidianBlack else IvoryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HudIconPill(
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = if (active) activeColor else DarkroomElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) activeColor else GunmetalBorder),
        modifier = Modifier
            .size(34.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun BottomShutterDeck(
    latestPhoto: com.example.model.CapturedPhoto?,
    photoCount: Int,
    activeFilterAccent: Color,
    isFineTuneOpen: Boolean,
    isVoiceDrawerOpen: Boolean,
    viewfinderMode: ViewfinderMode,
    isCapturing: Boolean,
    onOpenGallery: () -> Unit,
    onQuickShareLatest: () -> Unit,
    onCaptureShutter: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleFineTune: () -> Unit,
    onFlipOrImport: () -> Unit
) {
    val thumbBmp = remember(latestPhoto?.id, latestPhoto?.filePath) {
        latestPhoto?.filePath?.let { path ->
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Group: Darkroom Gallery Thumbnail + 1-Tap Instant Share Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkroomElevated)
                    .border(1.5.dp, LeicaAmber.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .clickable { onOpenGallery() }
                    .testTag("open_gallery_button"),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBmp != null) {
                    Image(
                        bitmap = thumbBmp.asImageBitmap(),
                        contentDescription = "Open Darkroom Gallery",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Open Darkroom Gallery",
                        tint = IvoryText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Count badge
                if (photoCount > 0) {
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = "$photoCount",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = LeicaAmber,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // One-Tap Share Latest Captured Photo Button
            if (latestPhoto != null) {
                Surface(
                    color = DarkroomElevated,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onQuickShareLatest() }
                        .testTag("instant_share_latest_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Latest Photo",
                            tint = OpticalCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Center: Tactile Dual-Ring Leica Shutter Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(3.dp, activeFilterAccent, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (isCapturing) LeicaAmber else IvoryText)
                .clickable(enabled = !isCapturing) { onCaptureShutter() }
                .testTag("shutter_capture_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, ObsidianBlack.copy(alpha = 0.35f), CircleShape)
            )
        }

        // Right Group: Voice Command Mic Button + Optical Fine-Tune Dial + Flip Lens
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = if (isVoiceDrawerOpen) OpticalCyan else DarkroomElevated,
                shape = CircleShape,
                border = BorderStroke(1.dp, if (isVoiceDrawerOpen) OpticalCyan else OpticalCyan.copy(alpha = 0.6f)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onToggleVoice() }
                    .testTag("bottom_voice_command_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Command Assistant",
                        tint = if (isVoiceDrawerOpen) ObsidianBlack else OpticalCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                color = if (isFineTuneOpen) LeicaAmber else DarkroomElevated,
                shape = CircleShape,
                border = BorderStroke(1.dp, if (isFineTuneOpen) LeicaAmber else GunmetalBorder),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onToggleFineTune() }
                    .testTag("fine_tune_toggle_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Fine-Tune Optical Grade",
                        tint = if (isFineTuneOpen) ObsidianBlack else IvoryText,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Surface(
                color = DarkroomElevated,
                shape = CircleShape,
                border = BorderStroke(1.dp, GunmetalBorder),
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onFlipOrImport() }
                    .testTag("flip_camera_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (viewfinderMode == ViewfinderMode.LIVE_CAMERA) {
                            Icons.Default.Cameraswitch
                        } else {
                            Icons.Default.AddPhotoAlternate
                        },
                        contentDescription = if (viewfinderMode == ViewfinderMode.LIVE_CAMERA) {
                            "Switch Camera Lens"
                        } else {
                            "Import Photo from Device"
                        },
                        tint = IvoryText,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}
