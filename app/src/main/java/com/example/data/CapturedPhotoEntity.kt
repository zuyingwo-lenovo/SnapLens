package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.CapturedPhoto
import com.example.model.FrameStyle

@Entity(tableName = "captured_photos")
data class CapturedPhotoEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val filterId: String,
    val filterName: String,
    val filterCode: String,
    val isoTag: String,
    val frameStyleName: String,
    val caption: String,
    val timestampMillis: Long,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false
)

fun CapturedPhotoEntity.toDomain(): CapturedPhoto {
    val resolvedFrame = FrameStyle.entries.firstOrNull { it.name == frameStyleName } ?: FrameStyle.NONE
    return CapturedPhoto(
        id = id,
        filePath = filePath,
        filterId = filterId,
        filterName = filterName,
        filterCode = filterCode,
        isoTag = isoTag,
        frameStyle = resolvedFrame,
        caption = caption,
        timestampMillis = timestampMillis,
        width = width,
        height = height,
        isFavorite = isFavorite
    )
}

fun CapturedPhoto.toEntity(): CapturedPhotoEntity {
    return CapturedPhotoEntity(
        id = id,
        filePath = filePath,
        filterId = filterId,
        filterName = filterName,
        filterCode = filterCode,
        isoTag = isoTag,
        frameStyleName = frameStyle.name,
        caption = caption,
        timestampMillis = timestampMillis,
        width = width,
        height = height,
        isFavorite = isFavorite
    )
}
