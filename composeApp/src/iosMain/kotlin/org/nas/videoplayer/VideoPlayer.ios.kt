package org.nas.videoplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import androidx.compose.ui.interop.LocalUIViewController
import platform.AVFoundation.*
import platform.AVKit.*
import platform.Foundation.*
import platform.UIKit.*
import platform.CoreMedia.*
import kotlinx.cinterop.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    onFullscreenClick: (() -> Unit)?
) {
    val viewController = LocalUIViewController.current
    val player = remember { 
        AVPlayer().apply {
            this.automaticallyWaitsToMinimizeStalling = true
        }
    }
    
    val playerViewController = remember {
        AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = true
            this.videoGravity = AVLayerVideoGravityResizeAspect
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect

        println("🚀 [iOS_DEBUG] START LOADING: $url")

        val nsUrl = try {
            if (url.contains("%")) {
                NSURL.URLWithString(url)
            } else {
                val nsString = url as Any as NSString
                val allowedSet = NSMutableCharacterSet.characterSetWithCharactersInString(":/?#[]@!$&'()*+,;=")
                allowedSet.formUnionWithCharacterSet(NSCharacterSet.URLQueryAllowedCharacterSet)
                allowedSet.formUnionWithCharacterSet(NSCharacterSet.URLPathAllowedCharacterSet)
                val encodedUrl = nsString.stringByAddingPercentEncodingWithAllowedCharacters(allowedSet)
                NSURL.URLWithString(encodedUrl ?: url)
            }
        } catch (e: Exception) {
            NSURL.URLWithString(url)
        } ?: return@LaunchedEffect

        player.pause()
        
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        )
        
        val assetOptions = mapOf(
            "AVURLAssetHTTPHeaderFieldsKey" to headers
        )

        val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = assetOptions as Map<Any?, *>)
        val item = AVPlayerItem.playerItemWithAsset(asset)
        
        item.preferredForwardBufferDuration = 5.0
        
        // 시스템 자동 자막 선택 및 동기화 기준 설정
        player.appliesMediaSelectionCriteriaAutomatically = true
        val criteria = AVPlayerMediaSelectionCriteria(
            preferredLanguages = listOf("ko", "kor", "ko-KR"),
            preferredMediaCharacteristics = listOf(AVMediaCharacteristicLegible)
        )
        player.setMediaSelectionCriteria(criteria, forMediaCharacteristic = AVMediaCharacteristicLegible)
        
        player.replaceCurrentItemWithPlayerItem(item)

        var checkCount = 0
        while (isActive && checkCount < 100) {
            if (item.status == AVPlayerItemStatusReadyToPlay) {
                println("✅ [iOS_DEBUG] Status: ReadyToPlay")
                
                val group = asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
                if (group != null) {
                    val options = group.options as List<AVMediaSelectionOption>
                    if (options.isNotEmpty()) {
                        val target = options.find { 
                            it.extendedLanguageTag?.contains("ko") == true || 
                            it.displayName.contains("Korean", ignoreCase = true) ||
                            it.displayName.contains("한국어", ignoreCase = true)
                        } ?: options.first()
                        
                        item.selectMediaOption(target, inMediaSelectionGroup = group)
                        println("🎬 [iOS_DEBUG] Subtitle Selected: ${target.displayName}")
                    }
                }

                delay(1000)
                player.play()
                break
            }
            delay(500)
            checkCount++
        }
    }

    UIKitViewController(
        factory = {
            if (playerViewController.parentViewController == null) {
                viewController.addChildViewController(playerViewController)
                playerViewController.didMoveToParentViewController(viewController)
            }
            playerViewController
        },
        modifier = modifier
    )
}
