package dev.vinigouveia.moviesapp.playback

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TimeBar
import dev.vinigouveia.moviesapp.CoverImage
import dev.vinigouveia.moviesapp.R
import dev.vinigouveia.moviesapp.Utils

@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel
) {
    val playerUiModel by playerViewModel.playerUiModel.collectAsState()

    Box {
        AndroidEmbeddedExternalSurface(
            modifier = modifier
                .aspectRatio(playerUiModel.videoAspectRatio)
                .clickable {
                    playerViewModel.showPlayerControl()
                }
        ) {
            onSurface { surface, _, _ ->
                playerViewModel.handleAction(AttachSurface(surface))
                surface.onDestroyed {
                    playerViewModel.handleAction(DetachSurface)
                }
            }
        }

        VideoOverlay(
            modifier = Modifier.matchParentSize(),
            playerViewModel = playerViewModel,
            onCollapseClick = {
                playerViewModel.exitFullScreen()
            },
            onExpandClick = {
                playerViewModel.enterFullScreen()
            },
            onControlsClicked = {
                playerViewModel.hidePlayerControl()
            },
            onSettingsClicked = {
                playerViewModel.showTrackSelector()
            },
            onAction = { action ->
                playerViewModel.handleAction(action)
            }
        )
    }
}

@Composable
fun VideoOverlay(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel,
    onCollapseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onControlsClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onAction: (Action) -> Unit
) {
    val playerUiModel by playerViewModel.playerUiModel.collectAsState()

    Box(
        modifier = modifier
    ) {
        playerUiModel.placeHolderImageResourceId?.let {
            CoverImage(
                modifier = Modifier.matchParentSize(),
                imageResId = it
            )
        }

        if (playerUiModel.playerControlsVisible) {
            PlaybackControls(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onControlsClicked),
                playerViewModel = playerViewModel,
                isFullScreen = playerUiModel.isFullScreen,
                onCollapseClick = onCollapseClick,
                onExpandClick = onExpandClick,
                onSettingsClicked = onSettingsClicked,
                onAction = onAction
            )
        }

        Subtitles(
            modifier = Modifier.matchParentSize(),
            cues = playerUiModel.currentSubtitles
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Subtitles(
    modifier: Modifier = Modifier,
    cues: List<Cue>
) {
    AndroidView(
        modifier = modifier.padding(bottom = 32.dp),
        factory = { context ->
            SubtitleView(context)
        },
        update = { subtitleView ->
            subtitleView.setCues(cues)
        }
    )
}

@Composable
fun PlaybackControls(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel,
    isFullScreen: Boolean,
    onCollapseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onSettingsClicked: () -> Unit,
    onAction: (Action) -> Unit
) {
    val playerUiModel by playerViewModel.playerUiModel.collectAsState()

    Box(
        modifier = modifier
            .background(color = Color(0xA0000000))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (playerUiModel.adUiModel?.currentAdBreak == null) {
                PlaybackButton(
                    resourceId = R.drawable.settings,
                    contentDescription = "Open track selector"
                ) {
                    onSettingsClicked()
                }
            }

            if (isFullScreen) {
                PlaybackButton(
                    resourceId = R.drawable.collapse,
                    contentDescription = "Exit full screen"
                ) {
                    onCollapseClick()
                }
            } else {
                PlaybackButton(
                    resourceId = R.drawable.expand,
                    contentDescription = "Enter full screen"
                ) {
                    onExpandClick()
                }
            }
        }

        if (playerUiModel.adUiModel?.currentAdBreak == null) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (playerUiModel.playbackState.isReady()) {
                    PlaybackButton(
                        R.drawable.startover,
                        contentDescription = "Start over"
                    ) {
                        onAction(Seek(0))
                    }
                    PlaybackButton(
                        resourceId = R.drawable.fast_rewind,
                        contentDescription = "Rewind"
                    ) {
                        onAction(Rewind(15_000))
                    }
                }

                when (playerUiModel.playbackState) {
                    PlaybackState.PLAYING -> {
                        PlaybackButton(
                            resourceId = R.drawable.pause,
                            contentDescription = "Pause"
                        ) {
                            onAction(Pause)
                        }
                    }

                    PlaybackState.PAUSED -> {
                        PlaybackButton(
                            resourceId = R.drawable.play,
                            contentDescription = "Play"
                        ) {
                            onAction(Resume)
                        }
                    }

                    PlaybackState.IDLE -> {
                        PlaybackButton(
                            resourceId = R.drawable.play,
                            contentDescription = "Play"
                        ) {
                            onAction(Start())
                        }
                    }

                    PlaybackState.BUFFERING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White
                        )
                    }

                    PlaybackState.COMPLETED -> {
                        PlaybackButton(
                            resourceId = R.drawable.replay,
                            contentDescription = "Replay"
                        ) {
                            onAction(Start(0))
                        }
                    }

                    PlaybackState.ERROR -> {
                        PlaybackButton(
                            resourceId = R.drawable.error,
                            contentDescription = "Error"
                        )
                        PlaybackButton(
                            resourceId = R.drawable.replay,
                            contentDescription = "Retry"
                        ) {
                            onAction(Start(playerUiModel.timelineUiModel?.currentPositionInMs))
                        }
                    }
                }

                if (playerUiModel.playbackState.isReady()) {
                    PlaybackButton(
                        resourceId = R.drawable.fast_forward,
                        contentDescription = "Fast forward"
                    ) {
                        onAction(FastForward(15_000))
                    }
                }
            }
        }

        playerUiModel.timelineUiModel?.let {
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                PlaybackPosition(
                    contentPositionInMs = it.currentPositionInMs,
                    contentDurationInMs = it.durationInMs,
                    adBreak = playerUiModel.adUiModel?.currentAdBreak
                )

                if (playerUiModel.adUiModel?.currentAdBreak == null) {
                    TimeBar(
                        positionInMs = it.currentPositionInMs,
                        durationInMs = it.durationInMs,
                        bufferedPositionInMs = it.bufferedPositionInMs,
                        adGroups = playerUiModel.adUiModel?.adGroups ?: emptyList()
                    ) {
                        onAction(Seek(it))
                    }
                }
            }
        }
    }
}

private enum class TrackState {
    VIDEO,
    AUDIO,
    SUBTITLE,
    LIST
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelector(
    trackSelectionUiModel: TrackSelectionUiModel,
    onVideoTrackSelected: (VideoTrack) -> Unit,
    onAudioTrackSelected: (AudioTrack) -> Unit,
    onSubtitleTrackSelected: (SubtitleTrack) -> Unit,
    onDismiss: () -> Unit
) {
    var currentState by remember { mutableStateOf(TrackState.LIST) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        when (currentState) {
            TrackState.LIST -> {
                Column {
                    Text(
                        text = "Video Tracks",
                        modifier = Modifier
                            .clickable { currentState = TrackState.VIDEO }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Audio Tracks",
                        modifier = Modifier
                            .clickable { currentState = TrackState.AUDIO }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Subtitle Tracks",
                        modifier = Modifier
                            .clickable { currentState = TrackState.SUBTITLE }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            TrackState.VIDEO -> {
                LazyColumn {
                    items(trackSelectionUiModel.videoTracks) { videoTrack ->
                        Text(
                            text = videoTrack.displayName,
                            modifier = Modifier
                                .clickable {
                                    onVideoTrackSelected(videoTrack)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (videoTrack === trackSelectionUiModel.selectedVideoTrack) Color.Yellow else Color.White
                        )
                    }
                }
            }

            TrackState.AUDIO -> {
                LazyColumn {
                    items(trackSelectionUiModel.audioTracks) { audioTrack ->
                        Text(
                            text = audioTrack.displayName,
                            modifier = Modifier
                                .clickable {
                                    onAudioTrackSelected(audioTrack)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (audioTrack === trackSelectionUiModel.selectedAudioTrack) Color.Yellow else Color.White
                        )
                    }
                }
            }

            TrackState.SUBTITLE -> {
                LazyColumn {
                    items(trackSelectionUiModel.subtitleTracks) { subtitleTrack ->
                        Text(
                            text = subtitleTrack.displayName,
                            modifier = Modifier
                                .clickable {
                                    onSubtitleTrackSelected(subtitleTrack)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (subtitleTrack === trackSelectionUiModel.selectedSubtitleTrack) Color.Yellow else Color.White
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun PlaybackPosition(
    contentPositionInMs: Long,
    contentDurationInMs: Long,
    adBreak: AdBreak?
) {
    if (adBreak == null) {
        val positionString = Utils.formatMsToString(contentPositionInMs)
        val durationString = Utils.formatMsToString(contentDurationInMs)

        Text(
            text = "$positionString / $durationString",
            fontSize = 10.sp
        )
    } else {
        val positionString = Utils.formatMsToString(adBreak.playbackPositionInMs)
        val durationString = Utils.formatMsToString(adBreak.durationInMs)

        Text(
            text = "Ad ${adBreak.adIndexInGroup} of ${adBreak.totalAdsInGroup} ($positionString / $durationString)",
            fontSize = 10.sp
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TimeBar(
    positionInMs: Long,
    durationInMs: Long,
    bufferedPositionInMs: Long,
    adGroups: List<AdGroup>,
    onSeek: (Long) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { context ->
            DefaultTimeBar(context).apply {
                setScrubberColor(0xFFFF0000.toInt())
                setPlayedColor(0xCCFF0000.toInt())
                setBufferedColor(0x77FF0000.toInt())
                setAdMarkerColor(0xFFFFFF00.toInt())
                setPlayedAdMarkerColor(0xFF888888.toInt())
            }
        },
        update = { timeBar ->
            with(timeBar) {
                addListener(object : TimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: TimeBar, position: Long) {}
                    override fun onScrubMove(timeBar: TimeBar, position: Long) {}

                    override fun onScrubStop(
                        timeBar: TimeBar,
                        position: Long,
                        canceled: Boolean
                    ) {
                        onSeek(position)
                    }
                })

                setDuration(durationInMs)
                setPosition(positionInMs)
                setBufferedPosition(bufferedPositionInMs)
                setAdGroupTimesMs(
                    adGroups.map { it.positionInMs }.toLongArray(),
                    adGroups.map { it.hasPlayed }.toBooleanArray(),
                    adGroups.size
                )
            }
        }
    )
}

@Composable
fun PlaybackButton(
    @DrawableRes resourceId: Int,
    contentDescription: String,
    onClick: () -> Unit = {}
) {
    Image(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        painter = painterResource(resourceId),
        contentDescription = contentDescription
    )
}
