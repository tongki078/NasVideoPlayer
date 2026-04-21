package org.nas.videoplayer.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val name: String,
    val path: String? = null,
    @SerialName("movies")
    val movies: List<Movie> = emptyList(),
    val genreIds: List<Int>? = null,
    val genreNames: List<String>? = null,
    val posterPath: String? = null,
    val year: String? = null,
    val overview: String? = null,
    val rating: String? = null,
    val seasonCount: Int? = null,
    val director: String? = null,
    val actors: List<Actor>? = null,
    val tmdbId: String? = null,
    val failed: Int = 0,
    val seasons: Map<String, List<Movie>>? = null,
    val chosung: String? = null,
    val ai_tags: String? = null, // JSON string of tags
    val updated_at: String? = null
)

@Serializable
data class Actor(
    val name: String,
    val profile: String? = null,
    val role: String? = null
)

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val videoUrl: String,
    val duration: String? = null,
    val overview: String? = null,
    val air_date: String? = null,
    val season_number: Int? = null,
    val episode_number: Int? = null,
    val runtime: Int? = null,
    val position: Float? = null,
    val category: String? = null,
    val tag: String? = null
)

@Serializable
data class HomeSection(
    val title: String,
    val items: List<Category>,
    val is_full_list: Boolean = false
)

data class Series(
    val title: String,
    val episodes: List<Movie>,
    val thumbnailUrl: String? = null,
    val posterPath: String? = null,
    val overview: String? = null,
    val year: String? = null,
    val fullPath: String? = null,
    val genreNames: List<String>? = null,
    val director: String? = null,
    val actors: List<Actor>? = null,
    val rating: String? = null,
    val tmdbId: String? = null,
    val seasons: Map<String, List<Movie>>? = null,
    val aiTags: List<String> = emptyList()
)

data class Season(val name: String, val episodes: List<Movie>, val seasonNumber: Int)

data class SeriesDetailState(
    val detail: Category? = null,
    val seasons: List<Season> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSeasonIndex: Int = 0
)

@Serializable
data class SubtitleInfo(
    val external: List<ExternalSubtitle> = emptyList(),
    val embedded: List<EmbeddedSubtitle> = emptyList(),
    val extraction_triggered: Boolean = false
)

@Serializable
data class ExternalSubtitle(
    val name: String,
    val path: String
)

@Serializable
data class EmbeddedSubtitle(
    val index: Int,
    val codec_name: String? = null,
    val tags: Map<String, String>? = null
)

enum class Screen { 
    HOME, SEARCH, ON_AIR, ANIMATIONS, MOVIES, FOREIGN_TV, KOREAN_TV, LATEST,
    MUSIC_FOREIGN, MUSIC_JAPAN, MUSIC_CLASSIC, MUSIC_DSD, MUSIC_OST
}
