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

    private fun fixMoviePaths(movie: Movie): Movie {
        return movie.copy(
            videoUrl = if (movie.videoUrl.startsWith("/")) "$baseUrl${movie.videoUrl}" else movie.videoUrl,
            thumbnailUrl = if (movie.thumbnailUrl?.startsWith("/") == true) "$baseUrl${movie.thumbnailUrl}" else movie.thumbnailUrl
        )
    }

    private fun fixCategoryPaths(category: Category): Category {
        val fixedMovies = category.movies.map { fixMoviePaths(it) }
        val fixedSeasons = category.seasons?.mapValues { entry -> 
            entry.value.map { fixMoviePaths(it) } 
        }
        return category.copy(movies = fixedMovies, seasons = fixedSeasons)
    }

    private fun fixCategoriesPaths(categories: List<Category>): List<Category> {
        return categories.map { fixCategoryPaths(it) }
    }

    override suspend fun getCategoryList(path: String): List<Category> {
        return try {
            val response = client.get("$baseUrl/list") { parameter("path", path) }
            val data = response.body<List<Category>>()
            fixCategoriesPaths(data)
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
                section.copy(items = fixCategoriesPaths(section.items))
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
                fixCategoryPaths(category)
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
            val response = client.get("$baseUrl/search") { 
                parameter("q", query)
                if (category != "전체") parameter("cat", category)
            }
            val results = response.body<List<Category>>()
            fixCategoriesPaths(results).map { it.toSeries(it.path) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getLatestMovies(): List<Series> {
        return try {
            getCategorySections("movies", "최신").flatMap { it.items.map { cat -> cat.toSeries() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAnimations(): List<Series> {
        return try {
            getCategorySections("animations_all", "전체").flatMap { it.items.map { cat -> cat.toSeries() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDramas(): List<Series> {
        return try {
            val ktv = getCategorySections("koreantv", "드라마").flatMap { it.items.map { cat -> cat.toSeries() } }
            val ftv = getCategorySections("foreigntv", "드라마").flatMap { it.items.map { cat -> cat.toSeries() } }
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
                section.copy(items = fixCategoriesPaths(section.items))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun Category.toSeries(basePath: String? = null): Series {
        val effectivePath = basePath ?: this.path
        return Series(
            title = this.name, 
            episodes = this.movies, 
            posterPath = this.posterPath, 
            overview = this.overview, 
            year = this.year, 
            fullPath = effectivePath, 
            genreNames = this.genreNames,
            director = this.director,
            actors = this.actors,
            rating = this.rating,
            tmdbId = this.tmdbId,
            seasons = this.seasons
        )
    }
}
