package org.nas.videoplayer.data.repository

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.nas.videoplayer.data.network.NasApiClient
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.model.HomeSection
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

    override suspend fun getCategoryList(path: String): List<Category> = try {
        // 새로운 server.py의 /list 엔드포인트는 keyword 파라미터를 받을 수 있음
        // path 파라미터를 통해 카테고리를 추론함 (server.py의 get_list 로직 참고)
        val response: List<Category> = client.get {
            url("$baseUrl/list")
            parameter("path", path)
        }.body()
        
        fixCategoryPaths(response)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    override suspend fun getCategorySections(category: String, keyword: String?): List<HomeSection> = try {
        // 새로운 server.py의 핵심 엔드포인트인 /category_sections 호출
        val response: List<HomeSection> = client.get {
            url("$baseUrl/category_sections")
            parameter("cat", category)
            if (keyword != null && keyword != "전체") {
                parameter("kw", keyword)
            }
        }.body()

        response.map { section ->
            section.copy(items = fixCategoryPaths(section.items))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    override suspend fun getSeriesDetail(path: String): Category? = try {
        // 새로운 server.py의 시리즈 상세 전용 엔드포인트 호출
        val response: Category = client.get {
            url("$baseUrl/api/series_detail")
            parameter("path", path)
        }.body()
        
        response.copy(
            movies = response.movies.map { movie ->
                movie.copy(
                    videoUrl = if (movie.videoUrl.startsWith("/")) "$baseUrl${movie.videoUrl}" else movie.videoUrl,
                    thumbnailUrl = if (movie.thumbnailUrl?.startsWith("/") == true) "$baseUrl${movie.thumbnailUrl}" else movie.thumbnailUrl
                )
            }
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    override suspend fun searchVideos(query: String, category: String): List<Series> = try {
        val results: List<Category> = client.get {
            url("$baseUrl/search")
            parameter("q", query)
        }.body()
        
        val fixedResults = fixCategoryPaths(results)
        fixedResults.flatMap { it.groupBySeries(it.path) }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    override suspend fun getLatestMovies(): List<Series> = try {
        // /category_sections의 "최신 공개작" 섹션 활용 가능하나, 
        // 기존 인터페이스 유지를 위해 최신 키워드로 요청
        getCategorySections("movies", "최신").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getAnimations(): List<Series> = try {
        getCategorySections("animations_all", "전체").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getDramas(): List<Series> = try {
        val ktv = getCategorySections("koreantv", "드라마").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
        val ftv = getCategorySections("foreigntv", "드라마").flatMap { it.items.flatMap { cat -> cat.groupBySeries() } }
        ktv + ftv
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getAnimationsAll(): List<Series> = getAnimations()

    override suspend fun getHomeSections(): List<HomeSection> = try {
        val response: List<HomeSection> = client.get("$baseUrl/home").body()
        response.map { section ->
            section.copy(items = fixCategoryPaths(section.items))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    private fun Category.groupBySeries(basePath: String? = null): List<Series> {
        val effectivePath = basePath ?: this.path
        // server.py의 시리즈 상세 정보(movies)가 비어있을 수 있으므로 (Lite 모드 등)
        // movies가 있으면 그룹화하고, 없으면 단일 시리즈로 취급
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
                    episodes = eps.sortedWith(compareBy<org.nas.videoplayer.domain.model.Movie> { it.title.extractSeason() }.thenBy { it.title.extractEpisode()?.filter { it.isDigit() }?.toIntOrNull() ?: 0 }), 
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
