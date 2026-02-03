package org.nas.videoplayer

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil3.compose.*
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.*
import coil3.disk.DiskCache
import okio.Path.Companion.toPath
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.nas.videoplayer.data.*
import org.nas.videoplayer.db.AppDatabase
import com.squareup.sqldelight.db.SqlDriver

// --- 앱 설정 및 기본 데이터 모델 ---
const val BASE_URL = "http://192.168.0.2:5000"
const val IPHONE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, lifestyle Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

@Serializable
data class Category(val name: String, val movies: List<Movie> = emptyList())
@Serializable
data class Movie(val id: String, val title: String, val thumbnailUrl: String? = null, val videoUrl: String, val duration: String? = null)
data class Series(val title: String, val episodes: List<Movie>, val thumbnailUrl: String? = null, val fullPath: String? = null)

enum class Screen { HOME, SEARCH, ON_AIR, ANIMATIONS, MOVIES, FOREIGN_TV, KOREAN_TV, LATEST }

val client = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    install(HttpTimeout) { requestTimeoutMillis = 60000; connectTimeoutMillis = 15000; socketTimeoutMillis = 60000 }
    defaultRequest { header("User-Agent", IPHONE_USER_AGENT) }
}

// --- 공통 유틸리티 (TmdbApi 연동) ---
suspend fun fetchCategoryList(path: String): List<Category> = try { client.get("$BASE_URL/list?path=${path.encodeURLParameter()}").body() } catch (e: Exception) { emptyList() }

suspend fun searchContent(query: String, category: String = "전체"): List<Series> = try {
    val url = "$BASE_URL/search?q=${query.encodeURLParameter()}" + if (category != "전체") "&category=${category.encodeURLParameter()}" else ""
    val results: List<Category> = client.get(url).body()
    results.flatMap { it.movies }.groupBySeries()
} catch (e: Exception) { emptyList() }

fun List<Movie>.groupBySeries(): List<Series> = this.groupBy { it.title.cleanTitle(includeYear = false) }
    .map { (title, eps) -> 
        Series(title, eps.sortedWith(compareBy<Movie>{ it.title.extractSeason() }.thenBy { it.title.extractEpisode()?.filter { it.isDigit() }?.toIntOrNull() ?: 0 })) 
    }.sortedBy { it.title }

@Composable
fun TmdbAsyncImage(title: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, typeHint: String? = null, isLarge: Boolean = false) {
    var metadata by remember(title) { mutableStateOf(tmdbCache[title]) }
    var isError by remember(title) { mutableStateOf(false) }
    var isLoading by remember(title) { mutableStateOf(metadata == null) }
    val imageUrl = metadata?.posterUrl?.replace(TMDB_POSTER_SIZE_MEDIUM, if (isLarge) TMDB_POSTER_SIZE_LARGE else TMDB_POSTER_SIZE_SMALL)

    LaunchedEffect(title) {
        if (metadata == null) {
            isLoading = true; metadata = fetchTmdbMetadata(title, typeHint)
            isError = metadata?.posterUrl == null; isLoading = false
        } else { isError = metadata?.posterUrl == null; isLoading = false }
    }
    
    Box(modifier = modifier.background(Color(0xFF1A1A1A))) {
        if (imageUrl != null) AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(imageUrl).crossfade(200).build(), null, Modifier.fillMaxSize(), contentScale = contentScale, onError = { isError = true })
        if (isError && !isLoading) Box(Modifier.fillMaxSize().padding(8.dp), Alignment.Center) { Text(title.cleanTitle(includeYear = false), color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 3) }
        if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(20.dp), Color.Red, 2.dp)
    }
}
