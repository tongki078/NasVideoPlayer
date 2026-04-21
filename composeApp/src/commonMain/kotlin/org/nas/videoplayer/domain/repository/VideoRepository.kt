package org.nas.videoplayer.domain.repository

import org.nas.videoplayer.domain.model.*

interface VideoRepository {
    suspend fun getCategoryList(path: String): List<Category>
    suspend fun getCategorySections(category: String, keyword: String? = null): List<HomeSection>
    suspend fun getSeriesDetail(path: String): Category?
    suspend fun searchVideos(query: String, category: String = "전체"): List<Series>
    suspend fun getLatestMovies(): List<Series>
    suspend fun getAnimations(): List<Series>
    suspend fun getDramas(): List<Series>
    suspend fun getAnimationsAll(): List<Series>
    suspend fun getHomeSections(): List<HomeSection>
    suspend fun updateProgress(episodeId: String, position: Float, duration: Float): Boolean
    suspend fun getSubtitleInfo(path: String, type: String): SubtitleInfo?
}
