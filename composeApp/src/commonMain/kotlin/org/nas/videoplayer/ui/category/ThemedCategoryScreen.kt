package org.nas.videoplayer.ui.category

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.model.Movie
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.domain.model.HomeSection
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.ui.common.MovieRow
import org.nas.videoplayer.ui.common.HeroSection
import org.nas.videoplayer.ui.common.TmdbAsyncImage
import org.nas.videoplayer.cleanTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedCategoryScreen(
    categoryName: String,
    repository: VideoRepository,
    selectedMode: Int,
    onModeChange: (Int) -> Unit,
    lazyListState: LazyListState = rememberLazyListState(),
    onSeriesClick: (Series) -> Unit,
    onPlayClick: (Movie, List<Movie>) -> Unit
) {
    val categoryCode = when (categoryName) {
        "방송중" -> "air"
        "애니", "애니메이션" -> "animations_all"
        "영화" -> "movies"
        "외국 TV", "외국TV" -> "foreigntv"
        "국내 TV", "국내TV" -> "koreantv"
        "외국음악" -> "music_foreign"
        "일본음악" -> "music_japan"
        "클래식" -> "music_classic"
        "DSD" -> "music_dsd"
        "OST" -> "music_ost"
        else -> "movies"
    }

    val selectedKeyword = when (categoryName) {
        "방송중" -> if (selectedMode == 0) "라프텔 애니메이션" else "드라마"
        "애니", "애니메이션" -> if (selectedMode == 0) "라프텔" else "시리즈"
        "영화" -> when(selectedMode) { 0 -> "최신"; 1 -> "UHD"; else -> "제목" }
        "외국 TV", "외국TV" -> when(selectedMode) { 0 -> "중국 드라마"; 1 -> "일본 드라마"; 2 -> "미국 드라마"; 3 -> "기타"; else -> "다큐" }
        "국내 TV", "국내TV" -> when(selectedMode) { 0 -> "드라마"; 1 -> "시트콤"; 2 -> "교양"; 3 -> "다큐"; else -> "예능" }
        else -> null
    }
    
    var expanded by remember { mutableStateOf(false) }
    var themedSections by remember(selectedMode, categoryName) { mutableStateOf<List<HomeSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedChosung by remember { mutableStateOf("전체") }

    val heroCategory = remember(themedSections) { 
        themedSections.filter { !it.is_full_list }.flatMap { it.items }.takeIf { it.isNotEmpty() }?.random()
    }

    LaunchedEffect(selectedMode, categoryName) {
        isLoading = true
        selectedChosung = "전체" // 카테고리 변경 시 초성 필터 초기화
        try {
            themedSections = repository.getCategorySections(categoryCode, selectedKeyword)
        } catch (e: Exception) {
            themedSections = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (categoryCode in listOf("air", "animations_all", "movies", "foreigntv", "koreantv")) {
            ExposedCategoryDropdown(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                selectedText = selectedKeyword ?: categoryName
            ) {
                // Dropdown menu items remain the same...
                when (categoryName) {
                    "방송중" -> {
                        DropdownMenuItem(text = { Text("라프텔 애니메이션") }, onClick = { onModeChange(0); expanded = false })
                        DropdownMenuItem(text = { Text("드라마") }, onClick = { onModeChange(1); expanded = false })
                    }
                    "애니", "애니메이션" -> {
                        DropdownMenuItem(text = { Text("라프텔") }, onClick = { onModeChange(0); expanded = false })
                        DropdownMenuItem(text = { Text("시리즈") }, onClick = { onModeChange(1); expanded = false })
                    }
                    "영화" -> {
                        DropdownMenuItem(text = { Text("최신") }, onClick = { onModeChange(0); expanded = false })
                        DropdownMenuItem(text = { Text("UHD") }, onClick = { onModeChange(1); expanded = false })
                        DropdownMenuItem(text = { Text("제목") }, onClick = { onModeChange(2); expanded = false })
                    }
                    "외국 TV", "외국TV" -> {
                        DropdownMenuItem(text = { Text("중국 드라마") }, onClick = { onModeChange(0); expanded = false })
                        DropdownMenuItem(text = { Text("일본 드라마") }, onClick = { onModeChange(1); expanded = false })
                        DropdownMenuItem(text = { Text("미국 드라마") }, onClick = { onModeChange(2); expanded = false })
                        DropdownMenuItem(text = { Text("기타") }, onClick = { onModeChange(3); expanded = false })
                        DropdownMenuItem(text = { Text("다큐") }, onClick = { onModeChange(4); expanded = false })
                    }
                    "국내 TV", "국내TV" -> {
                        DropdownMenuItem(text = { Text("드라마") }, onClick = { onModeChange(0); expanded = false })
                        DropdownMenuItem(text = { Text("시트콤") }, onClick = { onModeChange(1); expanded = false })
                        DropdownMenuItem(text = { Text("교양") }, onClick = { onModeChange(2); expanded = false })
                        DropdownMenuItem(text = { Text("다큐멘터리") }, onClick = { onModeChange(3); expanded = false })
                        DropdownMenuItem(text = { Text("예능") }, onClick = { onModeChange(4); expanded = false })
                    }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else if (themedSections.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("불러올 영상이 없습니다.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), 
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (heroCategory != null) {
                    item {
                        HeroSection(
                            category = heroCategory,
                            onInfoClick = { onSeriesClick(heroCategory.toSeries()) },
                            onPlayClick = {
                                val firstEp = heroCategory.movies.firstOrNull()
                                if (firstEp != null) onPlayClick(firstEp, heroCategory.movies)
                                else onSeriesClick(heroCategory.toSeries())
                            }
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
                
                itemsIndexed(themedSections) { _, section ->
                    if (section.is_full_list) {
                        // 전체 목록 섹션: 초성 필터 UI와 가로 리스트
                        FullListWithFilter(
                            section = section,
                            selectedChosung = selectedChosung,
                            onChosungClick = { selectedChosung = it },
                            onSeriesClick = onSeriesClick
                        )
                    } else if (section.items.isNotEmpty()) {
                        MovieRow(
                            title = section.title,
                            seriesList = section.items.map { it.toSeries() },
                            onSeriesClick = onSeriesClick
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FullListWithFilter(
    section: HomeSection,
    selectedChosung: String,
    onChosungClick: (String) -> Unit,
    onSeriesClick: (Series) -> Unit
) {
    // 서버 소스의 초성 로직에 대응하는 필터 목록
    val chosungs = listOf("전체", "ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ", "A-Z", "0-9")
    
    // 선택된 초성에 따른 필터링
    val filteredItems = remember(section.items, selectedChosung) {
        if (selectedChosung == "전체") section.items
        else section.items.filter { it.chosung == selectedChosung }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            Spacer(Modifier.width(12.dp))
            
            // 초성 필터 칩 리스트
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(chosungs) { chosung ->
                    ChosungChip(
                        text = chosung,
                        isSelected = selectedChosung == chosung,
                        onClick = { onChosungClick(chosung) }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // 가로 스크롤 리스트
        if (filteredItems.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text("해당하는 항목이 없습니다.", color = Color.DarkGray, fontSize = 14.sp)
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems, key = { it.path ?: it.name }) { item ->
                    FullListItem(item, onSeriesClick)
                }
            }
        }
    }
}

@Composable
private fun ChosungChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Color.Red else Color.DarkGray.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = text,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun FullListItem(item: Category, onSeriesClick: (Series) -> Unit) {
    Column(
        modifier = Modifier.width(110.dp).clickable { onSeriesClick(item.toSeries()) }
    ) {
        Card(
            modifier = Modifier.height(160.dp).fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        ) {
            TmdbAsyncImage(
                title = item.name,
                posterPath = item.posterPath,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.name.cleanTitle(includeYear = false),
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun Category.toSeries() = Series(
    title = this.name,
    episodes = this.movies,
    posterPath = this.posterPath,
    overview = this.overview,
    year = this.year,
    fullPath = this.path,
    genreNames = this.genreNames,
    director = this.director,
    actors = this.actors,
    rating = this.rating,
    tmdbId = this.tmdbId,
    seasons = this.seasons
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExposedCategoryDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedText: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            Surface(
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                color = Color.DarkGray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                content = content
            )
        }
    }
}
