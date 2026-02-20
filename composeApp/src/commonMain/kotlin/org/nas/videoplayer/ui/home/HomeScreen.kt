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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nas.videoplayer.ui.common.TmdbAsyncImage
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.domain.model.HomeSection
import org.nas.videoplayer.data.WatchHistory
import org.nas.videoplayer.cleanTitle
import org.nas.videoplayer.ui.common.HeroSection

@Composable
fun HomeScreen(
    watchHistory: List<WatchHistory>,
    homeSections: List<HomeSection>, // 서버에서 온 홈 섹션 리스트
    isLoading: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    onSeriesClick: (Series) -> Unit,
    onPlayClick: (Movie, List<Movie>) -> Unit,
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
                        onInfoClick = { onSeriesClick(heroCategory.toSeries()) },
                        onPlayClick = {
                            val firstEp = heroCategory.movies.firstOrNull()
                            if (firstEp != null) {
                                onPlayClick(firstEp, heroCategory.movies)
                            } else {
                                // movies가 비어있을 경우 (상세 정보를 아직 안가져와서 그럴 수 있음)
                                // 정보를 띄우는 것으로 대체 (상세 페이지에서 에피소드를 다시 로드하도록)
                                onSeriesClick(heroCategory.toSeries())
                            }
                        }
                    )
                }
            }
            
            if (watchHistory.isNotEmpty()) {
                item { SectionTitle("시청 중인 콘텐츠") }
                item {
                    val listState = androidx.compose.runtime.saveable.rememberSaveable(
                        "watch_history", 
                        saver = androidx.compose.foundation.lazy.LazyListState.Saver
                    ) {
                        androidx.compose.foundation.lazy.LazyListState()
                    }
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(watchHistory, key = { it.id }) { history ->
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
            itemsIndexed(homeSections, key = { _, section -> section.title }) { index, section ->
                // 시청 기록이 없으면 첫 번째 섹션 위에는 간격을 주지 않고,
                // 시청 기록이 있으면 위에서 이미 간격을 주었으므로 index > 0일 때만 추가
                if (index > 0) {
                    Spacer(Modifier.height(20.dp))
                }
                
                SectionTitle(section.title)
                
                val listState = androidx.compose.runtime.saveable.rememberSaveable(
                    "home_section_${section.title}", 
                    saver = androidx.compose.foundation.lazy.LazyListState.Saver
                ) {
                    androidx.compose.foundation.lazy.LazyListState()
                }

                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(section.items, key = { it.path ?: it.name }) { item ->
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
private fun SectionTitle(title: String) {
    Text(text = title, modifier = Modifier.padding(16.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
}

@Composable
private fun MovieCard(title: String, posterPath: String?, isAnimation: Boolean, onClick: () -> Unit) {
    Card(Modifier.size(130.dp, 200.dp).padding(end = 12.dp).clickable(onClick = onClick)) {
        TmdbAsyncImage(title = title, posterPath = posterPath, modifier = Modifier.fillMaxSize(), isAnimation = isAnimation)
    }
}
