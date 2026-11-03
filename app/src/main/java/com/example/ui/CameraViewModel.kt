package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LuminaDatabase
import com.example.data.PhotoGalleryRepository
import com.example.domain.CameraVoiceAction
import com.example.domain.FilterEngine
import com.example.domain.ParsedVoiceCommand
import com.example.domain.VoiceAssistantUiState
import com.example.domain.VoiceCameraAssistant
import com.example.domain.VoiceTurnEntry
import com.example.model.AdjustmentParameter
import com.example.model.AspectRatioPreset
import com.example.model.CapturedPhoto
import com.example.model.FilterAdjustments
import com.example.model.FilterCatalog
import com.example.model.FilterCategory
import com.example.model.FlashModePreset
import com.example.model.FrameStyle
import com.example.model.OpticalFilter
import com.example.model.StudioScene
import com.example.model.ViewfinderMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "CameraViewModel"

data class CameraUiState(
    val viewfinderMode: ViewfinderMode = ViewfinderMode.LIVE_CAMERA,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val isFrontLens: Boolean = false,
    val flashMode: FlashModePreset = FlashModePreset.OFF,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_3_4,
    val showGrid: Boolean = true,
    val showDateStamp: Boolean = true,
    val isSplitCompareEnabled: Boolean = false,
    val splitFraction: Float = 0.5f,
    val selectedCategory: FilterCategory = FilterCategory.ALL,
    val selectedFilter: OpticalFilter = FilterCatalog.filters[1], // Portra Gold 400 default
    val favoriteFilterIds: Set<String> = setOf("kodak_gold_400", "cine_teal_orange", "cyber_2077"),
    val adjustments: FilterAdjustments = FilterAdjustments(
        intensity = 1.0f,
        vignette = FilterCatalog.filters[1].defaultVignette,
        filmGrain = FilterCatalog.filters[1].defaultGrain,
        lightLeak = FilterCatalog.filters[1].defaultLightLeak
    ),
    val selectedAdjustmentParam: AdjustmentParameter = AdjustmentParameter.INTENSITY,
    val isFineTuneDrawerOpen: Boolean = false,
    val isVoiceDrawerOpen: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val voiceState: VoiceAssistantUiState = VoiceAssistantUiState.Idle,
    val voiceHistory: List<VoiceTurnEntry> = emptyList(),
    val lastSpokenReply: String? = null,
    val selectedFrameStyle: FrameStyle = FrameStyle.NONE,
    val activeStudioScene: StudioScene = StudioScene.TOKYO_NEON,
    val studioSceneBitmaps: Map<StudioScene, Bitmap> = emptyMap(),
    val filterThumbnailBitmap: Bitmap? = null,
    val capturedPhotos: List<CapturedPhoto> = emptyList(),
    val activeSharePhoto: CapturedPhoto? = null,
    val isGalleryOpen: Boolean = false,
    val isCapturing: Boolean = false,
    val shutterPulseCount: Int = 0,
    val statusMessage: String? = null
) {
    val visibleFilters: List<OpticalFilter>
        get() = when (selectedCategory) {
            FilterCategory.ALL -> FilterCatalog.filters
            FilterCategory.FAVORITES -> FilterCatalog.filters.filter { it.id in favoriteFilterIds }
            else -> FilterCatalog.filters.filter { it.category == selectedCategory }
        }
}

class CameraViewModel(
    private var galleryRepository: PhotoGalleryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var isInitialized = false

    fun initializeStudioAssets(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val repo = galleryRepository ?: PhotoGalleryRepository(
            LuminaDatabase.getInstance(context.applicationContext).capturedPhotoDao()
        ).also { galleryRepository = it }

        // Reactively observe Room database table `captured_photos`
        viewModelScope.launch {
            repo.allPhotosFlow.collectLatest { dbPhotos ->
                _uiState.update { state ->
                    val updatedActive = state.activeSharePhoto?.let { active ->
                        dbPhotos.firstOrNull { it.id == active.id } ?: active
                    }
                    state.copy(
                        capturedPhotos = dbPhotos,
                        activeSharePhoto = updatedActive
                    )
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            val scenes = mutableMapOf<StudioScene, Bitmap>()
            listOf(
                StudioScene.TOKYO_NEON,
                StudioScene.AMALFI_SUNSET,
                StudioScene.CYBER_PORTRAIT,
                StudioScene.ALPINE_DUSK
            ).forEach { scene ->
                scenes[scene] = FilterEngine.generateStudioSceneBitmap(scene, 900, 1200)
            }

            val thumbBmp = FilterEngine.generateStudioSceneBitmap(StudioScene.TOKYO_NEON, 160, 200)

            _uiState.update { state ->
                state.copy(
                    studioSceneBitmaps = scenes,
                    filterThumbnailBitmap = thumbBmp
                )
            }

            // Seed Room database on first launch if empty so the Darkroom Gallery has sample captures
            if (repo.getPhotoCount() == 0) {
                val tokyoBmp = scenes[StudioScene.TOKYO_NEON]
                val amalfiBmp = scenes[StudioScene.AMALFI_SUNSET]

                if (tokyoBmp != null) {
                    val cyberFilter = FilterCatalog.findById("cyber_2077")
                    val rendered1 = FilterEngine.renderFilteredBitmap(
                        source = tokyoBmp,
                        filter = cyberFilter,
                        adjustments = FilterAdjustments(
                            intensity = 1f,
                            vignette = cyberFilter.defaultVignette,
                            filmGrain = cyberFilter.defaultGrain,
                            lightLeak = cyberFilter.defaultLightLeak
                        ),
                        frameStyle = FrameStyle.CINEMA_SCOPE,
                        showDateStamp = true,
                        caption = "Shinjuku After Midnight",
                        timestampMillis = System.currentTimeMillis() - 3600_000L
                    )
                    val id1 = "sample_tokyo"
                    val file1 = FilterEngine.saveBitmapToInternalGallery(context.applicationContext, rendered1, id1)
                    repo.savePhoto(
                        CapturedPhoto(
                            id = id1,
                            filePath = file1.absolutePath,
                            filterId = cyberFilter.id,
                            filterName = cyberFilter.name,
                            filterCode = cyberFilter.shortCode,
                            isoTag = cyberFilter.isoTag,
                            frameStyle = FrameStyle.CINEMA_SCOPE,
                            caption = "Shinjuku After Midnight",
                            timestampMillis = System.currentTimeMillis() - 3600_000L,
                            width = rendered1.width,
                            height = rendered1.height,
                            isFavorite = true
                        )
                    )
                }

                if (amalfiBmp != null) {
                    val goldFilter = FilterCatalog.findById("kodak_gold_400")
                    val rendered2 = FilterEngine.renderFilteredBitmap(
                        source = amalfiBmp,
                        filter = goldFilter,
                        adjustments = FilterAdjustments(
                            intensity = 1f,
                            vignette = goldFilter.defaultVignette,
                            filmGrain = goldFilter.defaultGrain,
                            lightLeak = 0.25f
                        ),
                        frameStyle = FrameStyle.POLAROID,
                        showDateStamp = true,
                        caption = "Golden Hour Coastline",
                        timestampMillis = System.currentTimeMillis() - 7200_000L
                    )
                    val id2 = "sample_amalfi"
                    val file2 = FilterEngine.saveBitmapToInternalGallery(context.applicationContext, rendered2, id2)
                    repo.savePhoto(
                        CapturedPhoto(
                            id = id2,
                            filePath = file2.absolutePath,
                            filterId = goldFilter.id,
                            filterName = goldFilter.name,
                            filterCode = goldFilter.shortCode,
                            isoTag = goldFilter.isoTag,
                            frameStyle = FrameStyle.POLAROID,
                            caption = "Golden Hour Coastline",
                            timestampMillis = System.currentTimeMillis() - 7200_000L,
                            width = rendered2.width,
                            height = rendered2.height,
                            isFavorite = false
                        )
                    )
                }
            }
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                hasCameraPermission = granted,
                viewfinderMode = if (granted) ViewfinderMode.LIVE_CAMERA else ViewfinderMode.STUDIO_CANVAS
            )
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
    }

    fun toggleVoiceDrawer() {
        _uiState.update { state ->
            val nextOpen = !state.isVoiceDrawerOpen
            state.copy(
                isVoiceDrawerOpen = nextOpen,
                isFineTuneDrawerOpen = if (nextOpen) false else state.isFineTuneDrawerOpen
            )
        }
    }

    fun setVoiceDrawerOpen(open: Boolean) {
        _uiState.update { state ->
            state.copy(
                isVoiceDrawerOpen = open,
                isFineTuneDrawerOpen = if (open) false else state.isFineTuneDrawerOpen
            )
        }
    }

    fun toggleVoiceTts() {
        _uiState.update { it.copy(isTtsEnabled = !it.isTtsEnabled) }
    }

    fun updateVoiceListeningState(isListening: Boolean, partialText: String, amplitude: Float) {
        _uiState.update { state ->
            if (isListening) {
                state.copy(voiceState = VoiceAssistantUiState.Listening(partialText, amplitude))
            } else if (state.voiceState is VoiceAssistantUiState.Listening) {
                state.copy(voiceState = VoiceAssistantUiState.Idle)
            } else {
                state
            }
        }
    }

    /**
     * Processes a voice command (either transcribed text or raw WAV audio clip) via instant
     * local camera intent routing + Firebase AI Logic (`gemini-3.8-flash` / `gemini-3.5-flash-lite`),
     * executes the returned camera operations, and speaks the reply.
     */
    fun processVoiceCommand(
        context: Context,
        spokenText: String?,
        recordedWavBytes: ByteArray?,
        viewfinderBitmap: Bitmap?
    ) {
        val currentSnapshot = _uiState.value
        val activeFrame = viewfinderBitmap
            ?: currentSnapshot.studioSceneBitmaps[currentSnapshot.activeStudioScene]
            ?: currentSnapshot.studioSceneBitmaps[StudioScene.TOKYO_NEON]

        // 1. Check if spokenText matches a direct camera command for 0ms instant execution
        val fastMatch = if (!spokenText.isNullOrBlank()) {
            VoiceCameraAssistant.matchLocalFastCommand(
                rawText = spokenText,
                currentFilter = currentSnapshot.selectedFilter
            )
        } else {
            null
        }

        if (fastMatch != null) {
            val badges = executeParsedVoiceActions(
                context = context,
                parsed = fastMatch,
                viewfinderBitmap = activeFrame
            )
            val turnId = UUID.randomUUID().toString().take(8)
            val turnEntry = VoiceTurnEntry(
                id = turnId,
                userText = fastMatch.userTranscript,
                assistantReply = fastMatch.spokenReply,
                executedBadges = badges
            )
            _uiState.update { state ->
                state.copy(
                    voiceState = VoiceAssistantUiState.Success(fastMatch),
                    voiceHistory = listOf(turnEntry) + state.voiceHistory,
                    lastSpokenReply = fastMatch.spokenReply,
                    statusMessage = "🎙 ${fastMatch.spokenReply}"
                )
            }
            return
        }

        // 2. Multimodal / Open-ended / Audio clip inference via Firebase AI Logic
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    voiceState = VoiceAssistantUiState.Processing(
                        statusLabel = if (!spokenText.isNullOrBlank()) {
                            "「$spokenText」を Gemini Live AI で解析中..."
                        } else {
                            "音声波形を Gemini Live AI で解析中..."
                        }
                    )
                )
            }

            try {
                val parsed = VoiceCameraAssistant.interpretVoiceCommand(
                    spokenText = spokenText,
                    recordedWavBytes = recordedWavBytes,
                    currentFilter = _uiState.value.selectedFilter,
                    viewfinderSnapshot = activeFrame
                )

                val badges = executeParsedVoiceActions(
                    context = context,
                    parsed = parsed,
                    viewfinderBitmap = activeFrame
                )

                val turnEntry = VoiceTurnEntry(
                    id = UUID.randomUUID().toString().take(8),
                    userText = parsed.userTranscript,
                    assistantReply = parsed.spokenReply,
                    executedBadges = badges
                )

                _uiState.update { state ->
                    state.copy(
                        voiceState = VoiceAssistantUiState.Success(parsed),
                        voiceHistory = listOf(turnEntry) + state.voiceHistory,
                        lastSpokenReply = parsed.spokenReply,
                        statusMessage = "🎙 ${parsed.spokenReply}"
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Voice command AI inference timed out", e)
                _uiState.update {
                    it.copy(
                        voiceState = VoiceAssistantUiState.Error(
                            "音声AIの応答がタイムアウトしました。通信状況をご確認のうえ再試行してください。"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI voice command failed", e)
                _uiState.update {
                    it.copy(
                        voiceState = VoiceAssistantUiState.Error(
                            e.localizedMessage ?: "音声コマンドの解析に失敗しました。"
                        )
                    )
                }
            }
        }
    }

    private fun executeParsedVoiceActions(
        context: Context,
        parsed: ParsedVoiceCommand,
        viewfinderBitmap: Bitmap?
    ): List<String> {
        val badges = mutableListOf<String>()
        var shouldCaptureAfterSetup = false

        parsed.actions.forEach { action ->
            when (action) {
                CameraVoiceAction.SELECT_FILTER -> {
                    val targetId = parsed.filterId
                    if (!targetId.isNullOrBlank()) {
                        val filter = FilterCatalog.findById(targetId)
                        selectFilter(filter)
                        badges.add("LUT: ${filter.shortCode}")
                    }
                }

                CameraVoiceAction.NEXT_FILTER -> {
                    stepFilterByOffset(1)
                    badges.add("NEXT LUT")
                }

                CameraVoiceAction.PREV_FILTER -> {
                    stepFilterByOffset(-1)
                    badges.add("PREV LUT")
                }

                CameraVoiceAction.SWITCH_LENS -> {
                    toggleCameraLens()
                    badges.add("FLIP LENS")
                }

                CameraVoiceAction.SET_FLASH -> {
                    val target = FlashModePreset.entries.firstOrNull { it.name == parsed.flashMode }
                    if (target != null) {
                        _uiState.update { it.copy(flashMode = target) }
                        badges.add("FLASH: ${target.label}")
                    } else {
                        cycleFlashMode()
                        badges.add("FLASH")
                    }
                }

                CameraVoiceAction.SET_FRAME -> {
                    val target = FrameStyle.entries.firstOrNull { it.name == parsed.frameStyle }
                    if (target != null) {
                        selectFrameStyle(target)
                        badges.add("FRAME: ${target.badge}")
                    }
                }

                CameraVoiceAction.SET_ASPECT_RATIO -> {
                    val target = AspectRatioPreset.entries.firstOrNull { it.name == parsed.aspectRatio }
                    if (target != null) {
                        _uiState.update { it.copy(aspectRatio = target) }
                        badges.add(target.label)
                    } else {
                        cycleAspectRatio()
                        badges.add("RATIO")
                    }
                }

                CameraVoiceAction.ADJUST_GRADE -> {
                    _uiState.update { state ->
                        var adj = state.adjustments
                        parsed.intensity?.let { adj = adj.withValue(AdjustmentParameter.INTENSITY, it) }
                        parsed.exposure?.let { adj = adj.withValue(AdjustmentParameter.EXPOSURE, it) }
                        parsed.contrast?.let { adj = adj.withValue(AdjustmentParameter.CONTRAST, it) }
                        parsed.warmth?.let { adj = adj.withValue(AdjustmentParameter.WARMTH, it) }
                        parsed.vignette?.let { adj = adj.withValue(AdjustmentParameter.VIGNETTE, it) }
                        parsed.filmGrain?.let { adj = adj.withValue(AdjustmentParameter.GRAIN, it) }
                        parsed.lightLeak?.let { adj = adj.withValue(AdjustmentParameter.LIGHT_LEAK, it) }
                        state.copy(adjustments = adj)
                    }
                    badges.add("GRADE ADJUST")
                }

                CameraVoiceAction.TOGGLE_SPLIT_COMPARE -> {
                    toggleSplitCompare()
                    badges.add("SPLIT LOUPE")
                }

                CameraVoiceAction.TOGGLE_GRID -> {
                    toggleGrid()
                    badges.add("GRID")
                }

                CameraVoiceAction.TOGGLE_DATE_STAMP -> {
                    toggleDateStamp()
                    badges.add("DATE STAMP")
                }

                CameraVoiceAction.OPEN_GALLERY -> {
                    setGalleryOpen(true)
                    badges.add("OPEN GALLERY")
                }

                CameraVoiceAction.CLOSE_GALLERY -> {
                    setGalleryOpen(false)
                    badges.add("VIEWFINDER")
                }

                CameraVoiceAction.SHARE_LATEST -> {
                    _uiState.value.capturedPhotos.firstOrNull()?.let { latest ->
                        sharePhotoNow(context, latest)
                        badges.add("SHARED")
                    }
                }

                CameraVoiceAction.SAVE_LATEST -> {
                    _uiState.value.capturedPhotos.firstOrNull()?.let { latest ->
                        savePhotoToDevice(context, latest)
                        badges.add("SAVED")
                    }
                }

                CameraVoiceAction.RESET_GRADE -> {
                    resetAdjustments()
                    badges.add("RESET GRADE")
                }

                CameraVoiceAction.CAPTURE_PHOTO -> {
                    shouldCaptureAfterSetup = true
                    badges.add("SHUTTER")
                }

                CameraVoiceAction.NONE -> {}
            }
        }

        if (shouldCaptureAfterSetup && viewfinderBitmap != null) {
            captureAndProcessBitmap(
                context = context,
                rawBitmap = viewfinderBitmap,
                caption = parsed.userTranscript
            )
        }

        return badges
    }

    fun setViewfinderMode(mode: ViewfinderMode) {
        _uiState.update { it.copy(viewfinderMode = mode) }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontLens = !it.isFrontLens) }
    }

    fun cycleFlashMode() {
        _uiState.update { state ->
            val modes = FlashModePreset.entries
            val nextIdx = (modes.indexOf(state.flashMode) + 1) % modes.size
            state.copy(flashMode = modes[nextIdx])
        }
    }

    fun cycleAspectRatio() {
        _uiState.update { state ->
            val ratios = AspectRatioPreset.entries
            val nextIdx = (ratios.indexOf(state.aspectRatio) + 1) % ratios.size
            state.copy(aspectRatio = ratios[nextIdx])
        }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleDateStamp() {
        _uiState.update { it.copy(showDateStamp = !it.showDateStamp) }
    }

    fun toggleSplitCompare() {
        _uiState.update { state ->
            val next = !state.isSplitCompareEnabled
            state.copy(
                isSplitCompareEnabled = next,
                splitFraction = 0.5f,
                statusMessage = if (next) "Drag split bar to compare Original vs ${state.selectedFilter.shortCode}" else null
            )
        }
    }

    fun updateSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.08f, 0.92f)) }
    }

    fun selectCategory(category: FilterCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun selectFilter(filter: OpticalFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                adjustments = state.adjustments.copy(
                    intensity = 1.0f,
                    vignette = filter.defaultVignette,
                    filmGrain = filter.defaultGrain,
                    lightLeak = filter.defaultLightLeak
                )
            )
        }
    }

    fun stepFilterByOffset(delta: Int) {
        val all = FilterCatalog.filters
        val currentIdx = all.indexOfFirst { it.id == _uiState.value.selectedFilter.id }.coerceAtLeast(0)
        val nextIdx = (currentIdx + delta + all.size) % all.size
        selectFilter(all[nextIdx])
    }

    fun toggleFavoriteFilter(filterId: String) {
        _uiState.update { state ->
            val nextFavs = state.favoriteFilterIds.toMutableSet()
            val added = if (filterId in nextFavs) {
                nextFavs.remove(filterId)
                false
            } else {
                nextFavs.add(filterId)
                true
            }
            state.copy(
                favoriteFilterIds = nextFavs,
                statusMessage = if (added) "Saved LUT to Starred Favorites" else "Removed LUT from Starred"
            )
        }
    }

    fun toggleFineTuneDrawer() {
        _uiState.update { state ->
            val nextOpen = !state.isFineTuneDrawerOpen
            state.copy(
                isFineTuneDrawerOpen = nextOpen,
                isVoiceDrawerOpen = if (nextOpen) false else state.isVoiceDrawerOpen
            )
        }
    }

    fun selectAdjustmentParam(param: AdjustmentParameter) {
        _uiState.update { it.copy(selectedAdjustmentParam = param) }
    }

    fun updateAdjustmentValue(param: AdjustmentParameter, value: Float) {
        _uiState.update { state ->
            state.copy(adjustments = state.adjustments.withValue(param, value))
        }
    }

    fun resetAdjustments() {
        _uiState.update { state ->
            val f = state.selectedFilter
            state.copy(
                adjustments = FilterAdjustments(
                    intensity = 1.0f,
                    exposure = 0.0f,
                    contrast = 0.0f,
                    warmth = 0.0f,
                    saturation = 0.0f,
                    vignette = f.defaultVignette,
                    filmGrain = f.defaultGrain,
                    lightLeak = f.defaultLightLeak
                ),
                statusMessage = "Optical grade reset to ${f.shortCode} defaults"
            )
        }
    }

    fun selectFrameStyle(style: FrameStyle) {
        _uiState.update { it.copy(selectedFrameStyle = style) }
    }

    fun selectStudioScene(scene: StudioScene) {
        _uiState.update {
            it.copy(
                activeStudioScene = scene,
                viewfinderMode = ViewfinderMode.STUDIO_CANVAS
            )
        }
    }

    fun importPhotoFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val rawBitmap = inputStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return@launch

                val maxDim = 1440
                val scaled = if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(rawBitmap.width, rawBitmap.height)
                    Bitmap.createScaledBitmap(
                        rawBitmap,
                        (rawBitmap.width * scale).toInt().coerceAtLeast(64),
                        (rawBitmap.height * scale).toInt().coerceAtLeast(64),
                        true
                    )
                } else {
                    rawBitmap
                }

                _uiState.update { state ->
                    val updatedMap = state.studioSceneBitmaps.toMutableMap()
                    updatedMap[StudioScene.CUSTOM_IMPORT] = scaled
                    state.copy(
                        studioSceneBitmaps = updatedMap,
                        activeStudioScene = StudioScene.CUSTOM_IMPORT,
                        viewfinderMode = ViewfinderMode.STUDIO_CANVAS,
                        statusMessage = "Photo loaded into real-time optical studio"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Could not load selected image") }
            }
        }
    }

    fun captureAndProcessBitmap(
        context: Context,
        rawBitmap: Bitmap,
        caption: String = ""
    ) {
        if (_uiState.value.isCapturing) return
        val stateSnapshot = _uiState.value

        _uiState.update {
            it.copy(
                isCapturing = true,
                shutterPulseCount = it.shutterPulseCount + 1
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val croppedSource = cropToAspectRatio(rawBitmap, stateSnapshot.aspectRatio)
                val now = System.currentTimeMillis()
                val rendered = FilterEngine.renderFilteredBitmap(
                    source = croppedSource,
                    filter = stateSnapshot.selectedFilter,
                    adjustments = stateSnapshot.adjustments,
                    frameStyle = stateSnapshot.selectedFrameStyle,
                    showDateStamp = stateSnapshot.showDateStamp,
                    caption = caption,
                    timestampMillis = now
                )

                val photoId = UUID.randomUUID().toString().take(8)
                val savedFile = FilterEngine.saveBitmapToInternalGallery(
                    context = context.applicationContext,
                    bitmap = rendered,
                    photoId = photoId
                )

                val capturedPhoto = CapturedPhoto(
                    id = photoId,
                    filePath = savedFile.absolutePath,
                    filterId = stateSnapshot.selectedFilter.id,
                    filterName = stateSnapshot.selectedFilter.name,
                    filterCode = stateSnapshot.selectedFilter.shortCode,
                    isoTag = stateSnapshot.selectedFilter.isoTag,
                    frameStyle = stateSnapshot.selectedFrameStyle,
                    caption = caption,
                    timestampMillis = now,
                    width = rendered.width,
                    height = rendered.height,
                    isFavorite = false
                )

                galleryRepository?.savePhoto(capturedPhoto)

                _uiState.update { current ->
                    val updatedList = if (galleryRepository == null) {
                        listOf(capturedPhoto) + current.capturedPhotos
                    } else {
                        current.capturedPhotos
                    }
                    current.copy(
                        isCapturing = false,
                        capturedPhotos = updatedList,
                        activeSharePhoto = capturedPhoto,
                        statusMessage = "Saved to Room Gallery (${stateSnapshot.selectedFilter.shortCode})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        statusMessage = "Capture failed: ${e.localizedMessage ?: "error"}"
                    )
                }
            }
        }
    }

    fun updatePhotoFrameAndCaption(
        context: Context,
        photo: CapturedPhoto,
        newFrameStyle: FrameStyle,
        newCaption: String
    ) {
        val stateSnapshot = _uiState.value
        val baseSceneBmp = stateSnapshot.studioSceneBitmaps[stateSnapshot.activeStudioScene]
            ?: stateSnapshot.studioSceneBitmaps[StudioScene.TOKYO_NEON]
            ?: return

        viewModelScope.launch(Dispatchers.Default) {
            val filter = FilterCatalog.findById(photo.filterId)
            val rendered = FilterEngine.renderFilteredBitmap(
                source = baseSceneBmp,
                filter = filter,
                adjustments = stateSnapshot.adjustments,
                frameStyle = newFrameStyle,
                showDateStamp = stateSnapshot.showDateStamp,
                caption = newCaption,
                timestampMillis = photo.timestampMillis
            )
            val updatedId = "${photo.id.substringBefore("_")}_${System.currentTimeMillis() % 10000}"
            val savedFile = FilterEngine.saveBitmapToInternalGallery(
                context = context.applicationContext,
                bitmap = rendered,
                photoId = updatedId
            )
            val updatedPhoto = photo.copy(
                id = updatedId,
                filePath = savedFile.absolutePath,
                frameStyle = newFrameStyle,
                caption = newCaption,
                width = rendered.width,
                height = rendered.height
            )

            galleryRepository?.replacePhoto(
                oldPhotoId = photo.id,
                oldFilePath = photo.filePath,
                newPhoto = updatedPhoto
            )

            _uiState.update { state ->
                state.copy(
                    selectedFrameStyle = newFrameStyle,
                    activeSharePhoto = updatedPhoto,
                    statusMessage = "Updated frame & Room DB path"
                )
            }
        }
    }

    fun openShareModal(photo: CapturedPhoto) {
        _uiState.update { it.copy(activeSharePhoto = photo) }
    }

    fun dismissShareModal() {
        _uiState.update { it.copy(activeSharePhoto = null) }
    }

    fun setGalleryOpen(open: Boolean) {
        _uiState.update { it.copy(isGalleryOpen = open) }
    }

    fun togglePhotoFavorite(photoId: String) {
        viewModelScope.launch {
            val repo = galleryRepository
            if (repo != null) {
                repo.toggleFavorite(photoId)
            } else {
                _uiState.update { state ->
                    val updatedList = state.capturedPhotos.map {
                        if (it.id == photoId) it.copy(isFavorite = !it.isFavorite) else it
                    }
                    val updatedActive = state.activeSharePhoto?.let {
                        if (it.id == photoId) it.copy(isFavorite = !it.isFavorite) else it
                    }
                    state.copy(
                        capturedPhotos = updatedList,
                        activeSharePhoto = updatedActive
                    )
                }
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            val repo = galleryRepository
            if (repo != null) {
                repo.deletePhoto(photoId)
                _uiState.update { state ->
                    val nextActive = if (state.activeSharePhoto?.id == photoId) null else state.activeSharePhoto
                    state.copy(
                        activeSharePhoto = nextActive,
                        statusMessage = "Deleted photo from Room database"
                    )
                }
            } else {
                _uiState.update { state ->
                    val updatedList = state.capturedPhotos.filterNot { it.id == photoId }
                    val nextActive = if (state.activeSharePhoto?.id == photoId) null else state.activeSharePhoto
                    state.copy(
                        capturedPhotos = updatedList,
                        activeSharePhoto = nextActive,
                        statusMessage = "Deleted photo from Darkroom"
                    )
                }
            }
        }
    }

    fun sharePhotoNow(context: Context, photo: CapturedPhoto, customNote: String = "") {
        val ok = FilterEngine.shareCapturedPhoto(context, photo, customNote)
        if (!ok) {
            _uiState.update { it.copy(statusMessage = "Unable to open system share sheet") }
        }
    }

    fun savePhotoToDevice(context: Context, photo: CapturedPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = FilterEngine.exportToDeviceMediaStore(context, photo)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        statusMessage = if (saved) {
                            "Saved to device Pictures/LuminaLens"
                        } else {
                            "Could not save to device gallery"
                        }
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun cropToAspectRatio(source: Bitmap, preset: AspectRatioPreset): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val targetRatio = preset.ratioWidth / preset.ratioHeight
        val currentRatio = srcW.toFloat() / srcH.toFloat()

        if (kotlin.math.abs(currentRatio - targetRatio) < 0.03f) return source

        val cropW: Int
        val cropH: Int
        if (currentRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt().coerceIn(1, srcH)
        }
        val x = ((srcW - cropW) / 2).coerceAtLeast(0)
        val y = ((srcH - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, x, y, cropW, cropH)
    }
}
