package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.CameraVoiceAction
import com.example.domain.FilterEngine
import com.example.domain.VoiceCameraAssistant
import com.example.model.FilterAdjustments
import com.example.model.FilterCatalog
import com.example.model.FrameStyle
import com.example.model.StudioScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LuminaLens", appName)
    }

    @Test
    fun `real time filter matrix and bitmap rendering pipeline produces framed image`() {
        val sceneBmp = FilterEngine.generateStudioSceneBitmap(StudioScene.TOKYO_NEON, 240, 320)
        assertNotNull(sceneBmp)
        assertEquals(240, sceneBmp.width)
        assertEquals(320, sceneBmp.height)

        val filter = FilterCatalog.findById("cine_teal_orange")
        val adjustments = FilterAdjustments(
            intensity = 0.85f,
            exposure = 0.1f,
            contrast = 0.2f,
            warmth = 0.15f,
            vignette = 0.35f,
            filmGrain = 0.2f
        )

        val matrix = FilterEngine.buildCombinedColorMatrix(filter, adjustments)
        assertEquals(20, matrix.size)

        val framedBmp = FilterEngine.renderFilteredBitmap(
            source = sceneBmp,
            filter = filter,
            adjustments = adjustments,
            frameStyle = FrameStyle.POLAROID,
            showDateStamp = true,
            caption = "Tokyo Night Test"
        )
        assertTrue(framedBmp.width > sceneBmp.width)
        assertTrue(framedBmp.height > sceneBmp.height)
    }

    @Test
    fun `voice camera assistant parses structured JSON command actions accurately`() {
        val json = """
            {
              "userTranscript": "サイバーパンク風にして写真を撮って",
              "spokenReply": "ネオン・ディストピア(CYBER 88)フィルターに切り替えて撮影しました。",
              "actions": ["SELECT_FILTER", "CAPTURE_PHOTO"],
              "filterId": "cyber_2077",
              "exposure": 0.15
            }
        """.trimIndent()

        val parsed = VoiceCameraAssistant.parseJsonCommand(json)
        assertEquals("サイバーパンク風にして写真を撮って", parsed.userTranscript)
        assertEquals("cyber_2077", parsed.filterId)
        assertEquals(
            listOf(CameraVoiceAction.SELECT_FILTER, CameraVoiceAction.CAPTURE_PHOTO),
            parsed.actions
        )
        assertEquals(0.15f, parsed.exposure ?: 0f, 0.001f)
    }
}
