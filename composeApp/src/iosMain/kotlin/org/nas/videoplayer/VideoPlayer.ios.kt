package org.nas.videoplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import platform.AVFoundation.*
import platform.AVKit.*
import platform.Foundation.*
import platform.UIKit.*
import platform.CoreMedia.*
import kotlinx.cinterop.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.nas.videoplayer.data.network.NasApiClient

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    initialPosition: Long,
    onPositionUpdate: ((Long) -> Unit)?,
    onControllerVisibilityChanged: ((Boolean) -> Unit)?,
    onFullscreenClick: (() -> Unit)?,
    onVideoEnded: (() -> Unit)?
) {
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val currentOnPositionUpdate by rememberUpdatedState(onPositionUpdate)
    
    val absoluteUrl = remember(url) {
        if (url.startsWith("/")) "${NasApiClient.BASE_URL}$url" else url
    }

    val player = remember { AVPlayer() }
    
    val playerViewController = remember {
        val controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = true
        controller.videoGravity = AVLayerVideoGravityResizeAspect
        controller
    }

    LaunchedEffect(player) {
        while (isActive) {
            val currentTime = CMTimeGetSeconds(player.currentTime())
            if (!currentTime.isNaN()) {
                currentOnPositionUpdate?.invoke((currentTime * 1000).toLong())
            }
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = null
        ) { _ ->
            currentOnVideoEnded?.invoke()
        }
        
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    LaunchedEffect(absoluteUrl) {
        if (absoluteUrl.isBlank()) return@LaunchedEffect

        val nsUrl = NSURL.URLWithString(absoluteUrl) ?: return@LaunchedEffect
        player.pause()
        val item = AVPlayerItem.playerItemWithURL(nsUrl)
        player.replaceCurrentItemWithPlayerItem(item)

        if (initialPosition > 0) {
            val cmTime = CMTimeMake((initialPosition / 1000).toLong(), 1)
            player.seekToTime(cmTime)
        }
        player.play()
    }

    UIKitViewController(
        factory = {
            playerViewController.showsPlaybackControls = true
            playerViewController
        },
        modifier = modifier,
        update = {
            playerViewController.showsPlaybackControls = true
        }
    )
}
