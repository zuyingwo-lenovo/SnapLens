package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedPhotoDao {

    @Query("SELECT * FROM captured_photos ORDER BY timestampMillis DESC")
    fun observeAllPhotos(): Flow<List<CapturedPhotoEntity>>

    @Query("SELECT COUNT(*) FROM captured_photos")
    suspend fun getPhotoCount(): Int

    @Query("SELECT * FROM captured_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): CapturedPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: CapturedPhotoEntity)

    @Query("UPDATE captured_photos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM captured_photos WHERE id = :id")
    suspend fun deletePhotoById(id: String)
}
