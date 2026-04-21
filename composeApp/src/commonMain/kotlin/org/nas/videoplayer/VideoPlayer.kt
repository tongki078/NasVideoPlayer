package org.nas.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.nas.videoplayer.domain.model.SubtitleInfo

@Composable
expect fun VideoPlayer(
    url: String, 
    modifier: Modifier = Modifier,
    initialPosition: Long = 0L,
    subtitleInfo: SubtitleInfo? = null,
    onPositionUpdate: ((Long) -> Unit)? = null,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onFullscreenClick: (() -> Unit)? = null,
    onVideoEnded: (() -> Unit)? = null
)
