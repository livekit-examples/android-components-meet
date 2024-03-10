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

package io.livekit.android.compose.meet

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Parcelable
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.livekit.android.RoomOptions
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.meet.state.rememberEnableCamera
import io.livekit.android.compose.meet.state.rememberEnableMic
import io.livekit.android.compose.meet.state.rememberPrimarySpeaker
import io.livekit.android.compose.meet.ui.ParticipantItem
import io.livekit.android.compose.meet.ui.TrackItem
import io.livekit.android.compose.meet.ui.theme.LKMeetAppTheme
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class CallActivity2 : AppCompatActivity() {

    private val viewModel: CallViewModel by viewModelByFactory {
        val args = intent.getParcelableExtra<BundleArgs>(KEY_ARGS)
            ?: throw NullPointerException("args is null!")
        CallViewModel(
            url = args.url,
            token = args.token,
            e2ee = args.e2eeOn,
            e2eeKey = args.e2eeKey,
            application = application,
        )
    }

    private val screenCaptureIntentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data
            if (resultCode != Activity.RESULT_OK || data == null) {
                return@registerForActivityResult
            }
            viewModel.startScreenCapture(data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Setup compose view.
        setContent {
            Content()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.error.collect {
                    if (it != null) {
                        Toast.makeText(this@CallActivity2, "Error: $it", Toast.LENGTH_LONG).show()
                        viewModel.dismissError()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.dataReceived.collect {
                    Toast.makeText(this@CallActivity2, "Data received: $it", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun requestMediaProjection() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    @Composable
    fun Content() {
        // Track whether user wants their camera/mic enabled.
        val userEnabledCamera by rememberSaveable { mutableStateOf(false) }
        val userEnabledMic by rememberSaveable { mutableStateOf(false) }

        val enableCamera = rememberEnableCamera(enabled = userEnabledCamera)
        val enableMic = rememberEnableMic(enabled = userEnabledMic)
        LKMeetAppTheme(darkTheme = true) {
            RoomScope(
                url = viewModel.url,
                token = viewModel.token,
                audio = enableMic,
                video = enableCamera,
                connect = true,
                roomOptions = RoomOptions(adaptiveStream = true, dynacast = true),
                onError = { room: Room, exception: Exception? -> },
                passedRoom = null
            ) { room ->

                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    val (speakerView, audienceRow, buttonBar) = createRefs()

                    // Primary speaker view
                    Surface(
                        modifier = Modifier.constrainAs(speakerView) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            bottom.linkTo(audienceRow.top)
                            width = Dimension.fillToConstraints
                            height = Dimension.fillToConstraints
                        },
                    ) {
                        val primarySpeaker = rememberPrimarySpeaker(room = room)
                        ParticipantItem(
                            room = room,
                            participant = primarySpeaker,
                            isSpeaking = primarySpeaker::isSpeaking.flow.collectAsState().value,
                        )
                    }

                    val trackReferences = rememberTracks(
                        sources = listOf(
                            Track.Source.CAMERA,
                            Track.Source.SCREEN_SHARE
                        ),
                        onlySubscribed = false
                    )
                    // Audience row to display all participants.
                    LazyRow(
                        modifier = Modifier
                            .constrainAs(audienceRow) {
                                top.linkTo(speakerView.bottom)
                                bottom.linkTo(buttonBar.top)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                                width = Dimension.fillToConstraints
                                height = Dimension.value(120.dp)
                            },
                    ) {
                        items(
                            count = trackReferences.size,
                            key = { index -> trackReferences[index].participant.sid.value + trackReferences[index].source },
                        ) { index ->
                            TrackItem(
                                trackReference = trackReferences[index],
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1.0f, true),
                            )
                        }
                    }

                }

            }
        }
    }

    companion object {
        const val KEY_ARGS = "args"
    }

    @Parcelize
    data class BundleArgs(
        val url: String,
        val token: String,
        val e2eeKey: String,
        val e2eeOn: Boolean,
    ) : Parcelable
}
