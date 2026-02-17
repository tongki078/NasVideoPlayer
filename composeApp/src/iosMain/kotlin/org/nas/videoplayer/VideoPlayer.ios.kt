package org.nas.videoplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.AVFoundation.*
import platform.AVKit.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
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
    
    // Create AVPlayerViewController inside remember to persist it across recompositions
    val playerViewController = remember { AVPlayerViewController() }

    LaunchedEffect(player) {
        while (isActive) {
            val currentCValue = player.currentTime()
            val currentTime = CMTimeGetSeconds(currentCValue)
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
            queue = NSOperationQueue.mainQueue
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
        val item = AVPlayerItem.playerItemWithURL(nsUrl)
        player.replaceCurrentItemWithPlayerItem(item)

        if (initialPosition > 0) {
            // Use CMTimeMake for precise seeking. initialPosition is in milliseconds.
            // CMTimeMake(value, timescale) -> value / timescale = seconds
            val cmTime = CMTimeMake(initialPosition, 1000)
            player.seekToTime(cmTime)
        }
        player.play()
    }

    @Suppress("DEPRECATION")
    UIKitViewController(
        factory = {
            playerViewController.apply {
                this.player = player
                this.showsPlaybackControls = true
                this.videoGravity = AVLayerVideoGravityResizeAspect
            }
        },
        modifier = modifier,
        update = {
            // No update needed as the player is managed internally
        }
    )
}
