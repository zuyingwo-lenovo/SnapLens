package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CapturedPhoto
import com.example.model.FrameStyle
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QuickShareStudioDialog(
    photo: CapturedPhoto,
    onDismiss: () -> Unit,
    onShareNow: (CapturedPhoto, String) -> Unit,
    onSaveToDevice: (CapturedPhoto) -> Unit,
    onUpdateFrameAndCaption: (CapturedPhoto, FrameStyle, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    var captionInput by remember(photo.id) { mutableStateOf(photo.caption) }
    val decodedBitmap = remember(photo.id, photo.filePath) {
        val file = File(photo.filePath)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianBlack.copy(alpha = 0.96f),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("quick_share_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INSTANT DARKROOM EXPORT",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = LeicaAmber
                        )
                        Text(
                            text = "${photo.filterName} • ${photo.filterCode}",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryText
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleFavorite(photo.id) },
                            modifier = Modifier.testTag("modal_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Toggle Favorite",
                                tint = if (photo.isFavorite) HalideOrange else MutedSilver
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_share_modal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = IvoryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rendered High-Res Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkroomSurface)
                        .border(1.dp, GunmetalBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Captured Filtered Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        Text(
                            text = "Rendering optical output...",
                            color = MutedSilver,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }

                    // Bottom Metadata Pill
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${photo.width}×${photo.height} • ${photo.isoTag}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = OpticalCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Room SQLite Stored File Path Info Banner
                Surface(
                    color = DarkroomSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GunmetalBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_file_path_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = EmeraldFocal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ROOM SQLITE RECORD • FILE PATH",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = EmeraldFocal
                            )
                            Text(
                                text = photo.filePath,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = MutedSilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("modal_stored_file_path_text")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Frame Style Selector Row
                Text(
                    text = "SOCIAL EXPORT FRAME",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MutedSilver
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FrameStyle.entries, key = { it.name }) { style ->
                        val selected = style == photo.frameStyle
                        Surface(
                            color = if (selected) LeicaAmber else DarkroomElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) LeicaAmber else GunmetalBorder
                            ),
                            modifier = Modifier
                                .clickable {
                                    onUpdateFrameAndCaption(photo, style, captionInput)
                                }
                                .testTag("share_frame_option_${style.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = style.badge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (selected) ObsidianBlack else OpticalCyan
                                )
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selected) ObsidianBlack else IvoryText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Caption & Stamp Input
                OutlinedTextField(
                    value = captionInput,
                    onValueChange = { captionInput = it },
                    label = {
                        Text(
                            "Photo Caption / Frame Stamp",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp
                        )
                    },
                    placeholder = {
                        Text("Add a note or location stamp before sharing...")
                    },
                    singleLine = true,
                    trailingIcon = {
                        if (captionInput != photo.caption) {
                            Surface(
                                color = OpticalCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable {
                                        onUpdateFrameAndCaption(photo, photo.frameStyle, captionInput)
                                    }
                                    .testTag("apply_caption_stamp_button")
                            ) {
                                Text(
                                    text = "STAMP",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = OpticalCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LeicaAmber,
                        unfocusedBorderColor = GunmetalBorder,
                        focusedLabelColor = LeicaAmber,
                        unfocusedLabelColor = MutedSilver,
                        focusedTextColor = IvoryText,
                        unfocusedTextColor = IvoryText
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_caption_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action: One-Tap Share Photo via Android Share Sheet
                Button(
                    onClick = { onShareNow(photo, captionInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LeicaAmber,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("modal_share_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Share Filtered Photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Row: Save to Device Gallery + Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveToDevice(photo) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OpticalCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("modal_save_device_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Device", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onDeletePhoto(photo.id) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GunmetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedSilver),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("modal_delete_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Photo",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DarkroomGalleryScreen(
    photos: List<CapturedPhoto>,
    onBack: () -> Unit,
    onSelectPhoto: (CapturedPhoto) -> Unit,
    onSharePhoto: (CapturedPhoto) -> Unit,
    onSavePhoto: (CapturedPhoto) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var showFavoritesOnly by remember { mutableStateOf(false) }
    val filteredList = remember(photos, showFavoritesOnly) {
        if (showFavoritesOnly) photos.filter { it.isFavorite } else photos
    }

    Surface(
        color = ObsidianBlack,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("darkroom_gallery_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("gallery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Viewfinder",
                            tint = IvoryText
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = EmeraldFocal,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "LOCAL ROOM DB GALLERY",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = EmeraldFocal
                            )
                        }
                        Text(
                            text = "${photos.size} Stored Captures",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryText
                        )
                    }
                }

                // Filter Toggle: All vs Starred
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (!showFavoritesOnly) LeicaAmber else DarkroomElevated,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clickable { showFavoritesOnly = false }
                            .testTag("gallery_filter_all")
                    ) {
                        Text(
                            text = "ALL (${photos.size})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (!showFavoritesOnly) ObsidianBlack else IvoryText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = if (showFavoritesOnly) LeicaAmber else DarkroomElevated,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clickable { showFavoritesOnly = true }
                            .testTag("gallery_filter_favorites")
                    ) {
                        Text(
                            text = "STARRED (${photos.count { it.isFavorite }})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (showFavoritesOnly) ObsidianBlack else IvoryText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MutedSilver,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (showFavoritesOnly) "No Starred Captures Yet" else "Local Photo Gallery is Empty",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Capture photos in the live viewfinder to store their file paths in Room and view them here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSilver
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 6.dp,
                        bottom = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("gallery_photo_grid")
                ) {
                    items(filteredList, key = { it.id }) { photo ->
                        DarkroomPhotoCard(
                            photo = photo,
                            onClick = { onSelectPhoto(photo) },
                            onShare = { onSharePhoto(photo) },
                            onSave = { onSavePhoto(photo) },
                            onToggleFavorite = { onToggleFavorite(photo.id) },
                            onDelete = { onDeletePhoto(photo.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkroomPhotoCard(
    photo: CapturedPhoto,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val fileName = remember(photo.filePath) { File(photo.filePath).name }
    val bmp = remember(photo.id, photo.filePath) {
        val f = File(photo.filePath)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    Surface(
        color = DarkroomSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GunmetalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("gallery_photo_card_${photo.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .background(DarkroomElevated)
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = photo.filterName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Filter Code Badge & Favorite button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = photo.filterCode,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = LeicaAmber,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ObsidianBlack.copy(alpha = 0.65f))
                            .clickable { onToggleFavorite() }
                            .testTag("favorite_card_${photo.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (photo.isFavorite) HalideOrange else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bottom gradient scrim with title + stored file name
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, ObsidianBlack.copy(alpha = 0.90f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (photo.caption.isNotBlank()) photo.caption else photo.filterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 13.sp,
                        color = IvoryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fileName,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = EmeraldFocal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Card Footer with 1-Tap Delete, Save & Share Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm • MMM dd", Locale.US).format(Date(photo.timestampMillis)),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = MutedSilver
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = DarkroomElevated,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { onDelete() }
                            .testTag("delete_card_${photo.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Photo",
                                tint = MutedSilver,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        color = DarkroomElevated,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { onSave() }
                            .testTag("save_card_${photo.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save to Device",
                                tint = OpticalCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        color = LeicaAmber,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { onShare() }
                            .testTag("share_card_${photo.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Photo",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
