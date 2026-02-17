package org.nas.videoplayer

import coil3.PlatformContext

interface Platform {
    val name: String
    val isIos: Boolean get() = name.contains("iOS")
}

expect fun getPlatform(): Platform

// PlatformContext(안드로이드의 Context 등)를 전달받아 캐시 경로를 반환
expect fun getImageCacheDirectory(context: PlatformContext): String
