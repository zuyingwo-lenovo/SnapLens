package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdjustmentParameter
import com.example.model.FilterAdjustments
import com.example.ui.theme.DarkroomElevated
import com.example.ui.theme.DarkroomSurface
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.IvoryText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LeicaAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import kotlin.math.roundToInt

@Composable
fun OpticalFineTuneDrawer(
    adjustments: FilterAdjustments,
    selectedParam: AdjustmentParameter,
    onSelectParam: (AdjustmentParameter) -> Unit,
    onValueChange: (AdjustmentParameter, Float) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVal = adjustments.getValue(selectedParam)
    val formattedVal = when (selectedParam) {
        AdjustmentParameter.INTENSITY,
        AdjustmentParameter.VIGNETTE,
        AdjustmentParameter.GRAIN,
        AdjustmentParameter.LIGHT_LEAK -> "${(currentVal * 100).roundToInt()}%"
        else -> {
            val signed = (currentVal * 100).roundToInt()
            if (signed > 0) "+$signed" else "$signed"
        }
    }

    Surface(
        color = DarkroomSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GunmetalBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("optical_fine_tune_drawer")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedParam.label.uppercase(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = LeicaAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = DarkroomElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = formattedVal,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = OpticalCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = DarkroomElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GunmetalBorder),
                    modifier = Modifier
                        .clickable { onResetAll() }
                        .testTag("reset_adjustments_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Grade",
                            tint = MutedSilver,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESET",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = MutedSilver
                        )
                    }
                }
            }

            Slider(
                value = currentVal,
                onValueChange = { onValueChange(selectedParam, it) },
                valueRange = selectedParam.minVal..selectedParam.maxVal,
                colors = SliderDefaults.colors(
                    thumbColor = LeicaAmber,
                    activeTrackColor = LeicaAmber,
                    inactiveTrackColor = GunmetalBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .testTag("adjustment_slider")
            )

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(AdjustmentParameter.entries, key = { it.name }) { param ->
                    val isSelected = param == selectedParam
                    val paramVal = adjustments.getValue(param)
                    val isModified = kotlin.math.abs(paramVal - param.defaultVal) > 0.03f
                    Surface(
                        color = if (isSelected) LeicaAmber else DarkroomElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSelected -> LeicaAmber
                                isModified -> OpticalCyan.copy(alpha = 0.6f)
                                else -> GunmetalBorder
                            }
                        ),
                        modifier = Modifier
                            .clickable { onSelectParam(param) }
                            .testTag("param_chip_${param.shortTag.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = param.shortTag,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isSelected) ObsidianBlack else IvoryText
                            )
                        }
                    }
                }
            }
        }
    }
}
