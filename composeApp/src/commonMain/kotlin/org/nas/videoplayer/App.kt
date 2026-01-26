package org.nas.videoplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
    
    // 점(.)으로 구분된 기술적 정보 제거 (예: 1080p, E01 등)
    val parts = cleaned.split('.')
    if (parts.size > 1) {
        val filteredParts = mutableListOf<String>()
        for (part in parts) {
            if (part.matches(Regex("^[Ee]\\d+.*")) || 
                part.contains("1080p", ignoreCase = true) || 
                part.contains("720p", ignoreCase = true) ||
                part.matches(Regex("\\d{6}"))) {
                break
            }
            filteredParts.add(part)
        }
        if (filteredParts.isNotEmpty()) {
            cleaned = filteredParts.joinToString(" ")
        }
    }
    cleaned = cleaned.trim()

    // 괄호 형식 변경: (더빙) -> [더빙]
    cleaned = cleaned.replace("(더빙)", "[더빙]")
    
    // 년도 형식 변경: (2025) -> - 2025
    cleaned = cleaned.replace(Regex("\\((\\d{4})\\)$"), "- $1")

    return cleaned.trim()
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
                VideoPlayerScreen(movie = selectedMovie!!) { selectedMovie = null }
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
                                Screen.HOME -> HomeScreen(paddingValues, myCategories) { selectedMovie = it }
                                Screen.SERIES -> PlaceholderScreen(paddingValues, "시리즈")
                                Screen.MOVIES -> PlaceholderScreen(paddingValues, "영화")
                                Screen.ANIMATIONS -> AnimationDetailScreen(paddingValues, myCategories) { selectedMovie = it }
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
fun HomeScreen(paddingValues: PaddingValues, categories: List<Category>, onMovieClick: (Movie) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        item { 
            HeroSection(categories.firstOrNull()?.movies?.firstOrNull()) { movie ->
                onMovieClick(movie)
            }
        }
        items(categories) { category ->
            MovieRow(category.name, category.movies) { movie ->
                onMovieClick(movie)
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun AnimationDetailScreen(paddingValues: PaddingValues, categories: List<Category>, onMovieClick: (Movie) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 상단바 영역 확보
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
            
            // 애니메이션 전용 히어로 섹션
            item {
                HeroSection(categories.lastOrNull()?.movies?.firstOrNull(), isCompact = true) { movie ->
                    onMovieClick(movie)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            items(categories) { category ->
                MovieRow(category.name, category.movies) { movie ->
                    onMovieClick(movie)
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
            Text(
                text = "N",
                color = Color.Red,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onScreenSelected(Screen.HOME) }
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
            Text("←", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
fun MovieRow(title: String, movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
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
            items(movies) { movie ->
                MoviePosterCard(movie, onMovieClick)
            }
        }
    }
}

@Composable
fun MoviePosterCard(movie: Movie, onMovieClick: (Movie) -> Unit) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(190.dp)
            .clickable { onMovieClick(movie) },
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            movie.thumbnailUrl?.toSafeUrl()?.let { thumbUrl ->
                // 썸네일 중복 방지를 위해 movie.id를 파라미터로 추가
                val uniqueThumbUrl = if (thumbUrl.contains("?")) "$thumbUrl&v=${movie.id}" else "$thumbUrl?v=${movie.id}"
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(uniqueThumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFF222222)),
                    error = ColorPainter(Color(0xFF442222))
                )
            }
        }
    }
}

@Composable
fun NetflixBottomNavigation() {
    NavigationBar(
        containerColor = Color.Black, 
        contentColor = Color.White, 
        tonalElevation = 0.dp // 넷플릭스 스타일은 평면적임
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
