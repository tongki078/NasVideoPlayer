package org.nas.videoplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nas.videoplayer.ui.common.TmdbAsyncImage
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.domain.model.HomeSection
import org.nas.videoplayer.data.WatchHistory
import org.nas.videoplayer.cleanTitle

@Composable
fun HomeScreen(
    watchHistory: List<WatchHistory>,
    homeSections: List<HomeSection>, // 서버에서 온 홈 섹션 리스트
    isLoading: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    onSeriesClick: (Series) -> Unit,
    onPlayClick: (Movie) -> Unit,
    onHistoryClick: (WatchHistory) -> Unit = {}
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.Red)
        }
    } else {
        val heroCategory = remember(homeSections) {
            homeSections.firstOrNull()?.items?.firstOrNull()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = lazyListState
        ) {
            if (heroCategory != null) {
                item {
                    HeroSection(
                        category = heroCategory,
                        onClick = { onSeriesClick(heroCategory.toSeries()) },
                        onPlay = { onSeriesClick(heroCategory.toSeries()) }
                    )
                }
            }
            
            if (watchHistory.isNotEmpty()) {
                item { SectionTitle("시청 중인 콘텐츠") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(watchHistory) { history ->
                            MovieCard(
                                title = history.title.cleanTitle(),
                                posterPath = null, // 시청 기록에는 posterPath가 없음
                                isAnimation = history.screenType == "animation",
                                onClick = { onHistoryClick(history) }
                            )
                        }
                    }
                }
                // 시청 기록 다음에 홈 섹션이 있으면 간격 추가
                if (homeSections.isNotEmpty()) {
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }

            // 홈 섹션 반복 (인덱스 활용)
            itemsIndexed(homeSections) { index, section ->
                // 시청 기록이 없으면 첫 번째 섹션 위에는 간격을 주지 않고,
                // 시청 기록이 있으면 위에서 이미 간격을 주었으므로 index > 0일 때만 추가
                // 하지만 itemsIndexed는 리스트 내부의 인덱스이므로,
                // watchHistory가 비어있을 때 index > 0인 경우에만 Spacer 추가
                // watchHistory가 있을 때는 위에서 추가했으니, 여기서는 index > 0인 경우에만 추가하면 됨.
                // 즉, "섹션 간"의 간격은 항상 index > 0 일 때 추가.
                
                if (index > 0) {
                    Spacer(Modifier.height(20.dp))
                }
                
                SectionTitle(section.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(section.items) { item ->
                        MovieCard(
                            title = item.name.cleanTitle(includeYear = false), 
                            posterPath = item.posterPath,
                            isAnimation = item.path?.contains("애니메이션") ?: false,
                            onClick = { onSeriesClick(item.toSeries()) }
                        )
                    }
                }
            }
            
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

private fun Category.toSeries() = Series(
    title = this.name.cleanTitle(includeYear = false),
    episodes = this.movies,
    posterPath = this.posterPath,
    overview = this.overview,
    year = this.year,
    fullPath = this.path,
    genreNames = this.genreNames,
    director = this.director,
    actors = this.actors,
    rating = this.rating,
    tmdbId = this.tmdbId
)

@Composable
private fun HeroSection(category: Category, onClick: () -> Unit, onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .clickable { onClick() }
            .background(Color.Black)
    ) {
        TmdbAsyncImage(
            title = category.name,
            posterPath = category.posterPath,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            isLarge = true
        )
        
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black))))

        Card(
            modifier = Modifier.width(280.dp).height(400.dp).align(Alignment.TopCenter).padding(top = 40.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            TmdbAsyncImage(
                title = category.name, 
                posterPath = category.posterPath,
                modifier = Modifier.fillMaxSize(), 
                isLarge = true
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.name.cleanTitle(includeYear = false),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, shadow = Shadow(color = Color.Black, blurRadius = 8f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onPlay() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(120.dp).height(45.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("정보", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, modifier = Modifier.padding(16.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
}

@Composable
private fun MovieCard(title: String, posterPath: String?, isAnimation: Boolean, onClick: () -> Unit) {
    Card(Modifier.size(130.dp, 200.dp).padding(end = 12.dp).clickable(onClick = onClick)) {
        TmdbAsyncImage(title = title, posterPath = posterPath, modifier = Modifier.fillMaxSize(), isAnimation = isAnimation)
    }
}
