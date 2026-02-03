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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.nas.videoplayer.*
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.ui.common.TmdbAsyncImage

// 시즌 정보를 담는 데이터 클래스
private data class Season(val name: String, val episodes: List<Movie>)

// 데이터 로딩을 위한 데이터 클래스
private data class SeriesDetailData(
    val seasons: List<Season>,
    val metadata: TmdbMetadata?,
    val credits: List<TmdbCast>
)

// 에피소드 정렬 로직 공통화
private fun List<Movie>.sortedByEpisode(): List<Movie> = this.sortedWith(
    compareBy<Movie> { movie -> movie.title.extractSeason() }
        .thenBy { movie -> movie.title.extractEpisode()?.filter { char -> char.isDigit() }?.toIntOrNull() ?: 0 }
)

// 시리즈 상세 정보 (시즌별 에피소드, 메타데이터, 출연진)를 병렬로 로드하는 함수
private suspend fun loadSeriesData(
    series: Series,
    repository: VideoRepository
): SeriesDetailData = coroutineScope {
    val metadataDeferred = async { fetchTmdbMetadata(series.title) }

    val seasonsDeferred = async {
        val path = series.fullPath
        if (path != null) {
            try {
                val content = repository.getCategoryList(path)
                // 현재 폴더에 직접 영상 파일이 있는지 확인
                val hasDirectMovies = content.any { it.movies.isNotEmpty() }

                if (hasDirectMovies) {
                    // 평탄한 구조: 모든 영상을 하나의 시즌으로 묶음
                    listOf(Season("에피소드", content.flatMap { it.movies }.sortedByEpisode()))
                } else {
                    // 계층 구조: 하위 폴더(시즌별)를 각각의 시즌으로 처리
                    content.map { folder ->
                        async {
                            val folderMovies = repository.getCategoryList("$path/${folder.name}").flatMap { it.movies }
                            if (folderMovies.isNotEmpty()) {
                                Season(folder.name, folderMovies.sortedByEpisode())
                            } else null
                        }
                    }.awaitAll().filterNotNull().sortedBy { it.name }
                }
            } catch (_: Exception) {
                if (series.episodes.isNotEmpty()) {
                    listOf(Season("에피소드", series.episodes.sortedByEpisode()))
                } else emptyList()
            }
        } else if (series.episodes.isNotEmpty()) {
            listOf(Season("에피소드", series.episodes.sortedByEpisode()))
        } else {
            emptyList()
        }
    }

    val metadata = metadataDeferred.await()
    val seasons = seasonsDeferred.await()

    val credits = if (metadata.tmdbId != null) {
        fetchTmdbCredits(metadata.tmdbId, metadata.mediaType)
    } else {
        emptyList()
    }

    SeriesDetailData(seasons, metadata, credits)
}


@Composable
fun SeriesDetailScreen(
    series: Series,
    repository: VideoRepository,
    onBack: () -> Unit,
    onPlay: (Movie, List<Movie>) -> Unit
) {
    var detailData by remember { mutableStateOf<SeriesDetailData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    LaunchedEffect(series) {
        isLoading = true
        detailData = loadSeriesData(series, repository)
        selectedSeasonIndex = 0 // 시리즈가 바뀌면 첫 번째 시즌으로 초기화
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())
    ) {
        // 상단 바
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(
                series.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else if (detailData != null) {
            val (seasons, metadata, credits) = detailData!!

            SeriesDetailHeader(series = series, metadata = metadata, credits = credits)

            Spacer(Modifier.height(16.dp))

            if (seasons.isNotEmpty()) {
                // 시즌이 2개 이상일 때만 셀렉트박스 표시
                if (seasons.size > 1) {
                    SeasonSelector(
                        seasons = seasons,
                        selectedIndex = selectedSeasonIndex,
                        onSeasonSelected = { selectedSeasonIndex = it }
                    )
                }

                val currentEpisodes = seasons[selectedSeasonIndex].episodes
                
                EpisodeList(
                    episodes = currentEpisodes,
                    metadata = metadata,
                    onPlay = { movie -> onPlay(movie, currentEpisodes) }
                )
            } else {
                Text("영상 정보를 찾을 수 없습니다.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        }
        
        Spacer(Modifier.height(50.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSelector(
    seasons: List<Season>,
    selectedIndex: Int,
    onSeasonSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = seasons[selectedIndex].name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.widthIn(min = 160.dp).menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                seasons.forEachIndexed { index, season ->
                    DropdownMenuItem(
                        text = { Text(season.name) },
                        onClick = {
                            onSeasonSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailHeader(series: Series, metadata: TmdbMetadata?, credits: List<TmdbCast>) {
    TmdbAsyncImage(
        title = series.title,
        modifier = Modifier.fillMaxWidth().height(280.dp),
        contentScale = ContentScale.Crop,
        isLarge = true
    )

    Text(
        metadata?.overview ?: "상세 정보를 불러오는 중입니다...",
        modifier = Modifier.padding(16.dp),
        color = Color.LightGray,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    if (credits.isNotEmpty()) {
        Text(
            "성우 및 출연진",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(credits) { cast ->
                Column(
                    modifier = Modifier.width(90.dp).padding(end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = "$TMDB_IMAGE_BASE$TMDB_POSTER_SIZE_SMALL${cast.profilePath}",
                        contentDescription = null,
                        modifier = Modifier.size(70.dp).clip(CircleShape).background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cast.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<Movie>,
    metadata: TmdbMetadata?,
    onPlay: (Movie) -> Unit
) {
    Text(
        "에피소드",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Column(Modifier.padding(horizontal = 8.dp)) {
        episodes.forEach { ep ->
            EpisodeItem(ep, metadata, onPlay = { onPlay(ep) })
        }
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp).clickable(onClick = onPlay),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(140.dp).height(80.dp)) {
            AsyncImage(
                model = if (episodeDetails?.stillPath != null) "$TMDB_IMAGE_BASE$TMDB_POSTER_SIZE_SMALL${episodeDetails?.stillPath}" else "",
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                movie.title.prettyTitle(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = episodeDetails?.overview ?: "줄거리 정보가 없습니다.",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}
