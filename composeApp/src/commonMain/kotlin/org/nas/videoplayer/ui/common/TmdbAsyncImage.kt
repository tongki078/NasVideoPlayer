package org.nas.videoplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.nas.videoplayer.*

@Composable
fun TmdbAsyncImage(
    title: String, 
    modifier: Modifier = Modifier, 
    contentScale: ContentScale = ContentScale.Crop, 
    typeHint: String? = null, 
    isLarge: Boolean = false,
    isAnimation: Boolean = false,
    posterPath: String? = null // 추가: 서버에서 이미 찾은 포스터 경로
) {
    val cacheKey = if (isAnimation) "ani_$title" else title
    var metadata by remember(cacheKey) { mutableStateOf(tmdbCache[cacheKey]) }
    var isError by remember(cacheKey, posterPath) { mutableStateOf(false) }
    var isLoading by remember(cacheKey, posterPath) { mutableStateOf(metadata == null && posterPath == null) }
    
    // 서버에서 준 posterPath가 있으면 우선 사용, 없으면 캐시된 메타데이터 사용
    val finalPosterPath = posterPath ?: metadata?.posterUrl?.substringAfterLast("/")
    val imageUrl = if (finalPosterPath != null) {
        val size = if (isLarge) TMDB_POSTER_SIZE_LARGE else TMDB_POSTER_SIZE_MEDIUM
        "https://image.tmdb.org/t/p/$size/$finalPosterPath"
    } else null

    LaunchedEffect(cacheKey, posterPath) {
        if (posterPath == null && metadata == null) {
            isLoading = true
            metadata = fetchTmdbMetadata(title, typeHint, isAnimation = isAnimation)
            isLoading = false
        }
    }
    
    Box(
        modifier = modifier
            .background(shimmerBrush(showShimmer = isLoading && !isError))
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(imageUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onSuccess = { isLoading = false; isError = false },
                onError = { isError = true; isLoading = false }
            )
        }
        
        if (isError && !isLoading && imageUrl == null) {
            Box(Modifier.fillMaxSize().padding(8.dp), Alignment.Center) {
                Text(
                    text = title.cleanTitle(includeYear = false), 
                    color = Color.Gray, 
                    fontSize = 10.sp, 
                    textAlign = TextAlign.Center, 
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
