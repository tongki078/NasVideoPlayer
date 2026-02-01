package org.nas.videoplayer.data

import org.nas.videoplayer.db.AppDatabase
import org.nas.videoplayer.db.Search_history
import org.nas.videoplayer.db.Watch_history
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.squareup.sqldelight.db.SqlDriver

class SearchHistoryDataSource(private val db: AppDatabase) {
    fun getRecentQueries(): Flow<List<Search_history>> = flow {
        emit(db.searchHistoryQueries.selectAll().executeAsList())
    }
    fun insertQuery(query: String, timestamp: Long) = db.searchHistoryQueries.insertHistory(query, timestamp)
    fun deleteQuery(query: String) = db.searchHistoryQueries.deleteQuery(query)
    fun clearAll() = db.searchHistoryQueries.deleteAll()
}

class WatchHistoryDataSource(private val db: AppDatabase) {
    fun getWatchHistory(): Flow<List<Watch_history>> = flow {
        emit(db.watchHistoryQueries.selectAll().executeAsList())
    }
    fun insertWatchHistory(
        id: String, title: String, videoUrl: String, thumbnailUrl: String?, timestamp: Long, screenType: String, pathStackJson: String
    ) = db.watchHistoryQueries.insertHistory(id, title, videoUrl, thumbnailUrl, timestamp, screenType, pathStackJson)
    fun deleteWatchHistory(id: String) = db.watchHistoryQueries.deleteHistory(id)
}

expect fun currentTimeMillis(): Long

fun Watch_history.toData(): WatchHistory = WatchHistory(
    id = this.id,
    title = this.title,
    videoUrl = this.videoUrl,
    thumbnailUrl = this.thumbnailUrl,
    timestamp = this.timestamp,
    screenType = this.screenType,
    pathStackJson = this.pathStackJson
)

fun Search_history.toData(): SearchHistory = SearchHistory(
    query = this.query,
    timestamp = this.timestamp
)
