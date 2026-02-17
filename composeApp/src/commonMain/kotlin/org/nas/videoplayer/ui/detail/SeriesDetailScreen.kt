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
import org.nas.videoplayer.*
import org.nas.videoplayer.domain.model.*
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.ui.common.TmdbAsyncImage
import org.nas.videoplayer.ui.common.shimmerBrush

private fun List<Movie>.sortedByEpisode(): List<Movie> = this.sortedWith(
    compareBy<Movie> { it.season_number ?: it.title.extractSeason() }
        .thenBy { it.episode_number ?: it.title.extractEpisode()?.filter { char -> char.isDigit() }?.toIntOrNull() ?: 0 }
)

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

    LaunchedEffect(series.fullPath) {
        state = state.copy(isLoading = true)
        try {
            val detail = series.fullPath?.let { repository.getSeriesDetail(it) }

            val finalDetail = detail ?: Category(
                name = series.title,
                posterPath = series.posterPath,
                overview = series.overview,
                year = series.year,
                movies = series.episodes,
                rating = series.rating,
                genreNames = series.genreNames,
                director = series.director,
                actors = series.actors,
                tmdbId = series.tmdbId
            )

            val rawEpisodes = finalDetail.movies.sortedByEpisode()
            if (rawEpisodes.isNotEmpty()) {
                val seasons = rawEpisodes.groupBy { it.season_number ?: it.title.extractSeason() }
                    .map { (sNum, eps) -> Season("시즌 $sNum", eps, sNum) }
                    .sortedBy { it.seasonNumber }
                state = state.copy(detail = finalDetail, seasons = seasons, isLoading = false)
            } else {
                state = state.copy(detail = finalDetail, seasons = emptyList(), isLoading = false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            state = state.copy(isLoading = false)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) { pv ->
        Box(modifier = Modifier.fillMaxSize().padding(pv)) {
            DetailContent(
                series = series, 
                state = state, 
                initialPosition = currentPlaybackTime,
                onPositionUpdate = { currentPlaybackTime = it; onPositionUpdate(it) },
                onSeasonSelected = { index -> state = state.copy(selectedSeasonIndex = index) },
                onPlay = onPlay, 
                onPreviewPlay = onPreviewPlay
            )
            DetailFloatingTopBar(series.title, onBack)
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
    val firstSeason = state.seasons.getOrNull(state.selectedSeasonIndex) ?: state.seasons.firstOrNull()
    val firstEpisode = firstSeason?.episodes?.firstOrNull()

    LaunchedEffect(firstEpisode?.id) { 
        if (firstEpisode != null) onPreviewPlay(firstEpisode) 
    }

    val playFirstEpisode = {
        if (firstSeason != null) {
            val targetEpisode = firstSeason.episodes.firstOrNull()
            if (targetEpisode != null) {
                onPlay(targetEpisode, firstSeason.episodes, initialPosition)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SeriesDetailHeader(
            series = series, 
            detail = state.detail, 
            previewEpisode = firstEpisode,
            initialPosition = initialPosition, 
            onPositionUpdate = onPositionUpdate,
            onPlayClick = playFirstEpisode, 
            onFullscreenClick = playFirstEpisode
        )
        
        Spacer(Modifier.height(16.dp))

        state.detail?.actors?.let { actors ->
            if (actors.isNotEmpty()) {
                CastList(actors = actors)
            }
        }
        
        if (state.seasons.isNotEmpty()) {
            if (state.seasons.size > 1) {
                SeasonSelector(seasons = state.seasons, selectedIndex = state.selectedSeasonIndex, onSeasonSelected = onSeasonSelected)
            }
            val currentEpisodes = state.seasons.getOrNull(state.selectedSeasonIndex)?.episodes ?: emptyList()
            if (currentEpisodes.isNotEmpty()) {
                EpisodeList(episodes = currentEpisodes, onPlay = { movie -> onPlay(movie, currentEpisodes, 0L) })
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { 
                    Text("시청 가능한 회차가 없습니다.", color = Color.Gray) 
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { 
                Text("시청 가능한 회차가 없습니다.", color = Color.Gray) 
            }
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
                value = seasons.getOrNull(selectedIndex)?.name ?: "", onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.widthIn(min = 160.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
    series: Series, detail: Category?, previewEpisode: Movie? = null,
    initialPosition: Long = 0L, onPositionUpdate: (Long) -> Unit = {},
    onPlayClick: () -> Unit, onFullscreenClick: () -> Unit = {}
) {
    val isIos = remember { getPlatform().isIos }
    
    val previewUrl = remember(previewEpisode, isIos) {
        if (isIos) {
            // iOS: 미리보기(preview_serve)가 fMP4 관련 문제로 재생되지 않는 경우,
            // HLS 지원이 되는 video_serve(전체 영상)를 사용하여 1분 지점부터 재생
            previewEpisode?.videoUrl
        } else {
            previewEpisode?.videoUrl?.replace("/video_serve", "/preview_serve")
        }
    }
    
    val startPos = if (isIos) 60000L else 0L

    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        if (previewUrl != null) {
            VideoPlayer(
                url = previewUrl, 
                modifier = Modifier.fillMaxSize(), 
                initialPosition = startPos, 
                onPositionUpdate = { pos ->
                    // iOS 미리보기 시 90초(1분30초) 지나면 다시 처음(60초)으로 루프하거나 정지
                    if (isIos && pos > 90000L) {
                        // 루프 로직은 VideoPlayer에 없으므로 여기선 간단히 둠 (실제 구현 시 seek 필요할 수 있음)
                    }
                    onPositionUpdate(pos)
                },
                onFullscreenClick = onFullscreenClick,
                onControllerVisibilityChanged = null,
                onVideoEnded = null
            )
        } else {
            val posterPath = detail?.posterPath ?: series.posterPath
            TmdbAsyncImage(title = series.title, posterPath = posterPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, isLarge = true)
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
    
    val year = detail?.year ?: series.year
    val rating = detail?.rating ?: series.rating
    val genres = detail?.genreNames?.joinToString(", ")
    
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!year.isNullOrBlank()) {
            Text(year, color = Color.LightGray, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
        }
        if (!rating.isNullOrBlank()) {
            Text(rating, color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.border(1.dp, Color.Gray, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp))
            Spacer(Modifier.width(12.dp))
        }
        if (!genres.isNullOrBlank()) {
            Text(genres, color = Color.LightGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    
    Spacer(Modifier.height(8.dp))
    
    val overview = detail?.overview ?: series.overview ?: "상세 정보를 불러오는 중입니다..."
    Text(text = overview, modifier = Modifier.padding(horizontal = 16.dp), color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun EpisodeList(episodes: List<Movie>, onPlay: (Movie) -> Unit) {
    Text("회차 정보", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
    Column(Modifier.padding(horizontal = 8.dp)) { 
        episodes.forEach { ep -> EpisodeItem(ep, onPlay = { onPlay(ep) }) } 
    }
}

@Composable
fun EpisodeItem(movie: Movie, onPlay: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp).clickable(onClick = onPlay), verticalAlignment = Alignment.CenterVertically) {
        val imageUrl = movie.thumbnailUrl ?: ""
        Box(modifier = Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush(showShimmer = imageUrl.isEmpty()))) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(model = imageUrl, contentDescription = movie.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(movie.title.prettyTitle(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val summary = movie.overview ?: "에피소드 정보가 없습니다."
            Text(text = summary, color = Color.Gray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun CastList(actors: List<Actor>) {
    Text("출연진", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(actors) { actor ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                Box(modifier = Modifier.size(70.dp).clip(CircleShape).background(Color.DarkGray)) {
                    actor.profile?.let { profilePath ->
                        AsyncImage(
                            model = "https://image.tmdb.org/t/p/w185$profilePath",
                            contentDescription = actor.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(actor.name, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
