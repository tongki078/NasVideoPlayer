package org.nas.videoplayer.data.repository

import io.ktor.client.call.*
import io.ktor.client.request.*
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
    private val baseUrl = NasApiClient.BASE_URL

    override suspend fun getCategoryList(path: String): List<Category> = try {
        client.get("$baseUrl/list?path=${path.encodeURLParameter()}").body()
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun searchVideos(query: String, category: String): List<Series> = try {
        val url = "$baseUrl/search?q=${query.encodeURLParameter()}&lite=false"
        val results: List<Category> = client.get(url).body()
        results.flatMap { it.groupBySeries(it.path) }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getLatestMovies(): List<Series> = try {
        val results: List<Category> = client.get("$baseUrl/movies?lite=true").body()
        results.flatMap { cat -> 
            val fixedPath = if (cat.path?.startsWith("영화") == false) "영화/${cat.path}" else cat.path
            cat.groupBySeries(fixedPath) 
        }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getAnimations(): List<Series> = try {
        val results: List<Category> = client.get("$baseUrl/animations_all?lite=true").body()
        results.flatMap { cat -> 
            val fixedPath = if (cat.path?.startsWith("애니메이션") == false) "애니메이션/${cat.path}" else cat.path
            cat.groupBySeries(fixedPath) 
        }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getDramas(): List<Series> = try {
        val ktv: List<Category> = client.get("$baseUrl/koreantv?lite=true").body()
        val ftv: List<Category> = client.get("$baseUrl/foreigntv?lite=true").body()
        
        val fixedKtv = ktv.flatMap { cat -> 
            val fixedPath = if (cat.path?.startsWith("국내TV") == false) "국내TV/${cat.path}" else cat.path
            cat.groupBySeries(fixedPath)
        }
        val fixedFtv = ftv.flatMap { cat -> 
            val fixedPath = if (cat.path?.startsWith("외국TV") == false) "외국TV/${cat.path}" else cat.path
            cat.groupBySeries(fixedPath)
        }
        
        fixedKtv + fixedFtv
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getAnimationsAll(): List<Series> = getAnimations()

    override suspend fun getHomeSections(): List<HomeSection> = try {
        client.get("$baseUrl/home").body()
    } catch (e: Exception) {
        emptyList()
    }

    private fun Category.groupBySeries(basePath: String? = null): List<Series> {
        val effectivePath = basePath ?: this.path
        
        if (this.movies.isEmpty()) {
            return listOf(
                Series(
                    title = this.name,
                    episodes = emptyList(),
                    posterPath = this.posterPath,
                    overview = this.overview,
                    year = this.year,
                    fullPath = effectivePath
                )
            )
        }

        return this.movies.groupBy { it.title.cleanTitle(includeYear = false) }
            .map { (title, eps) -> 
                Series(
                    title = title, 
                    episodes = eps.sortedWith(
                        compareBy<org.nas.videoplayer.domain.model.Movie> { it.title.extractSeason() }
                            .thenBy { it.title.extractEpisode()?.filter { it.isDigit() }?.toIntOrNull() ?: 0 }
                    ),
                    posterPath = this.posterPath,
                    overview = this.overview,
                    year = this.year,
                    fullPath = effectivePath
                ) 
            }.sortedBy { it.title }
    }
}
