package org.nas.videoplayer.ui.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.ui.common.TmdbAsyncImage
import org.nas.videoplayer.ui.common.shimmerBrush

// ==========================================================
// 1. Data Models (데이터 구조)
// ==========================================================
private data class Season(val name: String, val episodes: List<Movie>)

private data class SeriesDetailState(
    val seasons: List<Season> = emptyList(),
    val metadata: TmdbMetadata? = null,
    val credits: List<TmdbCast> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSeasonIndex: Int = 0
)

// ==========================================================
// 2. Business Logic (비즈니스 로직 세분화)
// ==========================================================

// 에피소드 정렬 유틸
private fun List<Movie>.sortedByEpisode(): List<Movie> = this.sortedWith(
    compareBy<Movie> { it.title.extractSeason() }
        .thenBy { it.title.extractEpisode()?.filter { char -> char.isDigit() }?.toIntOrNull() ?: 0 }
)

// 시즌 정보 로드
private suspend fun loadSeasons(series: Series, repository: VideoRepository): List<Season> {
    val path = series.fullPath ?: return if (series.episodes.isNotEmpty()) {
        listOf(Season("에피소드", series.episodes.sortedByEpisode()))
    } else emptyList()

    return try {
        val content = repository.getCategoryList(path)
        val hasDirectMovies = content.any { it.movies.isNotEmpty() }

        if (hasDirectMovies) {
            listOf(Season("에피소드", content.flatMap { it.movies }.sortedByEpisode()))
        } else {
            coroutineScope {
                content.map { folder ->
                    async {
                        val folderMovies = repository.getCategoryList("$path/${folder.name}").flatMap { it.movies }
                        if (folderMovies.isNotEmpty()) Season(folder.name, folderMovies.sortedByEpisode()) else null
                    }
                }.awaitAll().filterNotNull().sortedBy { it.name }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// 메타데이터 및 출연진 로드
private suspend fun loadMetadataAndCredits(title: String): Pair<TmdbMetadata?, List<TmdbCast>> {
    val metadata = fetchTmdbMetadata(title)
    val credits = if (metadata.tmdbId != null) {
        fetchTmdbCredits(metadata.tmdbId, metadata.mediaType)
    } else emptyList()
    return metadata to credits
}

// ==========================================================
// 3. Main Composable (메인 화면)
// ==========================================================
@Composable
fun SeriesDetailScreen(
    series: Series,
    repository: VideoRepository,
    onBack: () -> Unit,
    onPlay: (Movie, List<Movie>) -> Unit
) {
    var state by remember { mutableStateOf(SeriesDetailState()) }

    LaunchedEffect(series) {
        state = state.copy(isLoading = true)
        coroutineScope {
            val seasonsDeferred = async { loadSeasons(series, repository) }
            val metaDeferred = async { loadMetadataAndCredits(series.title) }

            val seasons = seasonsDeferred.await()
            val (metadata, credits) = metaDeferred.await()

            state = state.copy(
                seasons = seasons,
                metadata = metadata,
                credits = credits,
                isLoading = false,
                selectedSeasonIndex = 0
            )
        }
    }

    Scaffold(
        topBar = { DetailTopBar(series.title, onBack) },
        containerColor = Color.Black
    ) { pv ->
        if (state.isLoading) {
            LoadingIndicator()
        } else {
            DetailContent(
                paddingValues = pv,
                series = series,
                state = state,
                onSeasonSelected = { index -> state = state.copy(selectedSeasonIndex = index) },
                onPlay = onPlay
            )
        }
    }
}

// ==========================================================
// 4. UI Components (UI 단위 조각)
// ==========================================================

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.Red)
    }
}

@Composable
private fun DetailContent(
    paddingValues: PaddingValues,
    series: Series,
    state: SeriesDetailState,
    onSeasonSelected: (Int) -> Unit,
    onPlay: (Movie, List<Movie>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState())) {
        SeriesDetailHeader(series = series, metadata = state.metadata, credits = state.credits)

        Spacer(Modifier.height(16.dp))

        if (state.seasons.isNotEmpty()) {
            if (state.seasons.size > 1) {
                SeasonSelector(
                    seasons = state.seasons,
                    selectedIndex = state.selectedSeasonIndex,
                    onSeasonSelected = onSeasonSelected
                )
            }

            val currentEpisodes = state.seasons[state.selectedSeasonIndex].episodes
            EpisodeList(
                episodes = currentEpisodes,
                metadata = state.metadata,
                onPlay = { movie -> onPlay(movie, currentEpisodes) }
            )
        } else {
            Text("영상 정보를 찾을 수 없습니다.", color = Color.Gray, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(50.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSelector(seasons: List<Season>, selectedIndex: Int, onSeasonSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = seasons[selectedIndex].name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Red, unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                ),
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
private fun SeriesDetailHeader(series: Series, metadata: TmdbMetadata?, credits: List<TmdbCast>) {
    TmdbAsyncImage(title = series.title, modifier = Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Crop, isLarge = true)
    Text(metadata?.overview ?: "상세 정보를 불러오는 중입니다...", modifier = Modifier.padding(16.dp), color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp)

    if (credits.isNotEmpty()) {
        Text("성우 및 출연진", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(credits) { cast ->
                Column(modifier = Modifier.width(90.dp).padding(end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    var isLoading by remember { mutableStateOf(true) }
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush(showShimmer = isLoading))
                    ) {
                        AsyncImage(
                            model = "$TMDB_IMAGE_BASE$TMDB_POSTER_SIZE_SMALL${cast.profilePath}",
                            contentDescription = cast.name,
                            onState = { state -> isLoading = state is AsyncImagePainter.State.Loading },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(cast.name, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(episodes: List<Movie>, metadata: TmdbMetadata?, onPlay: (Movie) -> Unit) {
    Text("에피소드", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Column(Modifier.padding(horizontal = 8.dp)) {
        episodes.forEach { ep -> EpisodeItem(ep, metadata, onPlay = { onPlay(ep) }) }
    }
}

@Composable
fun EpisodeItem(movie: Movie, seriesMeta: TmdbMetadata?, onPlay: () -> Unit) {
    var episodeDetails by remember { mutableStateOf<TmdbEpisode?>(null) }
    LaunchedEffect(movie, seriesMeta) {
        if (seriesMeta?.tmdbId != null && seriesMeta.mediaType == "tv") {
            val season = movie.title.extractSeason()
            val episodeNum = movie.title.extractEpisode()?.filter { it.isDigit() }?.toIntOrNull() ?: 1
            episodeDetails = fetchTmdbEpisodeDetails(seriesMeta.tmdbId, season, episodeNum)
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp).clickable(onClick = onPlay), verticalAlignment = Alignment.CenterVertically) {
        val imageUrl = when {
            episodeDetails?.stillPath != null -> "$TMDB_IMAGE_BASE$TMDB_POSTER_SIZE_SMALL${episodeDetails?.stillPath}"
            seriesMeta?.posterUrl != null -> seriesMeta.posterUrl
            else -> ""
        }
        
        var isLoading by remember { mutableStateOf(true) }
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush(showShimmer = isLoading && imageUrl.isNotEmpty()))
        ) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = movie.title,
                    onState = { state -> isLoading = state is AsyncImagePainter.State.Loading },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(movie.title.prettyTitle(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(text = episodeDetails?.overview ?: "줄거리 정보가 없습니다.", color = Color.Gray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
    }
}
