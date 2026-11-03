package com.example.data

import com.example.model.CapturedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PhotoGalleryRepository(
    private val capturedPhotoDao: CapturedPhotoDao
) {
    val allPhotosFlow: Flow<List<CapturedPhoto>> = capturedPhotoDao
        .observeAllPhotos()
        .map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getPhotoCount(): Int = capturedPhotoDao.getPhotoCount()

    suspend fun savePhoto(photo: CapturedPhoto) {
        capturedPhotoDao.upsertPhoto(photo.toEntity())
    }

    suspend fun replacePhoto(oldPhotoId: String, oldFilePath: String, newPhoto: CapturedPhoto) {
        if (oldPhotoId != newPhoto.id) {
            capturedPhotoDao.deletePhotoById(oldPhotoId)
            withContext(Dispatchers.IO) {
                if (oldFilePath != newPhoto.filePath) {
                    runCatching { File(oldFilePath).delete() }
                }
            }
        }
        capturedPhotoDao.upsertPhoto(newPhoto.toEntity())
    }

    suspend fun toggleFavorite(photoId: String) {
        val current = capturedPhotoDao.getPhotoById(photoId) ?: return
        capturedPhotoDao.updateFavorite(photoId, !current.isFavorite)
    }

    suspend fun deletePhoto(photoId: String) {
        val existing = capturedPhotoDao.getPhotoById(photoId)
        capturedPhotoDao.deletePhotoById(photoId)
        if (existing != null) {
            withContext(Dispatchers.IO) {
                runCatching { File(existing.filePath).delete() }
            }
        }
    }
}
