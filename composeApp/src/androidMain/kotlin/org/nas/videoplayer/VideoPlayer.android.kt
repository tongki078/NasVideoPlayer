package org.nas.videoplayer

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import org.nas.videoplayer.data.network.NasApiClient

@OptIn(UnstableApi::class)
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
    val context = LocalContext.current
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val currentOnPositionUpdate by rememberUpdatedState(onPositionUpdate)
    val currentOnVisibilityChanged by rememberUpdatedState(onControllerVisibilityChanged)

    // URL 절대 경로 변환
    val absoluteUrl = remember(url) {
        if (url.startsWith("/")) "${NasApiClient.BASE_URL}$url" else url
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
            .setSeekForwardIncrementMs(10000) // 10초 앞으로
            .setSeekBackIncrementMs(10000) // 10초 뒤로
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoPlayer", "재생 에러: ${error.errorCodeName} - ${error.message} - URL: $absoluteUrl")
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            currentOnVideoEnded?.invoke()
                        }
                    }
                })
            }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentOnPositionUpdate?.invoke(exoPlayer.currentPosition)
            }
            delay(1000)
        }
    }

    LaunchedEffect(absoluteUrl) {
        if (absoluteUrl.isBlank()) return@LaunchedEffect
        val mediaItem = MediaItem.Builder().setUri(absoluteUrl).build()
        exoPlayer.setMediaItem(mediaItem)
        if (initialPosition > 0) exoPlayer.seekTo(initialPosition)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    
                    // AndroidX Media3 PlayerView에서 이전/다음 버튼을 완전히 숨기기 위해서는 
                    // setShowPreviousButton, setShowNextButton 만으로는 공간이 남거나 완벽히 제어되지 않을 수 있습니다.
                    // 레이아웃 파일 조작 없이 코드상에서 커스텀 레이아웃을 완전히 덮어씌우지 않는 한,
                    // 가장 확실한 방법은 버튼 자체의 Visibility를 GONE으로 만들거나 
                    // 앞서 실패했던 명령 제한 방식을 PlayerView 수준에서 강제하는 것입니다.
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    
                    // 레이아웃의 내부 뷰를 찾아서 간격과 크기를 조정하는 편법 적용
                    post {
                        try {
                            val exoPrev = findViewById<View>(androidx.media3.ui.R.id.exo_prev)
                            val exoNext = findViewById<View>(androidx.media3.ui.R.id.exo_next)
                            exoPrev?.visibility = View.GONE
                            exoNext?.visibility = View.GONE
                            
                            // 넷플릭스처럼 10초 앞/뒤, 재생 버튼 크기 키우기 및 간격 넓히기
                            val exoRew = findViewById<View>(androidx.media3.ui.R.id.exo_rew)
                            val exoFfwd = findViewById<View>(androidx.media3.ui.R.id.exo_ffwd)
                            val exoPlay = findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                            
                            val paramsRew = exoRew?.layoutParams as? android.widget.LinearLayout.LayoutParams
                            val paramsFfwd = exoFfwd?.layoutParams as? android.widget.LinearLayout.LayoutParams
                            val paramsPlay = exoPlay?.layoutParams as? android.widget.LinearLayout.LayoutParams
                            
                            val margin = 80 // 버튼 간 간격을 넓힘
                            val size = 160 // 버튼 크기를 키움 (dp 단위가 아님에 주의, 픽셀)
                            
                            paramsRew?.apply {
                                width = size; height = size
                                marginEnd = margin
                            }
                            paramsFfwd?.apply {
                                width = size; height = size
                                marginStart = margin
                            }
                            paramsPlay?.apply {
                                width = size + 40; height = size + 40 // 재생 버튼은 더 크게
                            }
                            
                            exoRew?.layoutParams = paramsRew
                            exoFfwd?.layoutParams = paramsFfwd
                            exoPlay?.layoutParams = paramsPlay
                        } catch (e: Exception) {
                            Log.e("VideoPlayer", "UI 커스터마이징 실패: ${e.message}")
                        }
                    }

                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        currentOnVisibilityChanged?.invoke(visibility == View.VISIBLE)
                    })
                    showController()
                }
            },
            update = { playerView ->
                playerView.setFullscreenButtonClickListener {
                    onFullscreenClick?.invoke()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
