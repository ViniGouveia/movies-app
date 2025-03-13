package dev.vinigouveia.moviesapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.vinigouveia.moviesapp.playback.Init
import dev.vinigouveia.moviesapp.playback.PlayerViewModel
import dev.vinigouveia.moviesapp.playback.SetAudioTrack
import dev.vinigouveia.moviesapp.playback.SetSubtitleTrack
import dev.vinigouveia.moviesapp.playback.SetVideoTrack
import dev.vinigouveia.moviesapp.playback.Start
import dev.vinigouveia.moviesapp.playback.Stop
import dev.vinigouveia.moviesapp.playback.TrackSelector
import dev.vinigouveia.moviesapp.playback.VideoPlayer
import dev.vinigouveia.moviesapp.ui.theme.MoviesAppTheme

class PlaybackActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.buildFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val streamUrl = intent.getStringExtra(KEY_STREAM_URL) ?: throw IllegalArgumentException()
        val adTagUrl = intent.getStringExtra(KEY_AD_TAG_URL)
        viewModel.handleAction(Init(streamUrl, adTagUrl))

        setContent {
            val playerUiModel by viewModel.playerUiModel.collectAsState()

            MoviesAppTheme {
                Surface {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPlayer(playerViewModel = viewModel)
                    }

                    if (playerUiModel.isTrackSelectorVisible) {
                        playerUiModel.trackSelectionUiModel?.let { trackSelectionUiModel ->
                            TrackSelector(
                                trackSelectionUiModel = trackSelectionUiModel,
                                onVideoTrackSelected = {
                                    viewModel.handleAction(SetVideoTrack(it))
                                },
                                onAudioTrackSelected = {
                                    viewModel.handleAction(SetAudioTrack(it))
                                },
                                onSubtitleTrackSelected = {
                                    viewModel.handleAction(SetSubtitleTrack(it))
                                },
                                onDismiss = {
                                    viewModel.hideTrackSelector()
                                }
                            )
                        }
                    }
                }

                LaunchedEffect(playerUiModel.isFullScreen) {
                    val window = this@PlaybackActivity.window
                    val windowInsetsController =
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }

                    if (playerUiModel.isFullScreen) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        @SuppressLint("SourceLockedOrientationActivity")
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.handleAction(Start())
    }

    override fun onStop() {
        super.onStop()
        viewModel.handleAction(Stop)
    }

    companion object {
        private const val KEY_STREAM_URL = "STREAM_URL_KEY"
        private const val KEY_AD_TAG_URL = "AD_TAG_URL_KEY"

        fun buildIntent(context: Context, streamUrl: String, adTagUrl: String?): Intent =
            Intent(context, PlaybackActivity::class.java).apply {
                putExtra(KEY_STREAM_URL, streamUrl)
                putExtra(KEY_AD_TAG_URL, adTagUrl)
            }
    }
}
