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
import org.nas.videoplayer.domain.model.SubtitleInfo

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    initialPosition: Long,
    subtitleInfo: SubtitleInfo?,
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
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
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
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    
                    post {
                        try {
                            val exoPrev = findViewById<View>(androidx.media3.ui.R.id.exo_prev)
                            val exoNext = findViewById<View>(androidx.media3.ui.R.id.exo_next)
                            exoPrev?.visibility = View.GONE
                            exoNext?.visibility = View.GONE
                            
                            val exoRew = findViewById<View>(androidx.media3.ui.R.id.exo_rew)
                            val exoFfwd = findViewById<View>(androidx.media3.ui.R.id.exo_ffwd)
                            val exoPlay = findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                            
                            val paramsRew = exoRew?.layoutParams as? android.widget.LinearLayout.LayoutParams
                            val paramsFfwd = exoFfwd?.layoutParams as? android.widget.LinearLayout.LayoutParams
                            val paramsPlay = exoPlay?.layoutParams as? android.widget.LinearLayout.LayoutParams
                            
                            val margin = 80 
                            val size = 160 
                            
                            paramsRew?.apply {
                                width = size; height = size
                                marginEnd = margin
                            }
                            paramsFfwd?.apply {
                                width = size; height = size
                                marginStart = margin
                            }
                            paramsPlay?.apply {
                                width = size + 40; height = size + 40
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
