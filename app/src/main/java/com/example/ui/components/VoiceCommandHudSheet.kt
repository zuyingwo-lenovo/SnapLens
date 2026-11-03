package com.example.ui.components

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.VoiceAssistantUiState
import com.example.domain.VoiceTurnEntry
import com.example.domain.WavAudioRecorder
import com.example.ui.theme.DarkroomElevated
import com.example.ui.theme.DarkroomSurface
import com.example.ui.theme.EmeraldFocal
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.HalideOrange
import com.example.ui.theme.IvoryText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LeicaAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

private val QUICK_VOICE_PRESETS = listOf(
    "写真を撮って" to "シャッター撮影",
    "このシーンに合うフィルターにして" to "AIシーン最適化",
    "サイバーパンク風にして撮影して" to "CYBER 88 + 撮影",
    "映画風のフィルターにして" to "CINE T&O 適用",
    "モノクロ＋ポラロイド枠にして" to "B&W + INSTAX枠",
    "明るくしてビネットを強くして" to "露出+EV・ビネット",
    "ビフォーアフター比較を表示" to "分割比較ルーペ",
    "カメラを切り替えて" to "レンズ切替",
    "ギャラリーを開いて" to "Darkroom表示",
    "最新の写真をシェアして" to "ワンタップ共有"
)

@Composable
fun VoiceCommandDrawer(
    voiceState: VoiceAssistantUiState,
    voiceHistory: List<VoiceTurnEntry>,
    hasAudioPermission: Boolean,
    isTtsEnabled: Boolean,
    lastSpokenReply: String?,
    onRequestAudioPermission: () -> Unit,
    onToggleTts: () -> Unit,
    onUpdateListeningState: (Boolean, String, Float) -> Unit,
    onSubmitTextVoiceCommand: (String) -> Unit,
    onSubmitRecordedAudioCommand: (ByteArray) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var customCommandInput by remember { mutableStateOf("") }
    var isRecordingRawAudio by remember { mutableStateOf(false) }
    var liveMicAmplitude by remember { mutableFloatStateOf(0.2f) }
    val wavRecorder = remember { WavAudioRecorder() }

    // Text-To-Speech engine for speaking AI responses aloud
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.JAPANESE
            }
        }
        ttsEngine = engine
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
        }
    }

    // Speak new AI reply when TTS is enabled
    LaunchedEffect(lastSpokenReply, isTtsEnabled) {
        if (isTtsEnabled && !lastSpokenReply.isNullOrBlank()) {
            val isJapanese = lastSpokenReply.any { it.code in 0x3040..0x9FAF }
            ttsEngine?.language = if (isJapanese) Locale.JAPANESE else Locale.US
            ttsEngine?.speak(
                lastSpokenReply,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "lumina_voice_reply_${System.currentTimeMillis()}"
            )
        }
    }

    // Android SpeechRecognizer setup with fallback to direct WAV recording for Gemini multimodal audio
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(context) {
        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
        speechRecognizer = recognizer
        onDispose {
            runCatching { recognizer?.destroy() }
        }
    }

    fun startRawWavCaptureFallback() {
        if (isRecordingRawAudio) {
            isRecordingRawAudio = false
            return
        }
        isRecordingRawAudio = true
        onUpdateListeningState(true, "マイク音声を録音中 (もう一度タップで送信)...", 0.35f)
        coroutineScope.launch {
            val wavBytes = wavRecorder.recordClipUntilStopped(
                isStillRecording = { isRecordingRawAudio },
                onAmplitude = { amp ->
                    liveMicAmplitude = amp
                    onUpdateListeningState(true, "マイク音声を録音中 (タップで送信)...", amp)
                }
            )
            isRecordingRawAudio = false
            if (wavBytes != null && wavBytes.isNotEmpty()) {
                onSubmitRecordedAudioCommand(wavBytes)
            } else {
                onUpdateListeningState(false, "", 0f)
            }
        }
    }

    fun toggleMicrophoneListening() {
        if (!hasAudioPermission) {
            onRequestAudioPermission()
            return
        }

        if (isRecordingRawAudio) {
            // Stop recording and send WAV to Gemini
            isRecordingRawAudio = false
            return
        }

        if (voiceState is VoiceAssistantUiState.Listening) {
            runCatching { speechRecognizer?.stopListening() }
            onUpdateListeningState(false, "", 0f)
            return
        }

        val recognizer = speechRecognizer
        if (recognizer != null) {
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onUpdateListeningState(true, "話してください (例: 「写真を撮って」「映画風にして」)...", 0.3f)
                }

                override fun onBeginningOfSpeech() {
                    onUpdateListeningState(true, "聞き取り中...", 0.5f)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val norm = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                    liveMicAmplitude = norm
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // Fallback to direct WAV recording if SpeechRecognizer service is unavailable on emulator
                    startRawWavCaptureFallback()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestText = matches?.firstOrNull().orEmpty()
                    if (bestText.isNotBlank()) {
                        onSubmitTextVoiceCommand(bestText)
                    } else {
                        onUpdateListeningState(false, "", 0f)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        onUpdateListeningState(true, partial, liveMicAmplitude)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                startRawWavCaptureFallback()
            }
        } else {
            startRawWavCaptureFallback()
        }
    }

    Surface(
        color = DarkroomSurface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("voice_command_drawer")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header Row: Title, TTS Speaker Toggle, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = OpticalCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "GEMINI LIVE VOICE CAMERA OS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = OpticalCyan
                        )
                        Text(
                            text = "音声でシャッター・LUT変更・露出・共有を操作",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = MutedSilver
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Toggle Voice Response TTS
                    IconButton(
                        onClick = onToggleTts,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("voice_tts_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isTtsEnabled) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = "Toggle Voice Reply Audio",
                            tint = if (isTtsEnabled) LeicaAmber else MutedSilver,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("close_voice_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Voice Panel",
                            tint = IvoryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Microphone Button + Live Waveform / Status Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkroomElevated)
                    .border(
                        1.dp,
                        when (voiceState) {
                            is VoiceAssistantUiState.Listening -> HalideOrange
                            is VoiceAssistantUiState.Processing -> OpticalCyan
                            is VoiceAssistantUiState.Error -> Color(0xFFEF4444)
                            else -> GunmetalBorder
                        },
                        RoundedCornerShape(14.dp)
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Big Pulse Microphone Button
                val isListening = voiceState is VoiceAssistantUiState.Listening || isRecordingRawAudio
                Surface(
                    color = when {
                        isListening -> HalideOrange
                        !hasAudioPermission -> DarkroomSurface
                        else -> OpticalCyan
                    },
                    shape = CircleShape,
                    border = BorderStroke(
                        1.5.dp,
                        if (isListening) Color.White else OpticalCyan
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { toggleMicrophoneListening() }
                        .testTag("voice_mic_record_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isListening -> Icons.Default.Stop
                                !hasAudioPermission -> Icons.Default.MicOff
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Start or Stop Voice Input",
                            tint = if (isListening) Color.White else ObsidianBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Status / Live Transcript / AI Spoken Response
                Column(modifier = Modifier.weight(1f)) {
                    when (voiceState) {
                        is VoiceAssistantUiState.Idle -> {
                            Text(
                                text = if (hasAudioPermission) {
                                    "マイクをタップして話すか、下の音声コマンドを選択"
                                } else {
                                    "マイク権限を許可して音声コントロールを開始"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = IvoryText
                            )
                            Text(
                                text = "例: 「写真を撮って」「サイバーパンクにして」「このシーンに合うフィルターにして」",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = MutedSilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is VoiceAssistantUiState.Listening -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LiveVoiceWaveformBar(amplitude = liveMicAmplitude)
                                Text(
                                    text = voiceState.partialText.ifBlank { "聞き取り中..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LeicaAmber,
                                    maxLines = 2
                                )
                            }
                        }

                        is VoiceAssistantUiState.Processing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = OpticalCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = voiceState.statusLabel,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = OpticalCyan
                                )
                            }
                        }

                        is VoiceAssistantUiState.Success -> {
                            Text(
                                text = "「${voiceState.result.userTranscript}」",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = LeicaAmber
                            )
                            Text(
                                text = voiceState.result.spokenReply,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = IvoryText,
                                modifier = Modifier.testTag("voice_assistant_reply_text")
                            )
                        }

                        is VoiceAssistantUiState.Error -> {
                            Text(
                                text = "音声AIエラー: ${voiceState.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF87171),
                                modifier = Modifier.testTag("voice_assistant_error_text")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Voice Command Chips (1-Tap Voice Simulation & Hands-Free Presets)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_voice_presets_row")
            ) {
                items(QUICK_VOICE_PRESETS, key = { it.first }) { (phrase, badge) ->
                    Surface(
                        color = DarkroomElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GunmetalBorder),
                        modifier = Modifier
                            .clickable { onSubmitTextVoiceCommand(phrase) }
                            .testTag("voice_preset_chip_$badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = LeicaAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column {
                                Text(
                                    text = "「$phrase」",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = IvoryText
                                )
                                Text(
                                    text = badge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = OpticalCyan
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Direct Voice / Text Command Input Bar
            OutlinedTextField(
                value = customCommandInput,
                onValueChange = { customCommandInput = it },
                placeholder = {
                    Text(
                        "音声コマンドを入力またはマイクで発話 (例: 映画風にして写真を撮って)",
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (customCommandInput.isNotBlank()) {
                                val cmd = customCommandInput.trim()
                                customCommandInput = ""
                                onSubmitTextVoiceCommand(cmd)
                            }
                        },
                        enabled = customCommandInput.isNotBlank(),
                        modifier = Modifier.testTag("send_voice_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Voice Command",
                            tint = if (customCommandInput.isNotBlank()) OpticalCyan else MutedSilver
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpticalCyan,
                    unfocusedBorderColor = GunmetalBorder,
                    focusedTextColor = IvoryText,
                    unfocusedTextColor = IvoryText
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_command_text_input")
            )

            // Recent Voice Conversation & Executed Camera Actions Log
            AnimatedVisibility(
                visible = voiceHistory.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(voiceHistory.take(3), key = { it.id }) { turn ->
                        Surface(
                            color = ObsidianBlack.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GunmetalBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🎙 ${turn.userText}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = LeicaAmber,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        turn.executedBadges.forEach { badge ->
                                            Surface(
                                                color = EmeraldFocal.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = badge,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    color = EmeraldFocal,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = turn.assistantReply,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.sp,
                                    color = IvoryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceWaveformBar(amplitude: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = Modifier
            .width(46.dp)
            .height(22.dp)
    ) {
        val barCount = 6
        val barWidth = 4.dp.toPx()
        val spacing = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val wave = ((sin(phase + i * 0.9f) + 1f) / 2f)
            val hFactor = (0.25f + wave * amplitude.coerceIn(0.25f, 1.0f)).coerceIn(0.2f, 1.0f)
            val barH = size.height * hFactor
            val x = i * (barWidth + spacing)
            val y = (size.height - barH) / 2f
            drawRoundRect(
                color = if (i % 2 == 0) OpticalCyan else LeicaAmber,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
