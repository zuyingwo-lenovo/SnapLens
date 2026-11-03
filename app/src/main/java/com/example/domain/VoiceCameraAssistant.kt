package com.example.domain

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.model.OpticalFilter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private const val TAG = "VoiceCameraAssistant"

enum class CameraVoiceAction {
    CAPTURE_PHOTO,
    SELECT_FILTER,
    NEXT_FILTER,
    PREV_FILTER,
    SWITCH_LENS,
    SET_FLASH,
    SET_FRAME,
    SET_ASPECT_RATIO,
    ADJUST_GRADE,
    TOGGLE_SPLIT_COMPARE,
    TOGGLE_GRID,
    TOGGLE_DATE_STAMP,
    OPEN_GALLERY,
    CLOSE_GALLERY,
    SHARE_LATEST,
    SAVE_LATEST,
    RESET_GRADE,
    NONE
}

data class ParsedVoiceCommand(
    val userTranscript: String,
    val spokenReply: String,
    val actions: List<CameraVoiceAction>,
    val filterId: String? = null,
    val frameStyle: String? = null,
    val flashMode: String? = null,
    val aspectRatio: String? = null,
    val intensity: Float? = null,
    val exposure: Float? = null,
    val contrast: Float? = null,
    val warmth: Float? = null,
    val vignette: Float? = null,
    val filmGrain: Float? = null,
    val lightLeak: Float? = null
)

data class VoiceTurnEntry(
    val id: String,
    val userText: String,
    val assistantReply: String,
    val executedBadges: List<String>,
    val timestampMillis: Long = System.currentTimeMillis()
)

sealed interface VoiceAssistantUiState {
    data object Idle : VoiceAssistantUiState
    data class Listening(val partialText: String = "", val amplitude: Float = 0.2f) : VoiceAssistantUiState
    data class Processing(val statusLabel: String = "Gemini Live AI 解析中...") : VoiceAssistantUiState
    data class Success(val result: ParsedVoiceCommand) : VoiceAssistantUiState
    data class Error(val message: String) : VoiceAssistantUiState
}

object VoiceCameraAssistant {

    /**
     * Lightweight, flat 5-field Schema.obj so Gemini's structured-output grammar compiler
     * compiles in <150ms instead of stalling on nested enum arrays.
     */
    private val fastVoiceSchema = Schema.obj(
        properties = mapOf(
            "userTranscript" to Schema.string(
                description = "Transcribed user command"
            ),
            "spokenReply" to Schema.string(
                description = "Concise, friendly voice response in Japanese (or English if user spoke English)"
            ),
            "action" to Schema.string(
                description = "Comma-separated actions from: CAPTURE_PHOTO, SELECT_FILTER, NEXT_FILTER, PREV_FILTER, SWITCH_LENS, SET_FLASH, SET_FRAME, SET_ASPECT_RATIO, ADJUST_GRADE, TOGGLE_SPLIT_COMPARE, TOGGLE_GRID, TOGGLE_DATE_STAMP, OPEN_GALLERY, CLOSE_GALLERY, SHARE_LATEST, SAVE_LATEST, RESET_GRADE"
            ),
            "targetId" to Schema.string(
                description = "Filter ID (kodak_gold_400, cine_teal_orange, cyber_2077, fuji_velvia_50, leica_noir, tokyo_rain_800T, solaris_flare, polaroid_sx70, bleach_bypass, aerochrome_ir, sepia_platinum, natural_zero) or FrameStyle (POLAROID, CINEMA_SCOPE, GALLERY_MATTE, NONE) or FlashMode (ON, OFF, AUTO, TORCH)"
            ),
            "gradePreset" to Schema.string(
                description = "Optional grade adjustment keyword: BRIGHTER, DARKER, MORE_VIGNETTE, MORE_GRAIN, WARMER, COOLER, or NONE"
            )
        ),
        optionalProperties = listOf("targetId", "gradePreset")
    )

    private val fastModelConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = fastVoiceSchema
    }

    // Lazy initialization per Firebase AI Logic guidelines
    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = fastModelConfig
        )
    }

    // Ultra-low-latency fallback model per Firebase AI Logic guidelines
    private val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = fastModelConfig
        )
    }

    /**
     * Fast local intent parser that provides instant (<10ms) pre-recognition of common
     * Japanese & English voice camera commands so camera controls respond immediately.
     */
    fun matchLocalFastCommand(rawText: String, currentFilter: OpticalFilter): ParsedVoiceCommand? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)

        // If user is asking an open-ended visual scene question, let Gemini multimodal analyze the image
        if (text.contains("シーン") || text.contains("おすすめ") || text.contains("合う") ||
            text.contains("どんな") || lower.contains("scene") || lower.contains("recommend")
        ) {
            return null
        }

        val actions = mutableListOf<CameraVoiceAction>()
        var filterId: String? = null
        var frameStyle: String? = null
        var flashMode: String? = null
        var exposure: Float? = null
        var vignette: Float? = null
        var filmGrain: Float? = null
        val replyParts = mutableListOf<String>()

        // 1. Filter detection
        when {
            text.contains("サイバー") || text.contains("ネオン") || lower.contains("cyber") || lower.contains("neon") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "cyber_2077"
                replyParts.add("ネオン・ディストピア(CYBER 88)フィルターを適用")
            }
            text.contains("映画") || text.contains("シネマ") || text.contains("ティール") || lower.contains("cinema") || lower.contains("teal") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "cine_teal_orange"
                replyParts.add("映画風のAnamorphic Teal(CINE T&O)フィルターに変更")
            }
            text.contains("モノクロ") || text.contains("白黒") || text.contains("ノワール") || lower.contains("mono") || lower.contains("noir") || lower.contains("b&w") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "leica_noir"
                replyParts.add("Silver Halide B&W(モノクロ)フィルターに変更")
            }
            text.contains("ゴールド") || text.contains("ポートラ") || text.contains("フィルム") || lower.contains("portra") || lower.contains("gold") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "kodak_gold_400"
                replyParts.add("Portra Gold 400フィルターを適用")
            }
            text.contains("ベルビア") || text.contains("エメラルド") || text.contains("鮮やか") || lower.contains("velvia") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "fuji_velvia_50"
                replyParts.add("Velvia Emeraldフィルターを適用")
            }
            text.contains("タングステン") || text.contains("夜") || lower.contains("tungsten") || lower.contains("800t") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "tokyo_rain_800T"
                replyParts.add("Tungsten 800Tフィルターを適用")
            }
            text.contains("ライトリーク") || text.contains("光漏れ") || text.contains("ソラリス") || lower.contains("solar") || lower.contains("leak") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "solaris_flare"
                replyParts.add("Solaris Prism Leakフィルターを適用")
            }
            text.contains("セピア") || text.contains("プラチナ") || lower.contains("sepia") || lower.contains("platinum") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "sepia_platinum"
                replyParts.add("Platinum Palladiumフィルターを適用")
            }
            text.contains("赤外線") || lower.contains("infrared") || lower.contains("aero") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "aerochrome_ir"
                replyParts.add("Aerochrome Infraredフィルターを適用")
            }
            text.contains("銀残し") || text.contains("ブリーチ") || lower.contains("bleach") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "bleach_bypass"
                replyParts.add("Silver Retention(銀残し)フィルターを適用")
            }
            text.contains("標準") || text.contains("ナチュラル") || text.contains("クリア") || lower.contains("natural") -> {
                actions.add(CameraVoiceAction.SELECT_FILTER)
                filterId = "natural_zero"
                replyParts.add("標準のOptical Zeroに戻")
            }
            text.contains("次のフィルター") || lower.contains("next filter") -> {
                actions.add(CameraVoiceAction.NEXT_FILTER)
                replyParts.add("次のフィルターに切り替え")
            }
            text.contains("前のフィルター") || lower.contains("previous filter") -> {
                actions.add(CameraVoiceAction.PREV_FILTER)
                replyParts.add("前のフィルターに切り替え")
            }
        }

        // 2. Frame detection
        when {
            text.contains("ポラロイド") || text.contains("インスタント") || lower.contains("polaroid") || lower.contains("instax") -> {
                actions.add(CameraVoiceAction.SET_FRAME)
                frameStyle = "POLAROID"
                replyParts.add("ポラロイド枠(INSTAX)を設定")
            }
            text.contains("シネマ枠") || text.contains("アナモフィック") || text.contains("レターボックス") -> {
                actions.add(CameraVoiceAction.SET_FRAME)
                frameStyle = "CINEMA_SCOPE"
                replyParts.add("シネマスコープ枠(2.35:1)を設定")
            }
            text.contains("美術館") || text.contains("マット枠") || lower.contains("exif") || lower.contains("matte") -> {
                actions.add(CameraVoiceAction.SET_FRAME)
                frameStyle = "GALLERY_MATTE"
                replyParts.add("EXIFギャラリー枠を設定")
            }
            text.contains("枠なし") || text.contains("フレームなし") -> {
                actions.add(CameraVoiceAction.SET_FRAME)
                frameStyle = "NONE"
                replyParts.add("フレームなし(RAW)に設定")
            }
        }

        // 3. Optical Grade Adjustments
        if (text.contains("明るく") || text.contains("露出を上げ") || lower.contains("brighter")) {
            actions.add(CameraVoiceAction.ADJUST_GRADE)
            exposure = 0.28f
            replyParts.add("露出を明るく(+EV)調整")
        } else if (text.contains("暗く") || text.contains("露出を下げ") || lower.contains("darker")) {
            actions.add(CameraVoiceAction.ADJUST_GRADE)
            exposure = -0.25f
            replyParts.add("露出を暗く(-EV)調整")
        }
        if (text.contains("ビネット") || text.contains("周辺減光") || lower.contains("vignette")) {
            if (!actions.contains(CameraVoiceAction.ADJUST_GRADE)) actions.add(CameraVoiceAction.ADJUST_GRADE)
            vignette = 0.65f
            replyParts.add("レンズビネットを強調")
        }
        if (text.contains("粒子") || text.contains("グレイン") || text.contains("ザラザラ") || lower.contains("grain")) {
            if (!actions.contains(CameraVoiceAction.ADJUST_GRADE)) actions.add(CameraVoiceAction.ADJUST_GRADE)
            filmGrain = 0.55f
            replyParts.add("35mmフィルムグレインを強調")
        }
        if (text.contains("リセット") || lower.contains("reset")) {
            actions.add(CameraVoiceAction.RESET_GRADE)
            replyParts.add("光学グレード調整をリセット")
        }

        // 4. Camera hardware & HUD controls
        if (text.contains("比較") || text.contains("ビフォーアフター") || text.contains("ルーペ") || lower.contains("compare") || lower.contains("split")) {
            actions.add(CameraVoiceAction.TOGGLE_SPLIT_COMPARE)
            replyParts.add("ビフォーアフター比較ルーペを切り替え")
        }
        if (text.contains("インカメ") || text.contains("外カメ") || text.contains("カメラを切り替") || text.contains("レンズを切り替") || lower.contains("flip") || lower.contains("switch camera")) {
            actions.add(CameraVoiceAction.SWITCH_LENS)
            replyParts.add("カメラレンズを切り替え")
        }
        if (text.contains("フラッシュ") || text.contains("ライト") || lower.contains("flash") || lower.contains("torch")) {
            actions.add(CameraVoiceAction.SET_FLASH)
            flashMode = if (text.contains("オフ") || lower.contains("off")) "OFF" else "TORCH"
            replyParts.add("フラッシュモードを変更")
        }
        if (text.contains("グリッド") || lower.contains("grid")) {
            actions.add(CameraVoiceAction.TOGGLE_GRID)
            replyParts.add("グリッド表示を切り替え")
        }
        if (text.contains("日付") || text.contains("スタンプ") || lower.contains("date")) {
            actions.add(CameraVoiceAction.TOGGLE_DATE_STAMP)
            replyParts.add("日付スタンプを切り替え")
        }

        // 5. Gallery & Share
        if (text.contains("ギャラリー") || text.contains("アルバム") || text.contains("写真を見せ") || lower.contains("gallery")) {
            actions.add(CameraVoiceAction.OPEN_GALLERY)
            replyParts.add("ローカルRoomギャラリーを開き")
        }
        if (text.contains("シェア") || text.contains("共有") || text.contains("送る") || lower.contains("share")) {
            actions.add(CameraVoiceAction.SHARE_LATEST)
            replyParts.add("最新の写真を共有シートで開き")
        }

        // 6. Shutter Capture
        if (text.contains("撮って") || text.contains("撮影") || text.contains("シャッター") ||
            text.contains("チーズ") || text.contains("パシャ") ||
            lower.contains("take a photo") || lower.contains("capture") || lower.contains("shoot")
        ) {
            actions.add(CameraVoiceAction.CAPTURE_PHOTO)
            replyParts.add("シャッターを切って保存")
        }

        if (actions.isEmpty()) return null

        val reply = if (replyParts.isNotEmpty()) {
            "${replyParts.joinToString("、")}しました。"
        } else {
            "${currentFilter.name}でカメラ操作を実行しました。"
        }

        return ParsedVoiceCommand(
            userTranscript = text,
            spokenReply = reply,
            actions = actions,
            filterId = filterId,
            frameStyle = frameStyle,
            flashMode = flashMode,
            exposure = exposure,
            vignette = vignette,
            filmGrain = filmGrain
        )
    }

    /**
     * Interprets either a spoken text transcript OR raw recorded WAV audio (along with the live
     * camera viewfinder frame when visual context is requested) using Firebase AI Logic.
     * Uses a 30s timeout on primary `gemini-3.8-flash` and falls back to `gemini-3.5-flash-lite`
     * with a 30s timeout if the primary model times out.
     */
    suspend fun interpretVoiceCommand(
        spokenText: String?,
        recordedWavBytes: ByteArray?,
        currentFilter: OpticalFilter,
        viewfinderSnapshot: Bitmap?
    ): ParsedVoiceCommand {
        val needsSceneImage = recordedWavBytes != null ||
            spokenText.isNullOrBlank() ||
            spokenText.contains("シーン") ||
            spokenText.contains("おすすめ") ||
            spokenText.contains("合う") ||
            spokenText.contains("どんな") ||
            spokenText.lowercase(Locale.ROOT).let { it.contains("scene") || it.contains("recommend") || it.contains("look") }

        val scaledThumbnail = if (needsSceneImage) {
            viewfinderSnapshot?.let { bmp ->
                val targetW = 192
                val targetH = (bmp.height * (192f / bmp.width.coerceAtLeast(1))).toInt().coerceIn(120, 256)
                Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            }
        } else {
            null
        }

        val prompt = """
            LuminaLens Voice Camera OS. Active LUT: ${currentFilter.id} (${currentFilter.name}).
            Filter IDs: kodak_gold_400, cine_teal_orange, cyber_2077, fuji_velvia_50, leica_noir, tokyo_rain_800T, solaris_flare, polaroid_sx70, bleach_bypass, aerochrome_ir, sepia_platinum, natural_zero.
            Frame styles: POLAROID, CINEMA_SCOPE, GALLERY_MATTE, NONE.
            Actions: CAPTURE_PHOTO, SELECT_FILTER, NEXT_FILTER, PREV_FILTER, SWITCH_LENS, SET_FLASH, SET_FRAME, ADJUST_GRADE, TOGGLE_SPLIT_COMPARE, TOGGLE_GRID, TOGGLE_DATE_STAMP, OPEN_GALLERY, SHARE_LATEST, SAVE_LATEST, RESET_GRADE.
            Reply briefly in natural Japanese.
        """.trimIndent()

        val requestContent = content {
            text(prompt)
            if (scaledThumbnail != null) {
                image(scaledThumbnail)
            }
            if (recordedWavBytes != null && recordedWavBytes.isNotEmpty()) {
                inlineData(recordedWavBytes, "audio/wav")
                text("Transcribe the user's Japanese/English voice command from the audio and map it to camera actions.")
            }
            if (!spokenText.isNullOrBlank()) {
                text("Command: \"$spokenText\"")
            }
        }

        val response = try {
            withTimeout(30_000L) {
                primaryModel.generateContent(requestContent)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "gemini-3.8-flash timed out after 30s, retrying with gemini-3.5-flash-lite", e)
            withTimeout(30_000L) {
                fastLiteModel.generateContent(requestContent)
            }
        }

        val jsonText = response.text ?: throw IllegalStateException("Empty response from Gemini model.")
        return parseJsonCommand(jsonText, fallbackTranscript = spokenText.orEmpty())
    }

    fun parseJsonCommand(jsonString: String, fallbackTranscript: String = ""): ParsedVoiceCommand {
        val json = JSONObject(jsonString)
        val transcript = json.optString("userTranscript", fallbackTranscript).ifBlank {
            fallbackTranscript.ifBlank { "音声コマンド" }
        }
        val reply = json.optString("spokenReply", "カメラ設定を更新しました。")

        val actionsList = mutableListOf<CameraVoiceAction>()

        // Support both `actions` JSON array and comma-separated `action` string
        val actionsArray = json.optJSONArray("actions")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val rawAction = actionsArray.optString(i).trim()
                CameraVoiceAction.entries.firstOrNull { it.name == rawAction }?.let {
                    actionsList.add(it)
                }
            }
        }

        val actionStr = json.optString("action", "")
        if (actionStr.isNotBlank()) {
            if (actionStr.contains("SELECT_AND_CAPTURE")) {
                actionsList.add(CameraVoiceAction.SELECT_FILTER)
                actionsList.add(CameraVoiceAction.CAPTURE_PHOTO)
            } else {
                actionStr.split(",", " ").map { it.trim() }.forEach { token ->
                    CameraVoiceAction.entries.firstOrNull { it.name == token }?.let {
                        if (!actionsList.contains(it)) actionsList.add(it)
                    }
                }
            }
        }

        fun optFloatOrNull(key: String): Float? =
            if (json.has(key) && !json.isNull(key)) json.optDouble(key).toFloat() else null

        val targetId = json.optString("targetId", "").trim()
        val explicitFilterId = json.optString("filterId", "").takeIf { it.isNotBlank() }
            ?: targetId.takeIf { id ->
                id in setOf(
                    "natural_zero", "kodak_gold_400", "cine_teal_orange", "cyber_2077",
                    "fuji_velvia_50", "leica_noir", "tokyo_rain_800T", "solaris_flare",
                    "polaroid_sx70", "bleach_bypass", "aerochrome_ir", "sepia_platinum"
                )
            }

        val explicitFrameStyle = json.optString("frameStyle", "").takeIf { it.isNotBlank() }
            ?: targetId.takeIf { it in setOf("NONE", "POLAROID", "CINEMA_SCOPE", "GALLERY_MATTE") }

        val explicitFlashMode = json.optString("flashMode", "").takeIf { it.isNotBlank() }
            ?: targetId.takeIf { it in setOf("OFF", "AUTO", "ON", "TORCH") }

        val gradePreset = json.optString("gradePreset", "")
        val presetExposure = when (gradePreset) {
            "BRIGHTER" -> 0.25f
            "DARKER" -> -0.25f
            else -> optFloatOrNull("exposure")
        }
        val presetVignette = when (gradePreset) {
            "MORE_VIGNETTE" -> 0.65f
            else -> optFloatOrNull("vignette")
        }
        val presetGrain = when (gradePreset) {
            "MORE_GRAIN" -> 0.55f
            else -> optFloatOrNull("filmGrain")
        }
        val presetWarmth = when (gradePreset) {
            "WARMER" -> 0.35f
            "COOLER" -> -0.35f
            else -> optFloatOrNull("warmth")
        }

        return ParsedVoiceCommand(
            userTranscript = transcript,
            spokenReply = reply,
            actions = if (actionsList.isEmpty()) listOf(CameraVoiceAction.NONE) else actionsList,
            filterId = explicitFilterId,
            frameStyle = explicitFrameStyle,
            flashMode = explicitFlashMode,
            aspectRatio = json.optString("aspectRatio", "").takeIf { it.isNotBlank() },
            intensity = optFloatOrNull("intensity"),
            exposure = presetExposure,
            contrast = optFloatOrNull("contrast"),
            warmth = presetWarmth,
            vignette = presetVignette,
            filmGrain = presetGrain,
            lightLeak = optFloatOrNull("lightLeak")
        )
    }
}

/**
 * Records 16kHz 16-bit mono PCM audio from the device microphone and encodes a standard 44-byte WAV header
 * so raw voice commands can be sent directly to Gemini multimodal audio input (`audio/wav`).
 */
class WavAudioRecorder {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    suspend fun recordClipUntilStopped(
        isStillRecording: () -> Boolean,
        onAmplitude: (Float) -> Unit
    ): ByteArray? = withContext(Dispatchers.IO) {
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(4096)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufSize * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            return@withContext null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }

        val pcmOutput = ByteArrayOutputStream()
        val buffer = ByteArray(2048)

        try {
            recorder.startRecording()
            val startTime = System.currentTimeMillis()
            // Record up to 5 seconds max so audio payload stays compact and fast
            while (coroutineContext.isActive && isStillRecording() && (System.currentTimeMillis() - startTime) < 5000L) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pcmOutput.write(buffer, 0, read)
                    var sum = 0.0
                    var i = 0
                    while (i + 1 < read) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        sum += sample * sample
                        i += 2
                    }
                    val rms = kotlin.math.sqrt(sum / (read / 2.0)).toFloat()
                    val normalized = (rms / 8000f).coerceIn(0.08f, 1.0f)
                    onAmplitude(normalized)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during microphone recording", e)
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }

        val pcmBytes = pcmOutput.toByteArray()
        if (pcmBytes.size < 1600) return@withContext null
        wrapPcmInWavHeader(pcmBytes, sampleRate, 1, 16)
    }

    private fun wrapPcmInWavHeader(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return header + pcmData
    }
}
