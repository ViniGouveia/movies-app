package dev.vinigouveia.moviesapp.playback

import androidx.media3.common.text.Cue
import dev.vinigouveia.moviesapp.R

data class PlayerUiModel(
    val videoAspectRatio: Float = 16.0f / 9.0f,
    val isFullScreen: Boolean = false,
    val placeHolderImageResourceId: Int? = R.drawable.tears_of_steal_cover,
    val playerControlsVisible: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val timelineUiModel: TimelineUiModel? = null,
    val trackSelectionUiModel: TrackSelectionUiModel? = null,
    val isTrackSelectorVisible: Boolean = false,
    val currentSubtitles: List<Cue> = emptyList(),
    val adUiModel: AdUiModel? = null
)

enum class PlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
    COMPLETED
}

data class TimelineUiModel(
    val durationInMs: Long,
    val currentPositionInMs: Long,
    val bufferedPositionInMs: Long
)

data class TrackSelectionUiModel(
    val selectedVideoTrack: VideoTrack,
    val videoTracks: List<VideoTrack>,
    val selectedAudioTrack: AudioTrack,
    val audioTracks: List<AudioTrack>,
    val selectedSubtitleTrack: SubtitleTrack,
    val subtitleTracks: List<SubtitleTrack>
)

data class AdUiModel(
    val currentAdBreak: AdBreak?,
    val adGroups: List<AdGroup>
)

data class AdGroup(
    val positionInMs: Long,
    val hasPlayed: Boolean
)

data class AdBreak(
    val adIndexInGroup: Int,
    val totalAdsInGroup: Int,
    val durationInMs: Long,
    val playbackPositionInMs: Long
)

data class AudioTrack(
    val language: String
) {
    val displayName: String
        get() {
            return when (this) {
                AUTO -> "Auto"
                NONE -> "None"
                else -> language
            }
        }

    companion object {
        val AUTO = AudioTrack("Auto")
        val NONE = AudioTrack("None")
    }
}

data class SubtitleTrack(
    val language: String
) {
    val displayName: String
        get() {
            return when (this) {
                AUTO -> "Auto"
                NONE -> "None"
                else -> language
            }
        }

    companion object {
        val AUTO = SubtitleTrack("Auto")
        val NONE = SubtitleTrack("None")
    }
}

data class VideoTrack(
    val width: Int,
    val height: Int
) {
    val displayName: String
        get() {
            return when (this) {
                AUTO -> "Auto"
                else -> "${width}x$height"
            }
        }

    companion object {
        val AUTO = VideoTrack(0, 0)
    }
}

fun PlaybackState.isReady(): Boolean {
    return this == PlaybackState.PLAYING || this == PlaybackState.PAUSED
}
