package org.nas.videoplayer.ui.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.nas.videoplayer.*
import org.nas.videoplayer.data.network.NasApiClient
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.ui.common.TmdbAsyncImage
import org.nas.videoplayer.ui.common.shimmerBrush

// ==========================================================
// 1. Data Models (데이터 구조)
// ==========================================================
private data class Season(val name: String, val episodes: List<Movie>, val seasonNumber: Int)

private data class SeriesDetailState(
    val seasons: List<Season> = emptyList(),
    val metadata: TmdbMetadata? = null,
    val isLoading: Boolean = true,
    val selectedSeasonIndex: Int = 0
)

// ==========================================================
// 2. Business Logic (비즈니스 로직 세분화)
// ==========================================================

private fun List<Movie>.sortedByEpisode(): List<Movie> = this.sortedWith(
    compareBy<Movie> { it.title.extractSeason() }
        .thenBy { it.title.extractEpisode()?.filter { char -> char.isDigit() }?.toIntOrNull() ?: 0 }
)

private suspend fun fetchAllEpisodesRecursive(rootPath: String, repository: VideoRepository): List<Movie> = coroutineScope {
    val initialList = repository.getCategoryList(rootPath)
    val allMovies = mutableListOf<Movie>()
    initialList.forEach { allMovies.addAll(it.movies) }
    
    val subFolders = initialList.filter { it.movies.isEmpty() && !it.path.isNullOrEmpty() && it.path != rootPath }
    val deferredSubMovies = subFolders.map { folder ->
        async { repository.getCategoryList(folder.path!!).flatMap { it.movies } }
    }
    allMovies.addAll(deferredSubMovies.awaitAll().flatten())
    allMovies.distinctBy { it.videoUrl }
}

// ==========================================================
// 3. Main Composable (메인 화면)
// ==========================================================
@Composable
fun SeriesDetailScreen(
    series: Series,
    repository: VideoRepository,
    initialPlaybackPosition: Long = 0L,
    onPositionUpdate: (Long) -> Unit,
    onBack: () -> Unit,
    onPlay: (Movie, List<Movie>, Long) -> Unit,
    onPreviewPlay: (Movie) -> Unit = {}
) {
    var state by remember { mutableStateOf(SeriesDetailState()) }
    var currentPlaybackTime by remember { mutableStateOf(initialPlaybackPosition) }

    LaunchedEffect(series) {
        state = state.copy(isLoading = true)
        coroutineScope {
            val metaDeferred = async { fetchTmdbMetadata(series.title) }
            val episodesDeferred = async { 
                if (!series.fullPath.isNullOrEmpty()) fetchAllEpisodesRecursive(series.fullPath, repository)
                else series.episodes
            }
            val meta = metaDeferred.await()
            val rawEpisodes = episodesDeferred.await().sortedByEpisode()
            if (rawEpisodes.isNotEmpty()) {
                val seasons = rawEpisodes.groupBy { it.title.extractSeason() }
                    .map { (sNum, eps) -> Season("시즌 $sNum", eps, sNum) }
                    .sortedBy { it.seasonNumber }
                state = state.copy(metadata = meta, seasons = seasons, isLoading = false, selectedSeasonIndex = 0)
            } else {
                state = state.copy(isLoading = false, metadata = meta, seasons = emptyList())
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) { pv ->
        if (state.isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(pv)) { HeaderSkeleton() }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                DetailContent(
                    series = series, state = state, initialPosition = currentPlaybackTime,
                    onPositionUpdate = { currentPlaybackTime = it; onPositionUpdate(it) },
                    onSeasonSelected = { index -> state = state.copy(selectedSeasonIndex = index) },
                    onPlay = onPlay, onPreviewPlay = onPreviewPlay
                )
                DetailFloatingTopBar(series.title, onBack)
            }
        }
    }
}

@Composable
private fun DetailFloatingTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)) { 
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) 
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailContent(
    series: Series, state: SeriesDetailState, initialPosition: Long,
    onPositionUpdate: (Long) -> Unit, onSeasonSelected: (Int) -> Unit,
    onPlay: (Movie, List<Movie>, Long) -> Unit, onPreviewPlay: (Movie) -> Unit
) {
    val firstSeason = state.seasons.firstOrNull()
    val firstEpisode = firstSeason?.episodes?.firstOrNull()

    LaunchedEffect(firstEpisode) { if (firstEpisode != null) onPreviewPlay(firstEpisode) }

    val playFirstEpisode = {
        if (firstEpisode != null && firstSeason != null) onPlay(firstEpisode, firstSeason.episodes, initialPosition)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SeriesDetailHeader(
            series = series, metadata = state.metadata, previewUrl = firstEpisode?.videoUrl,
            initialPosition = initialPosition, onPositionUpdate = onPositionUpdate,
            onPlayClick = playFirstEpisode, onFullscreenClick = playFirstEpisode
        )
        Spacer(Modifier.height(16.dp))
        if (state.seasons.isNotEmpty()) {
            if (state.seasons.size > 1) SeasonSelector(seasons = state.seasons, selectedIndex = state.selectedSeasonIndex, onSeasonSelected = onSeasonSelected)
            val currentEpisodes = state.seasons[state.selectedSeasonIndex].episodes
            EpisodeList(episodes = currentEpisodes, onPlay = { movie -> onPlay(movie, currentEpisodes, 0L) })
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("시청 가능한 회차가 없습니다.", color = Color.Gray) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSelector(seasons: List<Season>, selectedIndex: Int, onSeasonSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = seasons[selectedIndex].name, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.widthIn(min = 160.dp).menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                seasons.forEachIndexed { index, season ->
                    DropdownMenuItem(text = { Text(season.name) }, onClick = { onSeasonSelected(index); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailHeader(
    series: Series, metadata: TmdbMetadata?, previewUrl: String? = null,
    initialPosition: Long = 0L, onPositionUpdate: (Long) -> Unit = {},
    onPlayClick: () -> Unit, onFullscreenClick: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        if (previewUrl != null) {
            // VideoRepository에서 이미 baseUrl이 붙은 URL을 제공하므로 직접 처리하지 않아도 됨
            // 만약 그래도 누락된 경우를 대비해 보수적으로 유지
            val fullVideoUrl = if (previewUrl.startsWith("/")) "${NasApiClient.BASE_URL}$previewUrl" else previewUrl
            VideoPlayer(url = fullVideoUrl, modifier = Modifier.fillMaxSize(), initialPosition = initialPosition, onPositionUpdate = onPositionUpdate, onFullscreenClick = onFullscreenClick)
        } else {
            TmdbAsyncImage(title = series.title, posterPath = series.posterPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, isLarge = true)
            Box(modifier = Modifier.align(Alignment.Center).size(70.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).clickable { onPlayClick() }.padding(8.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
    }
    Button(onClick = onPlayClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(4.dp)) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text("재생", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    val overview = metadata?.overview ?: series.overview ?: "상세 정보를 불러오는 중입니다..."
    Text(text = overview, modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun EpisodeList(episodes: List<Movie>, onPlay: (Movie) -> Unit) {
    Text("회차 정보", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
    Column(Modifier.padding(horizontal = 8.dp)) { episodes.forEach { ep -> EpisodeItem(ep, onPlay = { onPlay(ep) }) } }
}

@Composable
fun EpisodeItem(movie: Movie, onPlay: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp).clickable(onClick = onPlay), verticalAlignment = Alignment.CenterVertically) {
        val imageUrl = movie.thumbnailUrl ?: ""
        var isLoading by remember { mutableStateOf(true) }
        Box(modifier = Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush(showShimmer = isLoading && imageUrl.isNotEmpty()))) {
            if (imageUrl.isNotEmpty()) {
                val model = if (imageUrl.startsWith("/")) "${NasApiClient.BASE_URL}$imageUrl" else imageUrl
                AsyncImage(model = model, contentDescription = movie.title, onState = { state -> isLoading = state is AsyncImagePainter.State.Loading }, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(movie.title.prettyTitle(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(text = "에피소드 정보가 없습니다.", color = Color.Gray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun HeaderSkeleton() {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(320.dp).background(shimmerBrush()))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush()))
            Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush()))
            Box(Modifier.fillMaxWidth(0.7f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush()))
        }
    }
}
