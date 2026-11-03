package com.example

import com.example.data.CapturedPhotoDao
import com.example.data.CapturedPhotoEntity
import com.example.data.PhotoGalleryRepository
import com.example.data.toDomain
import com.example.data.toEntity
import com.example.model.AdjustmentParameter
import com.example.model.CapturedPhoto
import com.example.model.FilterCatalog
import com.example.model.FilterCategory
import com.example.model.FrameStyle
import com.example.ui.CameraViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `filter catalog has 12 distinct optical filters with 20-element matrices`() {
        assertEquals(12, FilterCatalog.filters.size)
        FilterCatalog.filters.forEach { filter ->
            assertEquals(20, filter.matrix.size)
        }
    }

    @Test
    fun `room entity mapping preserves file path and metadata`() {
        val original = CapturedPhoto(
            id = "photo_101",
            filePath = "/data/user/0/com.aistudio.luminalens.vqxrtk/files/captured_photos/lumina_photo_101.jpg",
            filterId = "kodak_gold_400",
            filterName = "Portra Gold 400",
            filterCode = "GOLD 400",
            isoTag = "ISO 400 • 35MM",
            frameStyle = FrameStyle.POLAROID,
            caption = "Sunset Test",
            timestampMillis = 1730000000000L,
            width = 1080,
            height = 1440,
            isFavorite = true
        )

        val entity = original.toEntity()
        assertEquals(original.filePath, entity.filePath)
        assertEquals("POLAROID", entity.frameStyleName)

        val restored = entity.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun `photo gallery repository stores and observes captured photo file paths`() = runTest {
        val fakeDao = object : CapturedPhotoDao {
            private val items = MutableStateFlow<List<CapturedPhotoEntity>>(emptyList())
            override fun observeAllPhotos(): Flow<List<CapturedPhotoEntity>> = items
            override suspend fun getPhotoCount(): Int = items.value.size
            override suspend fun getPhotoById(id: String): CapturedPhotoEntity? =
                items.value.firstOrNull { it.id == id }
            override suspend fun upsertPhoto(photo: CapturedPhotoEntity) {
                items.value = listOf(photo) + items.value.filterNot { it.id == photo.id }
            }
            override suspend fun updateFavorite(id: String, isFavorite: Boolean) {
                items.value = items.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
            }
            override suspend fun deletePhotoById(id: String) {
                items.value = items.value.filterNot { it.id == id }
            }
        }

        val repo = PhotoGalleryRepository(fakeDao)
        val photo = CapturedPhoto(
            id = "p1",
            filePath = "/files/captured_photos/lumina_p1.jpg",
            filterId = "cyber_2077",
            filterName = "Neon Dystopia",
            filterCode = "CYBER 88",
            isoTag = "ISO 1600",
            frameStyle = FrameStyle.CINEMA_SCOPE,
            caption = "Night Street",
            timestampMillis = 1000L,
            width = 900,
            height = 1200,
            isFavorite = false
        )

        repo.savePhoto(photo)
        assertEquals(1, repo.getPhotoCount())
        val list = repo.allPhotosFlow.first()
        assertEquals("/files/captured_photos/lumina_p1.jpg", list.first().filePath)

        repo.toggleFavorite("p1")
        assertTrue(repo.allPhotosFlow.first().first().isFavorite)

        repo.deletePhoto("p1")
        assertEquals(0, repo.getPhotoCount())
    }

    @Test
    fun `camera viewModel updates selected filter and adjustments`() {
        val viewModel = CameraViewModel()
        val cyberFilter = FilterCatalog.findById("cyber_2077")
        viewModel.selectFilter(cyberFilter)
        assertEquals("cyber_2077", viewModel.uiState.value.selectedFilter.id)

        viewModel.updateAdjustmentValue(AdjustmentParameter.INTENSITY, 0.65f)
        assertEquals(0.65f, viewModel.uiState.value.adjustments.intensity, 0.001f)

        viewModel.selectCategory(FilterCategory.CINEMA)
        assertTrue(viewModel.uiState.value.visibleFilters.all { it.category == FilterCategory.CINEMA })

        viewModel.toggleSplitCompare()
        assertTrue(viewModel.uiState.value.isSplitCompareEnabled)
        viewModel.toggleSplitCompare()
        assertFalse(viewModel.uiState.value.isSplitCompareEnabled)
    }
}
