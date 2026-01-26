package org.nas.videoplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Category(
    val name: String,
    val movies: List<Movie>
)

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val videoUrl: String
)

// 작품(시리즈) 단위로 묶기 위한 데이터 클래스
data class Series(
    val title: String,
    val episodes: List<Movie>,
    val thumbnailUrl: String? = null
)

enum class Screen { HOME, SERIES, MOVIES, ANIMATIONS }

val client = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 100000
        connectTimeoutMillis = 60000
        socketTimeoutMillis = 100000
    }
}

fun String.toSafeUrl(): String {
    if (this.isBlank()) return this
    try {
        val parts = this.split("?", limit = 2)
        val baseUrl = parts[0]
        if (parts.size < 2) return baseUrl
        val queryString = parts[1]
        if (queryString.startsWith("path=")) {
            val pathValue = queryString.substring(5)
            val encodedPath = pathValue.encodeURLParameter()
            return "$baseUrl?path=$encodedPath"
        }
        return "$baseUrl?${queryString.encodeURLParameter()}"
    } catch (e: Exception) {
        return this
    }
}

fun String.cleanTitle(): String {
    var cleaned = if (this.contains(".")) this.substringBeforeLast('.') else this
    
    val parts = cleaned.split('.')
    if (parts.size > 1) {
        val filteredParts = mutableListOf<String>()
        for (part in parts) {
            val p = part.lowercase()
            if (p.matches(Regex("^e\\d+.*")) || 
                p.contains("1080p") || 
                p.contains("720p") ||
                p.contains("h264") ||
                p.contains("h265") ||
                p.contains("x264") ||
                p.matches(Regex("\\d{6}"))) {
                break
            }
            filteredParts.add(part)
        }
        if (filteredParts.isNotEmpty()) {
            cleaned = filteredParts.joinToString(" ")
        }
    }
    cleaned = cleaned.trim()
    cleaned = cleaned.replace(Regex("\\s*\\((\\d{4})\\)"), " - $1")
    cleaned = cleaned.replace(Regex("\\(([^)]+)\\)"), "[$1]")
    cleaned = cleaned.replace("(더빙)", "[더빙]")
    return cleaned.trim()
}

fun String.extractEpisode(): String? {
    val eMatch = Regex("(?i)[Ee](\\d+)").find(this)
    if (eMatch != null) return "${eMatch.groupValues[1].toInt()}화"
    
    val hwaMatch = Regex("(\\d+)[화회]").find(this)
    if (hwaMatch != null) return "${hwaMatch.groupValues[1].toInt()}화"
    
    return null
}

// 개별 영상 리스트를 시리즈 제목별로 그룹화하는 함수
fun List<Movie>.groupBySeries(): List<Series> {
    return this.groupBy { it.title.cleanTitle() }
        .map { (title, episodes) ->
            Series(
                title = title,
                episodes = episodes.sortedBy { it.title },
                thumbnailUrl = episodes.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
            )
        }
}

@Composable
fun App() {
    setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .components { add(KtorNetworkFetcherFactory(client)) }
            .crossfade(true)
            .build()
    }

    var myCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val response: List<Category> = client.get("http://192.168.0.2:5000/movies").body()
            myCategories = response
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "NAS 연결 실패: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE50914),
            background = Color.Black,
            surface = Color(0xFF121212),
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (selectedMovie != null) {
                // 전체 화면 플레이어
                VideoPlayerScreen(movie = selectedMovie!!) { selectedMovie = null }
            } else if (selectedSeries != null) {
                // 넷플릭스 스타일 상세 화면 (상단 영상 + 하단 회차 리스트)
                SeriesDetailScreen(
                    series = selectedSeries!!,
                    onBack = { selectedSeries = null },
                    onPlayFullScreen = { selectedMovie = it }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = { NetflixBottomNavigation() },
                        containerColor = Color.Black
                    ) { paddingValues ->
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFE50914))
                            }
                        } else if (errorMessage != null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(errorMessage!!, color = Color.White, modifier = Modifier.padding(16.dp))
                                    Button(onClick = { 
                                        isLoading = true
                                        errorMessage = null
                                    }) { Text("다시 시도") }
                                }
                            }
                        } else {
                            when (currentScreen) {
                                Screen.HOME -> HomeScreen(paddingValues, myCategories) { selectedSeries = it }
                                Screen.SERIES -> PlaceholderScreen(paddingValues, "시리즈")
                                Screen.MOVIES -> PlaceholderScreen(paddingValues, "영화")
                                Screen.ANIMATIONS -> AnimationDetailScreen(paddingValues, myCategories) { selectedSeries = it }
                            }
                        }
                    }

                    if (!isLoading && errorMessage == null) {
                        NetflixTopBar(
                            currentScreen = currentScreen,
                            onScreenSelected = { currentScreen = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(paddingValues: PaddingValues, categories: List<Category>, onSeriesClick: (Series) -> Unit) {
    // 모든 영화를 합쳐서 시리즈별로 그룹화
    val allSeries = categories.flatMap { it.movies }.groupBySeries()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        item { 
            val firstMovie = categories.firstOrNull()?.movies?.firstOrNull()
            HeroSection(firstMovie) { movie ->
                val series = allSeries.find { it.episodes.contains(movie) }
                series?.let { onSeriesClick(it) }
            }
        }
        
        // "애니메이션" 제목 아래 가로 리스트로 표시
        item {
            MovieRow("애니메이션", allSeries) { series ->
                onSeriesClick(series)
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun AnimationDetailScreen(paddingValues: PaddingValues, categories: List<Category>, onSeriesClick: (Series) -> Unit) {
    val allSeries = categories.filter { it.name.contains("애니") }.flatMap { it.movies }.groupBySeries()
    
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Spacer(modifier = Modifier.statusBarsPadding().height(60.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding())
        ) {
            item {
                Text(
                    text = "애니메이션",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            
            item {
                MovieRow("전체 리스트", allSeries) { series ->
                    onSeriesClick(series)
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SeriesDetailScreen(series: Series, onBack: () -> Unit, onPlayFullScreen: (Movie) -> Unit) {
    var playingMovie by remember { mutableStateOf<Movie?>(series.episodes.firstOrNull()) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 상단 섹션: 비디오 플레이어
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(0xFF1A1A1A))) {
            playingMovie?.let { movie ->
                VideoPlayer(url = movie.videoUrl.toSafeUrl(), modifier = Modifier.fillMaxSize())
            }
            
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }

        // 작품 정보
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = series.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "총 ${series.episodes.size}개의 에피소드",
                fontSize = 14.sp,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { playingMovie?.let { onPlayFullScreen(it) } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("전체 화면으로 시청", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

        // 하단 섹션: 에피소드 리스트
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(series.episodes) { episode ->
                EpisodeItem(episode) {
                    playingMovie = episode // 상단 플레이어 교체
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(episode: Movie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(120.dp).height(70.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(episode.thumbnailUrl?.toSafeUrl())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFF222222))
            )
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title.extractEpisode() ?: episode.title.cleanTitle(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "재생하기",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PlaceholderScreen(paddingValues: PaddingValues, title: String) {
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
        Text("$title 페이지 준비 중", color = Color.LightGray, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NetflixTopBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Logo",
                tint = Color.Red,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onScreenSelected(Screen.HOME) }
            )
            
            Spacer(modifier = Modifier.width(24.dp))
            
            Text(
                text = "시리즈",
                color = if (currentScreen == Screen.SERIES) Color.White else Color.LightGray,
                fontSize = 15.sp,
                fontWeight = if (currentScreen == Screen.SERIES) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.clickable { onScreenSelected(Screen.SERIES) }
            )
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Text(
                text = "영화",
                color = if (currentScreen == Screen.MOVIES) Color.White else Color.LightGray,
                fontSize = 15.sp,
                fontWeight = if (currentScreen == Screen.MOVIES) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.clickable { onScreenSelected(Screen.MOVIES) }
            )
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Text(
                        text = if (currentScreen == Screen.ANIMATIONS) "애니메이션" else "카테고리",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (currentScreen == Screen.ANIMATIONS) FontWeight.Medium else FontWeight.Normal
                    )
                    Text(" ▼", color = Color.White, fontSize = 10.sp)
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF2B2B2B))
                ) {
                    DropdownMenuItem(
                        text = { Text("애니메이션", color = Color.White, fontWeight = FontWeight.Medium) },
                        onClick = {
                            onScreenSelected(Screen.ANIMATIONS)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("기타", color = Color.White) },
                        onClick = { expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(movie: Movie, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val safeUrl = movie.videoUrl.toSafeUrl()
        VideoPlayer(url = safeUrl, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
fun HeroSection(featuredMovie: Movie?, isCompact: Boolean = false, onPlayClick: (Movie) -> Unit) {
    val height = if (isCompact) 320.dp else 450.dp
    
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        featuredMovie?.thumbnailUrl?.toSafeUrl()?.let { thumbUrl ->
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFF222222)),
                error = ColorPainter(Color(0xFF442222))
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize().background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black),
                    startY = if (isCompact) 100f else 300f
                )
            )
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = featuredMovie?.title?.cleanTitle() ?: "",
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        blurRadius = 8f
                    )
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { featuredMovie?.let { onPlayClick(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text("▶ 재생", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun MovieRow(title: String, seriesList: List<Series>, onSeriesClick: (Series) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(seriesList) { series ->
                SeriesPosterCard(series, onSeriesClick)
            }
        }
    }
}

@Composable
fun SeriesPosterCard(series: Series, onSeriesClick: (Series) -> Unit) {
    Card(
        modifier = Modifier
            .width(135.dp)
            .height(200.dp)
            .clickable { onSeriesClick(series) },
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            series.thumbnailUrl?.toSafeUrl()?.let { thumbUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFF222222)),
                    error = ColorPainter(Color(0xFF442222))
                )
            }
            
            // 텍스트 가독성을 위한 하단 그라데이션 오버레이
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 0f
                        )
                    )
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = series.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "총 ${series.episodes.size}화",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixBottomNavigation() {
    NavigationBar(
        containerColor = Color.Black, 
        contentColor = Color.White, 
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = true, 
            onClick = {}, 
            icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp)) }, 
            label = { Text("홈", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = false, 
            onClick = {}, 
            icon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp)) }, 
            label = { Text("검색", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
    }
}
