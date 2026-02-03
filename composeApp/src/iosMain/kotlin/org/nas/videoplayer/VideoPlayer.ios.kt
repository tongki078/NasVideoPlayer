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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
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
    
    // AVPlayer는 하나만 유지
    val player = remember { 
        AVPlayer().apply {
            this.automaticallyWaitsToMinimizeStalling = true
        }
    }
    
    // AVPlayerViewController 인스턴스 관리 최적화
    val playerViewController = remember {
        AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = true
            this.videoGravity = AVLayerVideoGravityResizeAspect
            // 아이패드 전체화면 및 컨트롤러 사라짐 방지 옵션
            this.allowsPictureInPicturePlayback = true
        }
    }

    // 재생 위치 업데이트 감지
    LaunchedEffect(player) {
        while (isActive) {
            val currentTime = CMTimeGetSeconds(player.currentTime())
            if (!currentTime.isNaN()) {
                currentOnPositionUpdate?.invoke((currentTime * 1000).toLong())
            }
            delay(1000)
        }
    }

    // 리소드 해제 및 생명주기 관리
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

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect

        val nsUrl = try {
            if (url.contains("%")) {
                NSURL.URLWithString(url)
            } else {
                val allowedSet = NSMutableCharacterSet.characterSetWithCharactersInString(":/?#[]@!$&'()*+,;=")
                allowedSet.formUnionWithCharacterSet(NSCharacterSet.URLQueryAllowedCharacterSet)
                allowedSet.formUnionWithCharacterSet(NSCharacterSet.URLPathAllowedCharacterSet)
                
                val nsStringUrl = url as NSString
                val encodedUrl = nsStringUrl.stringByAddingPercentEncodingWithAllowedCharacters(allowedSet)
                NSURL.URLWithString(encodedUrl ?: url)
            }
        } catch (e: Exception) {
            NSURL.URLWithString(url)
        } ?: return@LaunchedEffect

        player.pause()
        
        val headers = NSDictionary.dictionaryWithObject(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            forKey = "AVURLAssetHTTPHeaderFieldsKey" as NSCopyingProtocol
        )
        
        val assetOptions = NSDictionary.dictionaryWithObject(
            headers,
            forKey = "AVURLAssetHTTPHeaderFieldsKey" as NSCopyingProtocol
        )

        val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = assetOptions as Map<Any?, *>)
        val item = AVPlayerItem.playerItemWithAsset(asset)
        
        item.preferredForwardBufferDuration = 5.0
        
        player.appliesMediaSelectionCriteriaAutomatically = true
        val criteria = AVPlayerMediaSelectionCriteria(
            preferredLanguages = listOf("ko", "kor", "ko-KR"),
            preferredMediaCharacteristics = listOf(AVMediaCharacteristicLegible)
        )
        player.setMediaSelectionCriteria(criteria, forMediaCharacteristic = AVMediaCharacteristicLegible)
        
        player.replaceCurrentItemWithPlayerItem(item)

        if (initialPosition > 0) {
            val cmTime = CMTimeMake((initialPosition / 1000).toLong(), 1)
            player.seekToTime(cmTime)
        }

        var checkCount = 0
        while (isActive && checkCount < 100) {
            if (item.status == AVPlayerItemStatusReadyToPlay) {
                // 자막 자동 선택
                val group = asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
                if (group != null) {
                    @Suppress("UNCHECKED_CAST")
                    val options = group.options as List<AVMediaSelectionOption>
                    if (options.isNotEmpty()) {
                        val target = options.find { opt ->
                            opt.extendedLanguageTag?.contains("ko") == true || 
                            opt.displayName.contains("Korean", ignoreCase = true) ||
                            opt.displayName.contains("한국어", ignoreCase = true)
                        } ?: options.first()
                        
                        item.selectMediaOption(target, inMediaSelectionGroup = group)
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
            // 재생 시 컨트롤러가 보이지 않는 문제 해결을 위해 factory 단계에서 재설정
            playerViewController.showsPlaybackControls = true
            playerViewController
        },
        modifier = modifier,
        update = {
            // Compose UI 업데이트 시마다 컨트롤러 가시성 강제 유지
            if (!playerViewController.showsPlaybackControls) {
                playerViewController.showsPlaybackControls = true
            }
        }
    )
}
