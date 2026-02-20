package org.nas.videoplayer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nas.videoplayer.domain.model.Series

@Composable
fun MovieRow(
    title: String,
    seriesList: List<Series>,
    onSeriesClick: (Series) -> Unit,
    // Add an optional unique key to preserve scroll state across compositions if needed
    rowKey: String = title
) {
    if (seriesList.isEmpty()) return
    
    // rememberLazyListState can maintain its state across simple recompositions
    // but usually in a LazyColumn, we might need a custom key or saveable state
    // We use a keyed remember here to associate the state with this specific row
    val listState = androidx.compose.runtime.saveable.rememberSaveable(
        rowKey, 
        saver = androidx.compose.foundation.lazy.LazyListState.Saver
    ) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Use path as key since titles might be duplicated after cleaning (e.g. "Idol I" error)
            items(seriesList, key = { it.fullPath ?: it.title }) { series ->
                Card(
                    modifier = Modifier
                        .width(130.dp)
                        .height(190.dp)
                        .clickable { onSeriesClick(series) },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    TmdbAsyncImage(
                        title = series.title, 
                        posterPath = series.posterPath,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
