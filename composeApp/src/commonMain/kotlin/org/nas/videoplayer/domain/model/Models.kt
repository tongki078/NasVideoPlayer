package org.nas.videoplayer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val name: String,
    val path: String? = null,
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
    val failed: Boolean = false
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
    val episode_number: Int? = null
)

@Serializable
data class HomeSection(
    val title: String,
    val items: List<Category>
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
    val tmdbId: String? = null
)

enum class Screen { HOME, SEARCH, ON_AIR, ANIMATIONS, MOVIES, FOREIGN_TV, KOREAN_TV, LATEST }
