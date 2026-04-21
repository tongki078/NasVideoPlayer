package org.nas.videoplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nas.videoplayer.VideoPlayer
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.SubtitleInfo
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.prettyTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    movie: Movie,
    playlist: List<Movie> = emptyList(),
    initialPosition: Long = 0L,
    repository: VideoRepository,
    onPositionUpdate: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    var currentMovie by remember(movie) { mutableStateOf(movie) }
    var isControllerVisible by remember { mutableStateOf(true) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var subtitleInfo by remember { mutableStateOf<SubtitleInfo?>(null) }
    val scope = rememberCoroutineScope()
    
    val nextMovie = remember(currentMovie, playlist) {
        val currentIndex = playlist.indexOfFirst { it.id == currentMovie.id }
        if (currentIndex != -1 && currentIndex < playlist.size - 1) {
            playlist[currentIndex + 1]
        } else null
    }

    // 자막 정보 로딩
    LaunchedEffect(currentMovie) {
        subtitleInfo = null
        val type = if (currentMovie.videoUrl.contains("type=movie")) "movies" 
                  else if (currentMovie.videoUrl.contains("type=ktv")) "koreantv"
                  else if (currentMovie.videoUrl.contains("type=ftv")) "foreigntv"
                  else if (currentMovie.videoUrl.contains("type=anim_all")) "animations_all"
                  else if (currentMovie.videoUrl.contains("type=air")) "air"
                  else "movies"
        
        val path = currentMovie.videoUrl.substringAfter("path=").substringBefore("&")
        try {
            subtitleInfo = repository.getSubtitleInfo(path, type)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 재생 진행률 서버 동기화 (30초마다)
    var currentPos by remember { mutableStateOf(initialPosition) }
    LaunchedEffect(currentMovie.id) {
        while (true) {
            delay(30000)
            if (currentPos > 0) {
                repository.updateProgress(currentMovie.id, (currentPos / 1000).toFloat(), 0f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoPlayer(
            url = currentMovie.videoUrl,
            modifier = Modifier.fillMaxSize(),
            initialPosition = if (currentMovie.id == movie.id) initialPosition else 0L,
            subtitleInfo = subtitleInfo,
            onPositionUpdate = { 
                currentPos = it
                onPositionUpdate(it) 
            },
            onControllerVisibilityChanged = { visible ->
                isControllerVisible = visible
            },
            onVideoEnded = {
                nextMovie?.let { currentMovie = it }
            }
        )

        AnimatedVisibility(
            visible = isControllerVisible && !showEpisodesSheet,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 상단 타이틀 바
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                )
                
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        scope.launch {
                            repository.updateProgress(currentMovie.id, (currentPos / 1000).toFloat(), 0f)
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentMovie.title.prettyTitle(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 하단 버튼들
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 60.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playlist.size > 1) {
                        PlayerControlIcon(Icons.AutoMirrored.Filled.List, "회차") { showEpisodesSheet = true }
                    }

                    if (nextMovie != null) {
                        PlayerControlIcon(Icons.Default.PlayArrow, "다음 화") {
                            currentMovie = nextMovie
                            isControllerVisible = true
                        }
                    }
                }
            }
        }
    }

    if (showEpisodesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEpisodesSheet = false },
            containerColor = Color(0xFF1E1E1E),
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = "회차 정보",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 0.dp)
                ) {
                    items(playlist) { ep ->
                        val isPlaying = ep.id == currentMovie.id
                        EpisodeRow(ep, isPlaying) {
                            currentMovie = ep
                            showEpisodesSheet = false
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EpisodeRow(movie: Movie, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) Color.DarkGray else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
            if (!movie.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = movie.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Text("재생 중", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = movie.title.prettyTitle(),
                color = if (isPlaying) Color.White else Color.LightGray,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = movie.overview ?: "",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
