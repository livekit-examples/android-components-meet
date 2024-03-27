package io.livekit.android.compose.meet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.livekit.android.compose.state.rememberParticipantTrackReferences
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track

@Composable
fun PrimarySpeakerView(
    participant: Participant,
    modifier: Modifier = Modifier,
) {
    val videoTracks = rememberParticipantTrackReferences(
        passedParticipant = participant,
        usePlaceholders = setOf(Track.Source.CAMERA),
    )

    // Prefer screen share track.
    val trackToShow = videoTracks.firstOrNull { track -> track.source == Track.Source.SCREEN_SHARE }
        ?: videoTracks.firstOrNull { track -> track.source == Track.Source.CAMERA }
        ?: videoTracks.first()

    TrackItem(
        trackReference = trackToShow,
        modifier = modifier
    )
}