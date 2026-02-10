package org.nas.videoplayer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val name: String,
    val path: String? = null,
    val movies: List<Movie> = emptyList(),
    val genreIds: List<Int>? = null,
    val posterPath: String? = null,
    val year: String? = null,
    val overview: String? = null,
    val rating: String? = null,
    val seasonCount: Int? = null,
    val failed: Boolean = false
)

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val videoUrl: String,
    val duration: String? = null
)

data class Series(
    val title: String,
    val episodes: List<Movie>,
    val thumbnailUrl: String? = null,
    val posterPath: String? = null,
    val overview: String? = null,
    val year: String? = null,
    val fullPath: String? = null
)

enum class Screen { HOME, SEARCH, ON_AIR, ANIMATIONS, MOVIES, FOREIGN_TV, KOREAN_TV, LATEST }
