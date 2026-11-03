package com.example.domain

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.CapturedPhoto
import com.example.model.FilterAdjustments
import com.example.model.FilterCatalog
import com.example.model.FrameStyle
import com.example.model.OpticalFilter
import com.example.model.StudioScene
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object FilterEngine {

    /**
     * Combines the filter's base 4x5 ColorMatrix (interpolated by adjustments.intensity)
     * with user-tuned Exposure, Contrast, Warmth, and Saturation into a single 20-float array.
     */
    fun buildCombinedColorMatrix(
        filter: OpticalFilter,
        adjustments: FilterAdjustments
    ): FloatArray {
        val t = adjustments.intensity.coerceIn(0f, 1f)
        val id = FilterCatalog.IDENTITY_MATRIX
        val src = filter.matrix
        val blended = FloatArray(20) { i ->
            id[i] + (src[i] - id[i]) * t
        }

        val combined = ColorMatrix(blended)

        // 1. Saturation
        val satFactor = (1f + adjustments.saturation).coerceIn(0f, 2.2f)
        if (satFactor != 1f) {
            val satMatrix = ColorMatrix().apply { setSaturation(satFactor) }
            combined.postConcat(satMatrix)
        }

        // 2. Contrast & Exposure
        val contrastScale = (1f + adjustments.contrast).coerceIn(0.4f, 2.0f)
        val contrastTranslate = (1f - contrastScale) * 128f
        val exposureShift = adjustments.exposure * 85f

        // 3. Warmth (R/B channel balance)
        val warmR = 1f + adjustments.warmth * 0.16f
        val warmG = 1f + adjustments.warmth * 0.03f
        val warmB = 1f - adjustments.warmth * 0.18f

        val gradeMatrix = ColorMatrix(
            floatArrayOf(
                contrastScale * warmR, 0f, 0f, 0f, contrastTranslate + exposureShift + (adjustments.warmth * 12f),
                0f, contrastScale * warmG, 0f, 0f, contrastTranslate + exposureShift,
                0f, 0f, contrastScale * warmB, 0f, contrastTranslate + exposureShift - (adjustments.warmth * 12f),
                0f, 0f, 0f, 1f, 0f
            )
        )
        combined.postConcat(gradeMatrix)

        return combined.array
    }

    /**
     * Renders a full high-resolution Bitmap with the active OpticalFilter, FilterAdjustments,
     * light leaks, lens vignette, 35mm film grain, retro date stamp, and FrameStyle.
     */
    fun renderFilteredBitmap(
        source: Bitmap,
        filter: OpticalFilter,
        adjustments: FilterAdjustments,
        frameStyle: FrameStyle,
        showDateStamp: Boolean,
        caption: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): Bitmap {
        val w = source.width.coerceAtLeast(64)
        val h = source.height.coerceAtLeast(64)

        val coreBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(coreBitmap)

        // 1. Draw base image through combined ColorMatrix
        val matrixArray = buildCombinedColorMatrix(filter, adjustments)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixArray))
        }
        canvas.drawBitmap(source, 0f, 0f, basePaint)

        // 2. Apply optical dual-tone overlay from filter
        val overlayAlpha = (filter.overlayIntensity * adjustments.intensity).coerceIn(0f, 0.75f)
        if (overlayAlpha > 0.01f && (filter.overlayStartArgb != 0 || filter.overlayEndArgb != 0)) {
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    filter.overlayStartArgb,
                    filter.overlayEndArgb,
                    Shader.TileMode.CLAMP
                )
                alpha = (overlayAlpha * 255).toInt().coerceIn(0, 255)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), overlayPaint)
        }

        // 3. Apply Prism Light Leak if enabled
        val leakStrength = adjustments.lightLeak.coerceIn(0f, 1f)
        if (leakStrength > 0.02f) {
            val leakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.12f,
                    h * 0.22f,
                    max(w, h) * 0.75f,
                    intArrayOf(
                        Color.argb((leakStrength * 210).toInt(), 255, 95, 20),
                        Color.argb((leakStrength * 140).toInt(), 255, 190, 40),
                        Color.argb((leakStrength * 60).toInt(), 255, 40, 120),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), leakPaint)
        }

        // 4. Apply Radial Optical Vignette
        val vigStrength = adjustments.vignette.coerceIn(0f, 1f)
        if (vigStrength > 0.02f) {
            val radius = max(w, h) * 0.72f
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w / 2f,
                    h / 2f,
                    radius,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb((vigStrength * 85).toInt(), 8, 9, 14),
                        Color.argb((vigStrength * 225).toInt(), 4, 5, 8)
                    ),
                    floatArrayOf(0.42f, 0.78f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vigPaint)
        }

        // 5. Apply 35mm Silver-Halide Film Grain
        val grainStrength = adjustments.filmGrain.coerceIn(0f, 1f)
        if (grainStrength > 0.02f) {
            val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rng = Random(42)
            val specks = (w * h / 110).coerceAtMost(9500)
            val maxAlpha = (grainStrength * 75).toInt().coerceIn(8, 90)
            for (i in 0 until specks) {
                val gx = rng.nextFloat() * w
                val gy = rng.nextFloat() * h
                val isLight = rng.nextBoolean()
                grainPaint.color = if (isLight) {
                    Color.argb(rng.nextInt(4, maxAlpha), 245, 242, 235)
                } else {
                    Color.argb(rng.nextInt(4, maxAlpha), 15, 15, 20)
                }
                val r = (w / 620f).coerceAtLeast(1.1f)
                canvas.drawCircle(gx, gy, r, grainPaint)
            }
        }

        // 6. Optional Retro Quartz 35mm Date Stamp
        if (showDateStamp) {
            val stampFormat = SimpleDateFormat("''yy MM dd  HH:mm", Locale.US)
            val stampText = "${stampFormat.format(Date(timestampMillis))}  •  ${filter.shortCode}"
            val textSize = (w * 0.033f).coerceAtLeast(20f)
            val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 145, 25)
                this.textSize = textSize
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setShadowLayer(textSize * 0.35f, 0f, 0f, Color.argb(210, 255, 65, 0))
            }
            val textWidth = stampPaint.measureText(stampText)
            val padX = w * 0.055f
            val padY = if (frameStyle == FrameStyle.CINEMA_SCOPE) h * 0.86f else h * 0.94f
            canvas.drawText(stampText, w - textWidth - padX, padY, stampPaint)
        }

        // 7. Apply FrameStyle wrapper
        return applyFrameStyle(
            core = coreBitmap,
            frameStyle = frameStyle,
            filter = filter,
            caption = caption,
            timestampMillis = timestampMillis
        )
    }

    private fun applyFrameStyle(
        core: Bitmap,
        frameStyle: FrameStyle,
        filter: OpticalFilter,
        caption: String,
        timestampMillis: Long
    ): Bitmap {
        val w = core.width
        val h = core.height

        return when (frameStyle) {
            FrameStyle.NONE -> core

            FrameStyle.CINEMA_SCOPE -> {
                val canvas = Canvas(core)
                val barHeight = h * 0.11f
                val barPaint = Paint().apply { color = Color.rgb(8, 9, 12) }
                canvas.drawRect(0f, 0f, w.toFloat(), barHeight, barPaint)
                canvas.drawRect(0f, h - barHeight, w.toFloat(), h.toFloat(), barPaint)

                val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(200, 255, 179, 0)
                    textSize = (w * 0.024f).coerceAtLeast(16f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    letterSpacing = 0.12f
                }
                val topLabel = "LUMINALENS ANAMORPHIC  •  ${filter.shortCode}  •  ${filter.isoTag}"
                canvas.drawText(topLabel, w * 0.05f, barHeight * 0.62f, metaPaint)

                val bottomLabel = if (caption.isNotBlank()) {
                    "\"${caption.trim()}\""
                } else {
                    SimpleDateFormat("yyyy.MM.dd // HH:mm:ss 'TC'", Locale.US).format(Date(timestampMillis))
                }
                metaPaint.color = Color.argb(220, 235, 240, 248)
                val bw = metaPaint.measureText(bottomLabel)
                canvas.drawText(bottomLabel, (w - bw) / 2f, h - barHeight * 0.38f, metaPaint)
                core
            }

            FrameStyle.POLAROID -> {
                val borderSide = (w * 0.075f).toInt()
                val borderTop = (w * 0.075f).toInt()
                val borderBottom = (h * 0.22f).toInt()
                val outW = w + borderSide * 2
                val outH = h + borderTop + borderBottom
                val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(outBitmap)

                // Warm archival paper stock
                canvas.drawColor(Color.rgb(248, 245, 238))

                // Subtle inner shadow around photo window
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(220, 215, 205)
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawBitmap(core, borderSide.toFloat(), borderTop.toFloat(), null)
                canvas.drawRect(
                    borderSide.toFloat(),
                    borderTop.toFloat(),
                    (borderSide + w).toFloat(),
                    (borderTop + h).toFloat(),
                    borderPaint
                )

                // Caption & LUT signature in bottom margin
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(30, 33, 42)
                    textSize = (outW * 0.040f).coerceAtLeast(24f)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                }
                val displayCaption = if (caption.isNotBlank()) caption.trim() else filter.name
                canvas.drawText(
                    displayCaption,
                    borderSide.toFloat() + 8f,
                    (borderTop + h + borderBottom * 0.48f),
                    titlePaint
                )

                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(120, 124, 135)
                    textSize = (outW * 0.024f).coerceAtLeast(16f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(timestampMillis))
                val metaLine = "${filter.shortCode}  •  ${filter.isoTag}  •  $dateStr"
                canvas.drawText(
                    metaLine,
                    borderSide.toFloat() + 8f,
                    (borderTop + h + borderBottom * 0.76f),
                    subPaint
                )
                outBitmap
            }

            FrameStyle.GALLERY_MATTE -> {
                val pad = (w * 0.09f).toInt()
                val bottomExtra = (h * 0.12f).toInt()
                val outW = w + pad * 2
                val outH = h + pad * 2 + bottomExtra
                val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(outBitmap)

                canvas.drawColor(Color.rgb(16, 18, 24))
                canvas.drawBitmap(core, pad.toFloat(), pad.toFloat(), null)

                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(255, 179, 0)
                    strokeWidth = 4f
                }
                val footerY = (pad + h + bottomExtra * 0.45f)
                canvas.drawLine(pad.toFloat(), footerY - 24f, (pad + w * 0.14f), footerY - 24f, linePaint)

                val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(240, 243, 249)
                    textSize = (outW * 0.028f).coerceAtLeast(18f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val leftText = if (caption.isNotBlank()) {
                    "${caption.trim().uppercase(Locale.US)}  //  ${filter.name.uppercase(Locale.US)}"
                } else {
                    "LUMINALENS OPTICS  //  ${filter.name.uppercase(Locale.US)}"
                }
                canvas.drawText(leftText, pad.toFloat(), footerY + 18f, exifPaint)

                exifPaint.color = Color.rgb(148, 163, 184)
                exifPaint.textSize = (outW * 0.022f).coerceAtLeast(14f)
                val rightSpec = "${filter.shortCode}   ${filter.isoTag}   ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestampMillis))
                }"
                canvas.drawText(rightSpec, pad.toFloat(), footerY + 54f, exifPaint)
                outBitmap
            }
        }
    }

    /**
     * Generates vivid, high-dynamic-range Studio Canvas photographic test scenes
     * so users can experience real-time LUT grading on rich highlights, shadows, neon, and skin tones.
     */
    fun generateStudioSceneBitmap(
        scene: StudioScene,
        width: Int = 900,
        height: Int = 1200
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()

        when (scene) {
            StudioScene.TOKYO_NEON -> {
                // Deep twilight sky to wet street asphalt
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(
                        Color.rgb(12, 16, 38),
                        Color.rgb(28, 22, 58),
                        Color.rgb(45, 25, 65),
                        Color.rgb(14, 18, 28)
                    ),
                    floatArrayOf(0f, 0.42f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                // Perspective street canyon buildings
                paint.color = Color.rgb(10, 12, 22)
                canvas.drawRect(0f, 0f, w * 0.24f, h * 0.72f, paint)
                canvas.drawRect(w * 0.74f, 0f, w, h * 0.72f, paint)
                paint.color = Color.rgb(18, 20, 34)
                canvas.drawRect(w * 0.16f, h * 0.08f, w * 0.33f, h * 0.68f, paint)
                canvas.drawRect(w * 0.66f, h * 0.05f, w * 0.82f, h * 0.68f, paint)

                // Glowing neon signs & out-of-focus bokeh orbs
                val neonColors = intArrayOf(
                    Color.rgb(255, 45, 135),
                    Color.rgb(0, 229, 255),
                    Color.rgb(255, 179, 0),
                    Color.rgb(16, 185, 129),
                    Color.rgb(255, 87, 34)
                )
                val rng = Random(101)
                for (i in 0 until 28) {
                    val cx = rng.nextFloat() * w
                    val cy = rng.nextFloat() * (h * 0.68f) + (h * 0.08f)
                    val radius = (rng.nextFloat() * 0.09f + 0.025f) * w
                    val c = neonColors[i % neonColors.size]
                    paint.shader = RadialGradient(
                        cx, cy, radius,
                        intArrayOf(
                            Color.argb(230, Color.red(c), Color.green(c), Color.blue(c)),
                            Color.argb(95, Color.red(c), Color.green(c), Color.blue(c)),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(cx, cy, radius, paint)
                }

                // Wet asphalt specular reflections
                for (i in 0 until 22) {
                    val ry = h * 0.68f + (i / 22f) * (h * 0.32f)
                    val rx = (rng.nextFloat() * 0.7f + 0.15f) * w
                    val rw = (rng.nextFloat() * 0.28f + 0.08f) * w
                    val c = neonColors[i % neonColors.size]
                    paint.shader = LinearGradient(
                        rx - rw / 2, ry, rx + rw / 2, ry,
                        intArrayOf(
                            Color.TRANSPARENT,
                            Color.argb(175, Color.red(c), Color.green(c), Color.blue(c)),
                            Color.TRANSPARENT
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(
                        RectF(rx - rw / 2, ry, rx + rw / 2, ry + h * 0.012f),
                        12f, 12f, paint
                    )
                }
                paint.shader = null

                // Silhouette umbrella figure in crosswalk
                paint.color = Color.rgb(8, 10, 16)
                val arcRect = RectF(w * 0.34f, h * 0.44f, w * 0.66f, h * 0.62f)
                canvas.drawArc(arcRect, 180f, 180f, true, paint)
                canvas.drawRoundRect(RectF(w * 0.46f, h * 0.52f, w * 0.54f, h * 0.78f), 20f, 20f, paint)
            }

            StudioScene.AMALFI_SUNSET -> {
                // Warm golden hour coastal sky
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.62f,
                    intArrayOf(
                        Color.rgb(38, 68, 145),
                        Color.rgb(185, 92, 110),
                        Color.rgb(255, 148, 66),
                        Color.rgb(255, 218, 125)
                    ),
                    floatArrayOf(0f, 0.38f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h * 0.62f, paint)

                // Sun orb & atmospheric flare
                paint.shader = RadialGradient(
                    w * 0.65f, h * 0.44f, w * 0.38f,
                    intArrayOf(
                        Color.rgb(255, 250, 220),
                        Color.argb(190, 255, 195, 75),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.32f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(w * 0.65f, h * 0.44f, w * 0.38f, paint)

                // Mediterranean turquoise sea
                paint.shader = LinearGradient(
                    0f, h * 0.58f, 0f, h,
                    intArrayOf(
                        Color.rgb(20, 148, 168),
                        Color.rgb(12, 92, 124),
                        Color.rgb(8, 42, 68)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, h * 0.58f, w, h, paint)

                // Sun shimmer on water
                paint.shader = null
                val rng = Random(202)
                for (i in 0 until 26) {
                    val sy = h * 0.60f + (i / 26f) * (h * 0.36f)
                    val sw = (1f - i / 32f) * w * 0.26f
                    val sx = w * 0.65f + (rng.nextFloat() - 0.5f) * w * 0.08f
                    paint.color = Color.argb(180 - i * 4, 255, 222, 130)
                    canvas.drawRoundRect(RectF(sx - sw / 2, sy, sx + sw / 2, sy + 6f), 4f, 4f, paint)
                }

                // Coastal cliff silhouette
                val cliff = Path().apply {
                    moveTo(0f, h * 0.32f)
                    lineTo(w * 0.28f, h * 0.42f)
                    lineTo(w * 0.46f, h * 0.64f)
                    lineTo(w * 0.35f, h)
                    lineTo(0f, h)
                    close()
                }
                paint.color = Color.rgb(34, 26, 38)
                canvas.drawPath(cliff, paint)
            }

            StudioScene.CYBER_PORTRAIT -> {
                // Dual-gel studio background (Magenta left, Cyan right)
                paint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                        Color.rgb(42, 12, 64),
                        Color.rgb(18, 22, 42),
                        Color.rgb(8, 52, 78)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)

                // Key light halo (warm coral/magenta) & rim light (electric cyan)
                paint.shader = RadialGradient(
                    w * 0.28f, h * 0.38f, w * 0.52f,
                    intArrayOf(Color.argb(210, 255, 50, 120), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(w * 0.28f, h * 0.38f, w * 0.52f, paint)

                paint.shader = RadialGradient(
                    w * 0.76f, h * 0.42f, w * 0.48f,
                    intArrayOf(Color.argb(210, 0, 229, 255), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(w * 0.76f, h * 0.42f, w * 0.48f, paint)

                // Neon studio ring behind subject
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.018f
                paint.color = Color.rgb(255, 195, 40)
                canvas.drawCircle(w * 0.5f, h * 0.42f, w * 0.31f, paint)
                paint.style = Paint.Style.FILL

                // Stylized studio bust with realistic warm skin-tone gradient for testing skin LUT accuracy
                paint.shader = LinearGradient(
                    w * 0.32f, h * 0.25f, w * 0.68f, h * 0.65f,
                    intArrayOf(
                        Color.rgb(242, 188, 162),
                        Color.rgb(208, 142, 118),
                        Color.rgb(112, 68, 78)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                // Shoulders
                canvas.drawRoundRect(RectF(w * 0.22f, h * 0.58f, w * 0.78f, h * 0.96f), w * 0.18f, w * 0.18f, paint)
                // Neck & Head
                canvas.drawRoundRect(RectF(w * 0.43f, h * 0.46f, w * 0.57f, h * 0.64f), 28f, 28f, paint)
                canvas.drawOval(RectF(w * 0.35f, h * 0.24f, w * 0.65f, h * 0.54f), paint)

                // Stylish visor / sunglasses reflection
                paint.shader = LinearGradient(
                    w * 0.36f, h * 0.35f, w * 0.64f, h * 0.41f,
                    intArrayOf(Color.rgb(0, 229, 255), Color.rgb(255, 42, 133)),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(RectF(w * 0.36f, h * 0.35f, w * 0.64f, h * 0.41f), 24f, 24f, paint)
            }

            StudioScene.ALPINE_DUSK, StudioScene.CUSTOM_IMPORT -> {
                // Crisp high-altitude alpenglow mountains & emerald pine lake
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.65f,
                    intArrayOf(
                        Color.rgb(18, 32, 68),
                        Color.rgb(65, 88, 142),
                        Color.rgb(235, 145, 130),
                        Color.rgb(252, 210, 165)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h * 0.65f, paint)

                // Far alpenglow peaks
                paint.shader = LinearGradient(
                    0f, h * 0.28f, 0f, h * 0.65f,
                    intArrayOf(Color.rgb(245, 135, 120), Color.rgb(52, 62, 98)),
                    null,
                    Shader.TileMode.CLAMP
                )
                val farPeaks = Path().apply {
                    moveTo(0f, h * 0.65f)
                    lineTo(0f, h * 0.46f)
                    lineTo(w * 0.22f, h * 0.30f)
                    lineTo(w * 0.44f, h * 0.48f)
                    lineTo(w * 0.68f, h * 0.26f)
                    lineTo(w * 0.90f, h * 0.44f)
                    lineTo(w, h * 0.36f)
                    lineTo(w, h * 0.65f)
                    close()
                }
                canvas.drawPath(farPeaks, paint)

                // Foreground emerald ridge
                paint.shader = null
                paint.color = Color.rgb(22, 48, 56)
                val nearRidge = Path().apply {
                    moveTo(0f, h * 0.65f)
                    lineTo(0f, h * 0.54f)
                    lineTo(w * 0.38f, h * 0.42f)
                    lineTo(w * 0.75f, h * 0.56f)
                    lineTo(w, h * 0.48f)
                    lineTo(w, h * 0.65f)
                    close()
                }
                canvas.drawPath(nearRidge, paint)

                // Mirror alpine lake
                paint.shader = LinearGradient(
                    0f, h * 0.65f, 0f, h,
                    intArrayOf(
                        Color.rgb(185, 130, 128),
                        Color.rgb(42, 85, 102),
                        Color.rgb(14, 34, 48)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, h * 0.65f, w, h, paint)
            }
        }

        return bmp
    }

    /**
     * Rotates and optionally mirrors a Bitmap (used for CameraX ImageProxy frames).
     */
    fun rotateAndMirrorBitmap(source: Bitmap, rotationDegrees: Int, mirrorHorizontal: Boolean): Bitmap {
        if (rotationDegrees == 0 && !mirrorHorizontal) return source
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            if (mirrorHorizontal) {
                postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Launches Android's native Share Sheet with FileProvider URI and preview thumbnail.
     */
    fun shareCapturedPhoto(
        context: Context,
        photo: CapturedPhoto,
        customShareNote: String = ""
    ): Boolean {
        return try {
            val sourceFile = File(photo.filePath)
            if (!sourceFile.exists()) return false

            val sharedDir = File(context.cacheDir, "shared_photos").apply { mkdirs() }
            val exportFile = File(sharedDir, "LuminaLens_${photo.filterCode.replace(" ", "_")}_${photo.id}.jpg")
            sourceFile.copyTo(exportFile, overwrite = true)

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val shareText = buildString {
                if (customShareNote.isNotBlank()) {
                    append(customShareNote.trim())
                    append("\n")
                } else if (photo.caption.isNotBlank()) {
                    append(photo.caption.trim())
                    append("\n")
                }
                append("Shot on LuminaLens • ${photo.filterName} (${photo.filterCode}) • ${photo.isoTag}")
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_TITLE, "${photo.filterName} • LuminaLens")
                clipData = ClipData.newRawUri(photo.filterName, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Share Filtered Photo").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Exports the captured photo into the device's public MediaStore Pictures/LuminaLens album.
     */
    fun exportToDeviceMediaStore(context: Context, photo: CapturedPhoto): Boolean {
        return try {
            val file = File(photo.filePath)
            if (!file.exists()) return false

            val resolver = context.contentResolver
            val fileName = "LuminaLens_${photo.filterCode.replace(" ", "")}_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LuminaLens")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { outStream ->
                file.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Saves a Bitmap to internal app storage under filesDir/captured_photos.
     */
    fun saveBitmapToInternalGallery(
        context: Context,
        bitmap: Bitmap,
        photoId: String
    ): File {
        val dir = File(context.filesDir, "captured_photos").apply { mkdirs() }
        val target = File(dir, "lumina_$photoId.jpg")
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
        return target
    }
}
