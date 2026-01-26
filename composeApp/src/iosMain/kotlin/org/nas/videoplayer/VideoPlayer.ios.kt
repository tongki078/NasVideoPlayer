package org.nas.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.play
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(url: String, modifier: Modifier) {
    // URL이 변경될 때만 플레이어 객체를 생성합니다.
    val player = remember(url) {
        val nsUrl = NSURL.URLWithString(url)
        nsUrl?.let { AVPlayer(uRL = it) }
    }

    // 플레이어 컨트롤러를 remember로 유지하여 뷰가 다시 그려질 때 초기화되지 않게 합니다.
    val playerViewController = remember { AVPlayerViewController() }

    if (player != null) {
        UIKitView(
            factory = {
                playerViewController.player = player
                playerViewController.showsPlaybackControls = true
                // 영상이 화면 비율에 맞게 꽉 차도록 설정 (검은 화면 방지)
                playerViewController.videoGravity = AVLayerVideoGravityResizeAspect
                
                player.play()
                playerViewController.view
            },
            modifier = modifier,
            onRelease = {
                player.pause()
                playerViewController.player = null
            },
            interactive = true
        )
    }

    // 컴포저블이 화면에서 사라질 때 영상을 멈춥니다.
    DisposableEffect(Unit) {
        onDispose {
            player?.pause()
        }
    }
}
