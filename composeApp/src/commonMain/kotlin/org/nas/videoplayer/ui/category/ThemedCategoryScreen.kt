package org.nas.videoplayer.ui.category

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import org.nas.videoplayer.domain.model.Series
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.domain.model.HomeSection
import org.nas.videoplayer.domain.repository.VideoRepository
import org.nas.videoplayer.ui.common.MovieRow
import org.nas.videoplayer.ui.common.shimmerBrush
import org.nas.videoplayer.cleanTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedCategoryScreen(
    categoryName: String,
    repository: VideoRepository,
    selectedMode: Int,
    onModeChange: (Int) -> Unit,
    lazyListState: LazyListState = rememberLazyListState(),
    onSeriesClick: (Series) -> Unit
) {
    // UI의 탭 이름(애니, 외국 TV 등)을 서버 코드의 categoryCode와 정확히 매칭
    val categoryCode = when (categoryName) {
        "방송중" -> "air"
        "애니", "애니메이션" -> "animations_all"
        "영화" -> "movies"
        "외국 TV", "외국TV" -> "foreigntv"
        "국내 TV", "국내TV" -> "koreantv"
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

    LaunchedEffect(selectedMode, categoryName) {
        isLoading = true
        try {
            themedSections = repository.getCategorySections(categoryCode, selectedKeyword)
        } catch (e: Exception) {
            themedSections = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ExposedCategoryDropdown(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            selectedText = selectedKeyword ?: categoryName
        ) {
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

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else if (themedSections.isEmpty() || themedSections.all { it.items.isEmpty() }) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("불러올 영상이 없습니다.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), 
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(themedSections) { index, section ->
                    if (section.items.isNotEmpty()) {
                        if (index > 0) {
                            Spacer(Modifier.height(20.dp))
                        }
                        MovieRow(
                            title = section.title,
                            seriesList = section.items.flatMap { it.toSeriesList() },
                            onSeriesClick = onSeriesClick
                        )
                    }
                }
            }
        }
    }
}

private fun Category.toSeriesList(): List<Series> {
    if (this.movies.isEmpty()) {
        return listOf(Series(
            title = this.name.cleanTitle(includeYear = false),
            episodes = emptyList(),
            posterPath = this.posterPath,
            overview = this.overview,
            year = this.year,
            fullPath = this.path,
            rating = this.rating
        ))
    }
    return this.movies.groupBy { it.title.cleanTitle(includeYear = false) }
        .map { (title, eps) -> 
            Series(
                title = title, 
                episodes = eps,
                posterPath = this.posterPath,
                overview = this.overview,
                year = this.year,
                fullPath = this.path,
                rating = this.rating
            ) 
        }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExposedCategoryDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedText: String,
    content: @Composable ColumnScope.() -> Unit
) {
     Box(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                label = { Text("카테고리 선택", color = Color.Gray) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.Red,
                    unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                content = content
            )
        }
    }
}
