package dev.vinigouveia.moviesapp.playback

import android.app.Application
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ads.AdsLoader
import com.google.ads.interactivemedia.v3.api.AdEvent
import dev.vinigouveia.moviesapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    application: Application
) : ViewModel() {
    private val _playerUiModel = MutableStateFlow(PlayerUiModel())
    val playerUiModel: StateFlow<PlayerUiModel> = _playerUiModel.asStateFlow()
    private val playerCoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
    private var positionTrackingJob: Job? = null

    private var selectedVideoTrack: VideoTrack = VideoTrack.AUTO
    private var selectedAudioTrack: AudioTrack = AudioTrack.AUTO
    private var selectedSubtitleTrack: SubtitleTrack = SubtitleTrack.AUTO

    private var videoTracksMap: Map<VideoTrack, ExoPlayerTrack?> = emptyMap()
    private var audioTracksMap: Map<AudioTrack, ExoPlayerTrack?> = emptyMap()
    private var subtitleTracksMap: Map<SubtitleTrack, ExoPlayerTrack?> = emptyMap()

    private class ExoPlayerTrack(
        val trackGroup: TrackGroup,
        val trackIndexInGroup: Int
    )

    private val playerEventListener: Player.Listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize !== VideoSize.UNKNOWN) {
                val videoWidth = videoSize.width
                val videoHeight = videoSize.height / videoSize.pixelWidthHeightRatio
                val videoAspectRatio = videoWidth / videoHeight
                _playerUiModel.value = _playerUiModel.value.copy(
                    videoAspectRatio = videoAspectRatio
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                _playerUiModel.value = _playerUiModel.value.copy(
                    playbackState = PlaybackState.PLAYING
                )
            } else if (exoPlayer.playbackState == Player.STATE_READY) {
                _playerUiModel.value = _playerUiModel.value.copy(
                    playbackState = PlaybackState.PAUSED
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val state = when (playbackState) {
                Player.STATE_IDLE -> {
                    if (exoPlayer.playerError != null) {
                        PlaybackState.ERROR
                    } else {
                        PlaybackState.IDLE
                    }
                }

                Player.STATE_BUFFERING -> PlaybackState.BUFFERING

                Player.STATE_READY -> {
                    if (exoPlayer.playWhenReady) PlaybackState.PLAYING
                    else PlaybackState.PAUSED
                }

                Player.STATE_ENDED -> PlaybackState.COMPLETED

                else -> PlaybackState.IDLE
            }

            _playerUiModel.value = _playerUiModel.value.copy(
                playbackState = state
            )

            if (state == PlaybackState.ERROR) {
                showPlayerControl()
            }

            when (playbackState) {
                Player.STATE_READY -> {
                    startTrackingPlaybackPosition()
                }

                else -> {
                    stopTrackingPlaybackPosition()
                }
            }

            when (playbackState) {
                Player.STATE_READY -> {
                    hidePlaceHolderImage()
                }

                Player.STATE_ENDED,
                Player.STATE_IDLE -> {
                    showPlaceHolderImage()
                }

                else -> {}
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val newVideoTracks = mutableMapOf<VideoTrack, ExoPlayerTrack?>(
                VideoTrack.AUTO to null
            )
            val newAudioTracks = mutableMapOf<AudioTrack, ExoPlayerTrack?>(
                AudioTrack.AUTO to null,
                AudioTrack.NONE to null
            )
            val newSubtitleTracks = mutableMapOf<SubtitleTrack, ExoPlayerTrack?>(
                SubtitleTrack.AUTO to null,
                SubtitleTrack.NONE to null
            )

            tracks.groups.forEach {
                when (it.type) {
                    C.TRACK_TYPE_VIDEO -> {
                        newVideoTracks.putAll(extractVideoTracks(it))
                    }

                    C.TRACK_TYPE_AUDIO -> {
                        newAudioTracks.putAll(extractAudioTracks(it))
                    }

                    C.TRACK_TYPE_TEXT -> {
                        newSubtitleTracks.putAll(extractSubtitleTracks(it))
                    }
                }
            }

            videoTracksMap = newVideoTracks
            audioTracksMap = newAudioTracks
            subtitleTracksMap = newSubtitleTracks

            _playerUiModel.value = _playerUiModel.value.copy(
                trackSelectionUiModel = TrackSelectionUiModel(
                    selectedVideoTrack = selectedVideoTrack,
                    videoTracks = videoTracksMap.keys.toList(),
                    selectedAudioTrack = selectedAudioTrack,
                    audioTracks = audioTracksMap.keys.toList(),
                    selectedSubtitleTrack = selectedSubtitleTrack,
                    subtitleTracks = subtitleTracksMap.keys.toList()
                )
            )
        }

        override fun onCues(cueGroup: CueGroup) {
            _playerUiModel.value = _playerUiModel.value.copy(
                currentSubtitles = cueGroup.cues
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun extractVideoTracks(info: Tracks.Group): Map<VideoTrack, ExoPlayerTrack> {
        val result = mutableMapOf<VideoTrack, ExoPlayerTrack>()

        for (trackIndex in 0 until info.mediaTrackGroup.length) {
            val format = info.mediaTrackGroup.getFormat(trackIndex)
            val width = format.width
            val height = format.height
            val videoTrack = VideoTrack(width, height)

            result[videoTrack] = ExoPlayerTrack(
                trackGroup = info.mediaTrackGroup,
                trackIndexInGroup = trackIndex
            )
        }

        return result
    }

    @OptIn(UnstableApi::class)
    private fun extractAudioTracks(info: Tracks.Group): Map<AudioTrack, ExoPlayerTrack> {
        val result = mutableMapOf<AudioTrack, ExoPlayerTrack>()

        for (trackIndex in 0 until info.mediaTrackGroup.length) {
            val format = info.mediaTrackGroup.getFormat(trackIndex)
            val language = format.language

            if (language != null) {
                val audioTrack = AudioTrack(language)

                result[audioTrack] = ExoPlayerTrack(
                    trackGroup = info.mediaTrackGroup,
                    trackIndexInGroup = trackIndex
                )
            }
        }

        return result
    }

    @OptIn(UnstableApi::class)
    private fun extractSubtitleTracks(info: Tracks.Group): Map<SubtitleTrack, ExoPlayerTrack> {
        val result = mutableMapOf<SubtitleTrack, ExoPlayerTrack>()

        for (trackIndex in 0 until info.mediaTrackGroup.length) {
            val format = info.mediaTrackGroup.getFormat(trackIndex)
            val language = format.language

            if (language != null) {
                val subtitleTrack = SubtitleTrack(language)

                result[subtitleTrack] = ExoPlayerTrack(
                    trackGroup = info.mediaTrackGroup,
                    trackIndexInGroup = trackIndex
                )
            }
        }

        return result
    }

    private fun setVideoTrack(videoTrack: VideoTrack) {
        val selectionBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)

        when {
            videoTrack == VideoTrack.AUTO -> {
                selectedVideoTrack = videoTrack
            }

            else -> {
                val exoVideoTrack = videoTracksMap[videoTrack]
                if (exoVideoTrack != null) {
                    selectionBuilder.setOverrideForType(
                        TrackSelectionOverride(
                            exoVideoTrack.trackGroup,
                            listOf(exoVideoTrack.trackIndexInGroup)
                        )
                    )
                    selectedVideoTrack = videoTrack
                }
            }
        }

        exoPlayer.trackSelectionParameters = selectionBuilder.build()

        _playerUiModel.value = _playerUiModel.value.copy(
            trackSelectionUiModel = _playerUiModel.value.trackSelectionUiModel?.copy(
                selectedVideoTrack = selectedVideoTrack
            )
        )
    }

    private fun setAudioTrack(audioTrack: AudioTrack) {
        val trackIsDisabled = audioTrack == AudioTrack.NONE
        val selectionBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, trackIsDisabled)

        when {
            audioTrack == VideoTrack.AUTO || audioTrack == AudioTrack.NONE -> {
                selectedAudioTrack = audioTrack
            }

            else -> {
                val exoAudioTrack = audioTracksMap[audioTrack]
                if (exoAudioTrack != null) {
                    selectionBuilder.setOverrideForType(
                        TrackSelectionOverride(
                            exoAudioTrack.trackGroup,
                            listOf(exoAudioTrack.trackIndexInGroup)
                        )
                    )
                    selectedAudioTrack = audioTrack
                }
            }
        }

        exoPlayer.trackSelectionParameters = selectionBuilder.build()

        _playerUiModel.value = _playerUiModel.value.copy(
            trackSelectionUiModel = _playerUiModel.value.trackSelectionUiModel?.copy(
                selectedAudioTrack = selectedAudioTrack
            )
        )
    }

    private fun setSubtitleTrack(subtitleTrack: SubtitleTrack) {
        val trackIsDisabled = subtitleTrack == SubtitleTrack.NONE
        val selectionBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, trackIsDisabled)

        when {
            subtitleTrack == VideoTrack.AUTO || subtitleTrack == SubtitleTrack.NONE -> {
                selectedSubtitleTrack = subtitleTrack
            }

            else -> {
                val exoSubtitleTrack = subtitleTracksMap[subtitleTrack]
                if (exoSubtitleTrack != null) {
                    selectionBuilder.setOverrideForType(
                        TrackSelectionOverride(
                            exoSubtitleTrack.trackGroup,
                            listOf(exoSubtitleTrack.trackIndexInGroup)
                        )
                    )
                    selectedSubtitleTrack = subtitleTrack
                }
            }
        }

        exoPlayer.trackSelectionParameters = selectionBuilder.build()

        _playerUiModel.value = _playerUiModel.value.copy(
            trackSelectionUiModel = _playerUiModel.value.trackSelectionUiModel?.copy(
                selectedSubtitleTrack = selectedSubtitleTrack
            )
        )
    }

    private val exoPlayer = buildExoPlayer(application).apply {
        addListener(playerEventListener)
    }

    private val imaAdsLoader = buildImaAdsLoader(application)

    private fun buildExoPlayer(application: Application): ExoPlayer {
        val exoPlayerBuilder = ExoPlayer.Builder(application)
        val adsProvider = AdsLoader.Provider { imaAdsLoader }
        val mediaSourceFactory = DefaultMediaSourceFactory(application)
            .setLocalAdInsertionComponents(adsProvider) { null }

        return exoPlayerBuilder.setMediaSourceFactory(mediaSourceFactory).build()
    }

    @OptIn(UnstableApi::class)
    private fun buildImaAdsLoader(application: Application): ImaAdsLoader {
        return ImaAdsLoader.Builder(application)
            .setAdEventListener {
                processAdEvents(it)
            }
            .build().also {
                it.setPlayer(exoPlayer)
            }
    }

    private fun processAdEvents(event: AdEvent) {
        when (event.type) {
            AdEvent.AdEventType.AD_PROGRESS -> {
                val adPod = event.ad.adPodInfo
                adPod.let {
                    val adBreak = AdBreak(
                        adIndexInGroup = it.adPosition,
                        totalAdsInGroup = it.totalAds,
                        durationInMs = exoPlayer.duration,
                        playbackPositionInMs = exoPlayer.currentPosition
                    )

                    _playerUiModel.value = _playerUiModel.value.copy(
                        adUiModel = _playerUiModel.value.adUiModel?.copy(
                            currentAdBreak = adBreak
                        )
                    )
                }
            }

            AdEvent.AdEventType.COMPLETED -> {
                val adBreak = _playerUiModel.value.adUiModel?.currentAdBreak
                val isLastInGroup = adBreak?.adIndexInGroup == adBreak?.totalAdsInGroup
                if (isLastInGroup) {
                    _playerUiModel.value = _playerUiModel.value.copy(
                        adUiModel = _playerUiModel.value.adUiModel?.copy(
                            currentAdBreak = null
                        )
                    )
                }
            }

            else -> {}
        }
    }

    private fun buildAdGroups(): List<AdGroup> {
        val adGroups = mutableListOf<AdGroup>()
        val timeline = exoPlayer.currentTimeline
        if (!timeline.isEmpty) {
            val window = Timeline.Window()
            val period = Timeline.Period()
            timeline.getWindow(0, window)
            timeline.getPeriod(window.firstPeriodIndex, period)
            val adGroupCount = period.adGroupCount
            for (index in 0 until adGroupCount) {
                val adGroupTimeInUs = period.getAdGroupTimeUs(index)
                val isPostRoll = adGroupTimeInUs == C.TIME_END_OF_SOURCE
                val adGroupTimeInMs =
                    if (isPostRoll) exoPlayer.duration else adGroupTimeInUs / 1000L
                val isPlayed = period.hasPlayedAdGroup(index)
                adGroups.add(AdGroup(adGroupTimeInMs, isPlayed))
            }
        }

        return adGroups
    }

    private fun startTrackingPlaybackPosition() {
        positionTrackingJob = playerCoroutineScope.launch {
            while (true) {
                val newTimelineUiModel =
                    buildTimelineUiModel() ?: _playerUiModel.value.timelineUiModel
                val newAdUiModel = if (_playerUiModel.value.adUiModel != null) {
                    _playerUiModel.value.adUiModel?.copy(
                        adGroups = buildAdGroups()
                    )
                } else {
                    AdUiModel(
                        currentAdBreak = null,
                        adGroups = buildAdGroups()
                    )
                }
                _playerUiModel.value = _playerUiModel.value.copy(
                    timelineUiModel = newTimelineUiModel,
                    adUiModel = newAdUiModel
                )
                delay(1000)
            }
        }
    }

    private fun stopTrackingPlaybackPosition() {
        buildTimelineUiModel()
        positionTrackingJob?.cancel()
        positionTrackingJob = null
    }

    private fun buildTimelineUiModel(): TimelineUiModel? {
        val duration = exoPlayer.contentDuration
        if (duration == C.TIME_UNSET) return null

        val currentPosition = exoPlayer.contentPosition
        val bufferedPosition = exoPlayer.contentBufferedPosition

        return TimelineUiModel(
            durationInMs = duration,
            currentPositionInMs = currentPosition,
            bufferedPositionInMs = bufferedPosition
        )
    }

    fun handleAction(action: Action) {
        when (action) {
            is AttachSurface -> {
                exoPlayer.setVideoSurface(action.surface)
            }

            DetachSurface -> {
                exoPlayer.setVideoSurface(null)
            }

            is FastForward -> {
                exoPlayer.seekTo(exoPlayer.currentPosition + action.amountInMs)
            }

            is Rewind -> {
                exoPlayer.seekTo(exoPlayer.currentPosition - action.amountInMs)
            }

            is Init -> {
                val mediaItem = MediaItem.Builder().run {
                    action.adTagUrl?.let {
                        this.setAdsConfiguration(
                            MediaItem.AdsConfiguration.Builder(it.toUri()).build()
                        )
                    } ?: this
                }.setUri(action.streamUrl).build()

                exoPlayer.setMediaItem(mediaItem)
            }

            Pause -> {
                exoPlayer.pause()
            }

            Resume -> {
                exoPlayer.play()
            }

            is Seek -> {
                exoPlayer.seekTo(action.targetInMs)
            }

            is Start -> {
                with(exoPlayer) {
                    prepare()
                    play()
                    action.positionInMs?.let {
                        seekTo(it)
                    }
                }
            }

            Stop -> {
                exoPlayer.stop()
            }

            is SetVideoTrack -> {
                setVideoTrack(action.videoTrack)
            }

            is SetAudioTrack -> {
                setAudioTrack(action.audioTrack)
            }

            is SetSubtitleTrack -> {
                setSubtitleTrack(action.subtitleTrack)
            }
        }
    }

    fun showPlaceHolderImage() {
        _playerUiModel.value = _playerUiModel.value.copy(
            placeHolderImageResourceId = R.drawable.tears_of_steal_cover
        )
    }

    fun hidePlaceHolderImage() {
        _playerUiModel.value = _playerUiModel.value.copy(
            placeHolderImageResourceId = null
        )
    }

    fun showPlayerControl() {
        _playerUiModel.value = _playerUiModel.value.copy(
            playerControlsVisible = true
        )
    }

    fun hidePlayerControl() {
        _playerUiModel.value = _playerUiModel.value.copy(
            playerControlsVisible = false
        )
    }

    fun enterFullScreen() {
        _playerUiModel.value = _playerUiModel.value.copy(
            isFullScreen = true
        )
    }

    fun exitFullScreen() {
        _playerUiModel.value = _playerUiModel.value.copy(
            isFullScreen = false
        )
    }

    fun showTrackSelector() {
        _playerUiModel.value = _playerUiModel.value.copy(
            isTrackSelectorVisible = true
        )
    }

    fun hideTrackSelector() {
        _playerUiModel.value = _playerUiModel.value.copy(
            isTrackSelectorVisible = false
        )
    }

    override fun onCleared() {
        exoPlayer.release()
    }

    companion object {
        fun buildFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return PlayerViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel type $modelClass")
                }
            }
        }
    }
}
