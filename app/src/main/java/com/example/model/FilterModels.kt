package com.example.model

import androidx.compose.ui.graphics.Color

enum class FilterCategory(val label: String) {
    ALL("All LUTs"),
    FAVORITES("Starred"),
    FILM_STOCK("Analog 35mm"),
    CINEMA("Cinema Grade"),
    CYBER_NEON("Cyber & Neon"),
    MONOCHROME("B&W Darkroom"),
    RETRO_LIGHT("Light Leaks")
}

enum class AdjustmentParameter(
    val label: String,
    val shortTag: String,
    val minVal: Float,
    val maxVal: Float,
    val defaultVal: Float
) {
    INTENSITY("Filter LUT Strength", "LUT", 0f, 1f, 1f),
    EXPOSURE("Optical Exposure", "EV", -0.6f, 0.6f, 0f),
    CONTRAST("Tone Contrast", "CTR", -0.4f, 0.7f, 0f),
    WARMTH("Color Temp", "WB", -0.8f, 0.8f, 0f),
    SATURATION("Vibrance", "SAT", -1f, 0.8f, 0f),
    VIGNETTE("Lens Vignette", "VIG", 0f, 1f, 0.25f),
    GRAIN("35mm Silver Grain", "GRN", 0f, 1f, 0.15f),
    LIGHT_LEAK("Prism Light Leak", "LEAK", 0f, 1f, 0f)
}

data class FilterAdjustments(
    val intensity: Float = 1.0f,
    val exposure: Float = 0.0f,
    val contrast: Float = 0.0f,
    val warmth: Float = 0.0f,
    val saturation: Float = 0.0f,
    val vignette: Float = 0.2f,
    val filmGrain: Float = 0.12f,
    val lightLeak: Float = 0.0f
) {
    fun getValue(param: AdjustmentParameter): Float = when (param) {
        AdjustmentParameter.INTENSITY -> intensity
        AdjustmentParameter.EXPOSURE -> exposure
        AdjustmentParameter.CONTRAST -> contrast
        AdjustmentParameter.WARMTH -> warmth
        AdjustmentParameter.SATURATION -> saturation
        AdjustmentParameter.VIGNETTE -> vignette
        AdjustmentParameter.GRAIN -> filmGrain
        AdjustmentParameter.LIGHT_LEAK -> lightLeak
    }

    fun withValue(param: AdjustmentParameter, value: Float): FilterAdjustments {
        val clamped = value.coerceIn(param.minVal, param.maxVal)
        return when (param) {
            AdjustmentParameter.INTENSITY -> copy(intensity = clamped)
            AdjustmentParameter.EXPOSURE -> copy(exposure = clamped)
            AdjustmentParameter.CONTRAST -> copy(contrast = clamped)
            AdjustmentParameter.WARMTH -> copy(warmth = clamped)
            AdjustmentParameter.SATURATION -> copy(saturation = clamped)
            AdjustmentParameter.VIGNETTE -> copy(vignette = clamped)
            AdjustmentParameter.GRAIN -> copy(filmGrain = clamped)
            AdjustmentParameter.LIGHT_LEAK -> copy(lightLeak = clamped)
        }
    }
}

enum class FrameStyle(val label: String, val badge: String) {
    NONE("Full Bleed", "RAW"),
    POLAROID("Instant Film", "INSTAX"),
    CINEMA_SCOPE("Anamorphic 2.35", "CINE"),
    GALLERY_MATTE("Museum EXIF", "EXIF")
}

enum class AspectRatioPreset(val label: String, val ratioWidth: Float, val ratioHeight: Float) {
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_9_16("9:16", 9f, 16f)
}

enum class FlashModePreset(val label: String) {
    OFF("OFF"),
    AUTO("AUTO"),
    ON("ON"),
    TORCH("TORCH")
}

enum class ViewfinderMode(val label: String) {
    LIVE_CAMERA("LIVE OPTICS"),
    STUDIO_CANVAS("STUDIO CANVAS")
}

enum class StudioScene(
    val id: String,
    val title: String,
    val subtitle: String,
    val focalSpec: String
) {
    TOKYO_NEON("tokyo_neon", "Shinjuku Rain", "Night Neon & Wet Asphalt", "35mm f/1.4 • ISO 800"),
    AMALFI_SUNSET("amalfi_sunset", "Solstice Coast", "Warm Golden Hour Cliffs", "50mm f/1.8 • ISO 100"),
    CYBER_PORTRAIT("cyber_portrait", "Prism Studio", "Dual-Gel Portrait Lighting", "85mm f/1.2 • ISO 320"),
    ALPINE_DUSK("alpine_dusk", "Dolomite Blue", "High Altitude Alpenglow", "24mm f/2.8 • ISO 200"),
    CUSTOM_IMPORT("custom_import", "Imported Photo", "User Selected Device Image", "Custom Source")
}

data class OpticalFilter(
    val id: String,
    val name: String,
    val shortCode: String,
    val category: FilterCategory,
    val description: String,
    val isoTag: String,
    val accentColor: Color,
    val matrix: FloatArray, // 20-element 4x5 Android ColorMatrix
    val overlayStartArgb: Int = 0x00000000,
    val overlayEndArgb: Int = 0x00000000,
    val overlayIntensity: Float = 0f,
    val defaultVignette: Float = 0.2f,
    val defaultGrain: Float = 0.1f,
    val defaultLightLeak: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpticalFilter) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class CapturedPhoto(
    val id: String,
    val filePath: String,
    val filterId: String,
    val filterName: String,
    val filterCode: String,
    val isoTag: String,
    val frameStyle: FrameStyle,
    val caption: String,
    val timestampMillis: Long,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false
)

object FilterCatalog {
    val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    val filters: List<OpticalFilter> = listOf(
        OpticalFilter(
            id = "natural_zero",
            name = "Optical Zero",
            shortCode = "STD 00",
            category = FilterCategory.FILM_STOCK,
            description = "Unaltered neutral glass calibration with true-to-life dynamic range.",
            isoTag = "ISO 100 • 50MM",
            accentColor = Color(0xFFE2E8F0),
            matrix = IDENTITY_MATRIX,
            defaultVignette = 0.05f,
            defaultGrain = 0.0f
        ),
        OpticalFilter(
            id = "kodak_gold_400",
            name = "Portra Gold 400",
            shortCode = "GOLD 400",
            category = FilterCategory.FILM_STOCK,
            description = "Warm golden highlights, creamy skin tones, and nostalgic analog roll-off.",
            isoTag = "ISO 400 • 35MM",
            accentColor = Color(0xFFFFB300),
            matrix = floatArrayOf(
                1.18f, 0.06f, -0.04f, 0f, 10f,
                0.03f, 1.09f, -0.02f, 0f, 6f,
                -0.06f, 0.02f, 0.88f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x33FF9800,
            overlayEndArgb = 0x223E2723,
            overlayIntensity = 0.18f,
            defaultVignette = 0.30f,
            defaultGrain = 0.22f
        ),
        OpticalFilter(
            id = "cine_teal_orange",
            name = "Anamorphic Teal",
            shortCode = "CINE T&O",
            category = FilterCategory.CINEMA,
            description = "Blockbuster split-toning pushing shadows into deep cyan and midtones into amber.",
            isoTag = "T1.4 • 2.35:1",
            accentColor = Color(0xFF00E5FF),
            matrix = floatArrayOf(
                1.24f, 0.05f, -0.12f, 0f, 8f,
                -0.04f, 1.06f, 0.08f, 0f, 2f,
                -0.18f, 0.12f, 1.22f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x38FF6D00,
            overlayEndArgb = 0x40006064,
            overlayIntensity = 0.24f,
            defaultVignette = 0.42f,
            defaultGrain = 0.14f
        ),
        OpticalFilter(
            id = "cyber_2077",
            name = "Neon Dystopia",
            shortCode = "CYBER 88",
            category = FilterCategory.CYBER_NEON,
            description = "Electric magenta and laser cyan split grade designed for night street lights.",
            isoTag = "ISO 1600 • f/1.2",
            accentColor = Color(0xFFFF2A85),
            matrix = floatArrayOf(
                1.28f, -0.10f, 0.24f, 0f, 6f,
                -0.12f, 0.96f, 0.18f, 0f, -8f,
                0.16f, -0.05f, 1.38f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x45FF007F,
            overlayEndArgb = 0x4500E5FF,
            overlayIntensity = 0.30f,
            defaultVignette = 0.45f,
            defaultGrain = 0.18f,
            defaultLightLeak = 0.20f
        ),
        OpticalFilter(
            id = "fuji_velvia_50",
            name = "Velvia Emerald",
            shortCode = "VELVIA 50",
            category = FilterCategory.FILM_STOCK,
            description = "Slide film saturation with lush emerald greens, deep cobalt skies, and crisp micro-contrast.",
            isoTag = "ISO 50 • SLIDE",
            accentColor = Color(0xFF10B981),
            matrix = floatArrayOf(
                1.22f, -0.08f, -0.04f, 0f, -6f,
                -0.05f, 1.28f, -0.06f, 0f, 4f,
                -0.06f, -0.04f, 1.25f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x2200E676,
            overlayEndArgb = 0x220D47A1,
            overlayIntensity = 0.14f,
            defaultVignette = 0.25f,
            defaultGrain = 0.08f
        ),
        OpticalFilter(
            id = "leica_noir",
            name = "Silver Halide B&W",
            shortCode = "NOIR M11",
            category = FilterCategory.MONOCHROME,
            description = "High-contrast panchromatic monochrome with rich obsidian blacks and glowing silver highlights.",
            isoTag = "ISO 400 • MONO",
            accentColor = Color(0xFFCBD5E1),
            matrix = floatArrayOf(
                0.38f, 0.72f, 0.14f, 0f, -18f,
                0.38f, 0.72f, 0.14f, 0f, -18f,
                0.38f, 0.72f, 0.14f, 0f, -18f,
                0f, 0f, 0f, 1f, 0f
            ),
            defaultVignette = 0.48f,
            defaultGrain = 0.35f
        ),
        OpticalFilter(
            id = "tokyo_rain_800T",
            name = "Tungsten 800T",
            shortCode = "CINE 800T",
            category = FilterCategory.CINEMA,
            description = "Motion picture tungsten stock with halation glow and cool emerald-blue night shadows.",
            isoTag = "ISO 800T • 35MM",
            accentColor = Color(0xFF38BDF8),
            matrix = floatArrayOf(
                0.96f, 0.04f, 0.08f, 0f, -4f,
                0.02f, 1.12f, 0.08f, 0f, 6f,
                -0.04f, 0.14f, 1.26f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x3000B0FF,
            overlayEndArgb = 0x35FF3D00,
            overlayIntensity = 0.22f,
            defaultVignette = 0.36f,
            defaultGrain = 0.26f,
            defaultLightLeak = 0.15f
        ),
        OpticalFilter(
            id = "solaris_flare",
            name = "Solaris Prism Leak",
            shortCode = "SOLAR 77",
            category = FilterCategory.RETRO_LIGHT,
            description = "Sun-drenched vintage film burn with warm amber light leaks and lifted matte shadows.",
            isoTag = "ISO 200 • LEAK",
            accentColor = Color(0xFFFF6D00),
            matrix = floatArrayOf(
                1.25f, 0.10f, 0.02f, 0f, 16f,
                0.06f, 1.05f, 0.00f, 0f, 8f,
                -0.04f, 0.02f, 0.82f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x66FF3D00,
            overlayEndArgb = 0x22FFD600,
            overlayIntensity = 0.35f,
            defaultVignette = 0.22f,
            defaultGrain = 0.24f,
            defaultLightLeak = 0.65f
        ),
        OpticalFilter(
            id = "polaroid_sx70",
            name = "Instant SX-70",
            shortCode = "SX-70",
            category = FilterCategory.RETRO_LIGHT,
            description = "Dreamy instant film chemistry with faded milky blacks, warm yellow cast, and soft roll-off.",
            isoTag = "ISO 160 • INSTANT",
            accentColor = Color(0xFFFBBF24),
            matrix = floatArrayOf(
                1.08f, 0.10f, -0.02f, 0f, 18f,
                0.04f, 1.04f, 0.02f, 0f, 14f,
                -0.02f, 0.06f, 0.86f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x28FFF59D,
            overlayEndArgb = 0x28004D40,
            overlayIntensity = 0.20f,
            defaultVignette = 0.38f,
            defaultGrain = 0.20f,
            defaultLightLeak = 0.25f
        ),
        OpticalFilter(
            id = "bleach_bypass",
            name = "Silver Retention",
            shortCode = "BLEACH 99",
            category = FilterCategory.CINEMA,
            description = "Gritty silver-retention darkroom process combining crushed desaturated tones with metallic contrast.",
            isoTag = "ISO 640 • LAB",
            accentColor = Color(0xFF94A3B8),
            matrix = floatArrayOf(
                0.78f, 0.35f, 0.12f, 0f, -16f,
                0.24f, 0.88f, 0.12f, 0f, -16f,
                0.22f, 0.32f, 0.72f, 0f, -14f,
                0f, 0f, 0f, 1f, 0f
            ),
            defaultVignette = 0.50f,
            defaultGrain = 0.32f
        ),
        OpticalFilter(
            id = "aerochrome_ir",
            name = "Aerochrome Infrared",
            shortCode = "IR AERO",
            category = FilterCategory.CYBER_NEON,
            description = "Surreal false-color infrared emulsion shifting foliage and midtones into crimson scarlet.",
            isoTag = "ISO 400 • IR720",
            accentColor = Color(0xFFF43F5E),
            matrix = floatArrayOf(
                0.45f, 0.85f, -0.10f, 0f, 10f,
                0.25f, 0.35f, 0.35f, 0f, -6f,
                -0.10f, 0.25f, 0.95f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            ),
            overlayStartArgb = 0x35FF1744,
            overlayEndArgb = 0x25311B92,
            overlayIntensity = 0.22f,
            defaultVignette = 0.32f,
            defaultGrain = 0.18f
        ),
        OpticalFilter(
            id = "sepia_platinum",
            name = "Platinum Palladium",
            shortCode = "PLATINUM",
            category = FilterCategory.MONOCHROME,
            description = "Archival 19th-century warm toned darkroom print with velvety chocolate shadows.",
            isoTag = "ISO 80 • PLATE",
            accentColor = Color(0xFFD97706),
            matrix = floatArrayOf(
                0.44f, 0.68f, 0.16f, 0f, -6f,
                0.36f, 0.60f, 0.14f, 0f, -10f,
                0.26f, 0.46f, 0.12f, 0f, -16f,
                0f, 0f, 0f, 1f, 0f
            ),
            defaultVignette = 0.45f,
            defaultGrain = 0.28f
        )
    )

    fun findById(id: String): OpticalFilter =
        filters.firstOrNull { it.id == id } ?: filters.first()
}
