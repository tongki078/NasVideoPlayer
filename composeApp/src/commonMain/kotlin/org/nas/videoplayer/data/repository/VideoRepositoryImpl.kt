package org.nas.videoplayer.data.repository

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.nas.videoplayer.data.network.NasApiClient
import org.nas.videoplayer.domain.model.*
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.cleanTitle
import org.nas.videoplayer.extractSeason
import org.nas.videoplayer.extractEpisode

class VideoRepositoryImpl : VideoRepository {
    private val client = NasApiClient.client
    private val baseUrl = NasApiClient.BASE_URL.removeSuffix("/")

    private fun fixCategoryPaths(categories: List<Category>): List<Category> {
        return categories.map { category ->
            category.copy(
                movies = category.movies.map { movie ->
                    movie.copy(
                        videoUrl = if (movie.videoUrl.startsWith("/")) "$baseUrl${movie.videoUrl}" else movie.videoUrl,
                        thumbnailUrl = if (movie.thumbnailUrl?.startsWith("/") == true) "$baseUrl${movie.thumbnailUrl}" else movie.thumbnailUrl
                    )
                }
            )
        }
    }

    override suspend fun getCategoryList(path: String): List<Category> {
        return try {
            val response = client.get("$baseUrl/list") { parameter("path", path) }
            val data = response.body<List<Category>>()
            fixCategoryPaths(data)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getCategorySections(category: String, keyword: String?): List<HomeSection> {
        return try {
            val response = client.get("$baseUrl/category_sections") {
                parameter("cat", category)
                if (keyword != null && keyword != "전체") {
                    parameter("kw", keyword)
                }
            }

            val results = if (response.status == HttpStatusCode.OK) {
                response.body<List<HomeSection>>()
            } else {
                emptyList()
            }

            results.map { section ->
                section.copy(items = fixCategoryPaths(section.items))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getSeriesDetail(path: String): Category? {
        return try {
            val response = client.get("$baseUrl/api/series_detail") { parameter("path", path) }
            if (response.status == HttpStatusCode.OK) {
                val category = response.body<Category>()
                category.copy(
                    movies = category.movies.map { movie ->
                        movie.copy(
                            videoUrl = if (movie.videoUrl.startsWith("/")) "$baseUrl${movie.videoUrl}" else movie.videoUrl,
                            thumbnailUrl = if (movie.thumbnailUrl?.startsWith("/") == true) "$baseUrl${movie.thumbnailUrl}" else movie.thumbnailUrl
                        )
                    }.sortedWith(
                        compareBy<Movie> { it.season_number ?: 0 }
                            .thenBy { it.episode_number ?: 0 }
                            .thenBy { it.title }
                    )
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun searchVideos(query: String, category: String): List<Series> {
        return try {
            val response = client.get("$baseUrl/search") { parameter("q", query) }
            val results = response.body<List<Category>>()
            fixCategoryPaths(results).flatMap { it.groupBySeries(it.path) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getLatestMovies(): List<Series> {
        return try {
            getCategorySections("movies", "최신").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAnimations(): List<Series> {
        return try {
            getCategorySections("animations_all", "전체").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDramas(): List<Series> {
        return try {
            val ktv = getCategorySections("koreantv", "드라마").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
            val ftv = getCategorySections("foreigntv", "드라마").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
            ktv + ftv
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAnimationsAll(): List<Series> = getAnimations()

    override suspend fun getHomeSections(): List<HomeSection> {
        return try {
            val response = client.get("$baseUrl/home")
            val sections = response.body<List<HomeSection>>()
            sections.map { section ->
                section.copy(items = fixCategoryPaths(section.items))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun Category.groupBySeries(basePath: String? = null): List<Series> {
        val effectivePath = basePath ?: this.path
        if (this.movies.isEmpty()) {
            return listOf(Series(
                title = this.name.cleanTitle(includeYear = false), 
                episodes = emptyList(), 
                posterPath = this.posterPath, 
                overview = this.overview, 
                year = this.year, 
                fullPath = effectivePath, 
                genreNames = this.genreNames,
                director = this.director,
                actors = this.actors,
                rating = this.rating,
                tmdbId = this.tmdbId
            ))
        }
        return this.movies.groupBy { it.title.cleanTitle(includeYear = false) }
            .map { (title, eps) -> 
                Series(
                    title = title, 
                    episodes = eps.sortedWith(
                        compareBy<Movie> { it.season_number ?: 0 }
                            .thenBy { it.episode_number ?: 0 }
                            .thenBy { it.title }
                    ),
                    posterPath = this.posterPath, 
                    overview = this.overview, 
                    year = this.year, 
                    fullPath = effectivePath, 
                    genreNames = this.genreNames,
                    director = this.director,
                    actors = this.actors,
                    rating = this.rating,
                    tmdbId = this.tmdbId
                )
            }.sortedBy { it.title }
    }
}
