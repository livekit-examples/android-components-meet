/*
 * Copyright 2023-2024 LiveKit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.livekit.android.compose.meet.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import kotlinx.coroutines.flow.combine

/**
 * Finds the primary speaker.
 */
@Composable
fun rememberPrimarySpeaker(room: Room): Participant {

    var participantState by remember {
        // Initialize with
        mutableStateOf(
            calculatePrimarySpeaker(
                previousSpeaker = null,
                activeSpeakers = room.activeSpeakers,
                participantsList = room.remoteParticipants.values + room.localParticipant,
                room = room
            )
        )
    }

    LaunchedEffect(room) {
        combine(room::remoteParticipants.flow, room::activeSpeakers.flow) { remoteParticipants, activeSpeakers ->
            participantState = calculatePrimarySpeaker(
                previousSpeaker = participantState,
                activeSpeakers = room.activeSpeakers,
                participantsList = room.remoteParticipants.values + room.localParticipant,
                room = room
            )
        }
    }
    return participantState
}

private fun calculatePrimarySpeaker(
    previousSpeaker: Participant?,
    activeSpeakers: List<Participant>,
    participantsList: List<Participant>,
    room: Room
): Participant {
    return if (previousSpeaker == null || !previousSpeaker.isSpeaking || !participantsList.contains(previousSpeaker)) {
        activeSpeakers.firstOrNull { p -> p != room.localParticipant }
            ?: participantsList.firstOrNull()
            ?: room.localParticipant
    } else {
        previousSpeaker
    }
}
