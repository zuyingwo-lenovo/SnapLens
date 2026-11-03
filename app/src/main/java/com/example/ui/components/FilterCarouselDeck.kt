package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FilterCategory
import com.example.model.FrameStyle
import com.example.model.OpticalFilter
import com.example.model.StudioScene
import com.example.model.ViewfinderMode
import com.example.ui.theme.DarkroomElevated
import com.example.ui.theme.DarkroomSurface
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.IvoryText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LeicaAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan

@Composable
fun FilterCarouselDeck(
    viewfinderMode: ViewfinderMode,
    activeStudioScene: StudioScene,
    selectedCategory: FilterCategory,
    visibleFilters: List<OpticalFilter>,
    selectedFilter: OpticalFilter,
    favoriteFilterIds: Set<String>,
    selectedFrameStyle: FrameStyle,
    thumbnailBitmap: Bitmap?,
    onSelectStudioScene: (StudioScene) -> Unit,
    onImportDevicePhoto: () -> Unit,
    onSelectCategory: (FilterCategory) -> Unit,
    onSelectFilter: (OpticalFilter) -> Unit,
    onToggleFavoriteFilter: (String) -> Unit,
    onSelectFrameStyle: (FrameStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Optional Studio Canvas Scene Selector Bar when in STUDIO_CANVAS mode
        if (viewfinderMode == ViewfinderMode.STUDIO_CANVAS) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("studio_scene_selector_row")
            ) {
                item {
                    Surface(
                        color = OpticalCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clickable { onImportDevicePhoto() }
                            .testTag("import_device_photo_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Import Photo",
                                tint = OpticalCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Import Photo",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = OpticalCyan
                            )
                        }
                    }
                }

                val presetScenes = listOf(
                    StudioScene.TOKYO_NEON,
                    StudioScene.AMALFI_SUNSET,
                    StudioScene.CYBER_PORTRAIT,
                    StudioScene.ALPINE_DUSK
                )
                items(presetScenes, key = { it.id }) { scene ->
                    val isSelected = activeStudioScene == scene
                    Surface(
                        color = if (isSelected) LeicaAmber.copy(alpha = 0.20f) else DarkroomElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) LeicaAmber else GunmetalBorder
                        ),
                        modifier = Modifier
                            .clickable { onSelectStudioScene(scene) }
                            .testTag("scene_chip_${scene.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = scene.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 11.sp,
                                color = if (isSelected) LeicaAmber else IvoryText
                            )
                        }
                    }
                }
            }
        }

        // Row 1: Category Filter Tabs + Frame Style Quick Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(FilterCategory.entries, key = { it.name }) { category ->
                    val selected = category == selectedCategory
                    Surface(
                        color = if (selected) LeicaAmber else DarkroomSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (selected) LeicaAmber else GunmetalBorder
                        ),
                        modifier = Modifier
                            .clickable { onSelectCategory(category) }
                            .testTag("category_tab_${category.name.lowercase()}")
                    ) {
                        Text(
                            text = category.label,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            color = if (selected) ObsidianBlack else MutedSilver,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Cycle Frame Style Pill
            Surface(
                color = DarkroomElevated,
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    1.dp,
                    if (selectedFrameStyle != FrameStyle.NONE) OpticalCyan else GunmetalBorder
                ),
                modifier = Modifier
                    .clickable {
                        val all = FrameStyle.entries
                        val next = all[(all.indexOf(selectedFrameStyle) + 1) % all.size]
                        onSelectFrameStyle(next)
                    }
                    .testTag("cycle_frame_style_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Frame Style",
                        tint = if (selectedFrameStyle != FrameStyle.NONE) OpticalCyan else MutedSilver,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedFrameStyle.badge,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (selectedFrameStyle != FrameStyle.NONE) OpticalCyan else IvoryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 2: Live-Filtered LUT Thumbnail Cards
        if (visibleFilters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No starred LUTs yet. Tap the star on any filter card to bookmark it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSilver
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filter_carousel_row")
            ) {
                items(visibleFilters, key = { it.id }) { filter ->
                    val isSelected = filter.id == selectedFilter.id
                    val isFavorite = filter.id in favoriteFilterIds
                    FilterThumbnailCard(
                        filter = filter,
                        isSelected = isSelected,
                        isFavorite = isFavorite,
                        thumbnailBitmap = thumbnailBitmap,
                        onSelect = { onSelectFilter(filter) },
                        onToggleFavorite = { onToggleFavoriteFilter(filter.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterThumbnailCard(
    filter: OpticalFilter,
    isSelected: Boolean,
    isFavorite: Boolean,
    thumbnailBitmap: Bitmap?,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val colorFilter = remember(filter.id) {
        ColorFilter.colorMatrix(ColorMatrix(filter.matrix))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable { onSelect() }
            .testTag("filter_card_${filter.id}")
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkroomElevated)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) LeicaAmber else GunmetalBorder,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            if (thumbnailBitmap != null) {
                val imgBmp = remember(thumbnailBitmap) { thumbnailBitmap.asImageBitmap() }
                Image(
                    bitmap = imgBmp,
                    contentDescription = filter.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom gradient scrim with shortCode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ObsidianBlack.copy(alpha = 0.85f)
                            ),
                            startY = 30f
                        )
                    )
            )

            // Favorite Star button in top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.55f))
                    .clickable { onToggleFavorite() }
                    .testTag("favorite_filter_${filter.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite Filter",
                    tint = if (isFavorite) LeicaAmber else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(12.dp)
                )
            }

            // Filter accent indicator dot
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(filter.accentColor)
            )

            Text(
                text = filter.shortCode,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = if (isSelected) LeicaAmber else Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) LeicaAmber else MutedSilver,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
