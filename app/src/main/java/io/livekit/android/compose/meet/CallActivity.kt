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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.github.ajalt.timberkt.Timber
import io.livekit.android.RoomOptions
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.meet.state.rememberEnableCamera
import io.livekit.android.compose.meet.state.rememberEnableMic
import io.livekit.android.compose.meet.state.rememberPrimarySpeaker
import io.livekit.android.compose.meet.ui.ControlButton
import io.livekit.android.compose.meet.ui.PrimarySpeakerView
import io.livekit.android.compose.meet.ui.SendMessageDialog
import io.livekit.android.compose.meet.ui.TrackItem
import io.livekit.android.compose.meet.ui.theme.LKMeetAppTheme
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.flipped
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.parcelize.Parcelize

class CallActivity : ComponentActivity() {

    private val screenCaptureIntentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data
            if (resultCode != Activity.RESULT_OK || data == null) {
                return@registerForActivityResult
            }
            // TODO
            // viewModel.startScreenCapture(data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val args = intent.getParcelableExtra<BundleArgs>(KEY_ARGS)
            ?: throw NullPointerException("args is null!")

        val e2eeOptions = if (args.e2eeOn && !args.e2eeKey.isNullOrEmpty()) {
            E2EEOptions().apply {
                this.keyProvider.setSharedKey(args.e2eeKey)
            }
        } else {
            null
        }

        // Setup compose view.
        setContent {
            Content(
                url = args.url,
                token = args.token,
                e2eeOptions = e2eeOptions
            )
        }
    }


    private fun requestMediaProjection() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    @Composable
    fun Content(
        url: String,
        token: String,
        e2eeOptions: E2EEOptions?,
    ) {
        // Track whether user wants their camera/mic enabled.
        var userEnabledCamera by rememberSaveable { mutableStateOf(false) }
        var userEnabledMic by rememberSaveable { mutableStateOf(false) }

        val enableCamera = rememberEnableCamera(enabled = userEnabledCamera)
        val enableMic = rememberEnableMic(enabled = userEnabledMic)

        var cameraPosition by remember { mutableStateOf(CameraPosition.FRONT) }
        LKMeetAppTheme(darkTheme = true) {
            RoomScope(
                url = url,
                token = token,
                audio = enableMic,
                video = enableCamera,
                connect = true,
                roomOptions = RoomOptions(adaptiveStream = true, dynacast = true, e2eeOptions = e2eeOptions),
                liveKitOverrides = null,
                onError = { _, exception ->
                    Timber.e(exception)
                    Toast.makeText(this@CallActivity, "Error: $exception", Toast.LENGTH_LONG).show()
                },
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
                        PrimarySpeakerView(participant = primarySpeaker)
                    }

                    val trackReferences = rememberTracks(
                        sources = listOf(
                            Track.Source.CAMERA,
                            Track.Source.SCREEN_SHARE
                        ),
                        usePlaceholders = setOf(
                            Track.Source.CAMERA,
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

                    // Control bar for any switches such as mic/camera enable/disable.
                    Column(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 20.dp)
                            .fillMaxWidth()
                            .constrainAs(buttonBar) {
                                bottom.linkTo(parent.bottom)
                                width = Dimension.fillToConstraints
                                height = Dimension.wrapContent
                            },
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom,
                        ) {

                            val micResource =
                                if (userEnabledMic) R.drawable.outline_mic_24 else R.drawable.outline_mic_off_24
                            ControlButton(
                                resourceId = micResource,
                                contentDescription = "Mic",
                                onClick = { userEnabledMic = !userEnabledMic }
                            )


                            val cameraResource =
                                if (userEnabledCamera) R.drawable.outline_videocam_24 else R.drawable.outline_videocam_off_24
                            ControlButton(
                                resourceId = cameraResource,
                                contentDescription = "Camera",
                                onClick = { userEnabledCamera = !userEnabledCamera }
                            )

                            ControlButton(
                                resourceId = R.drawable.outline_flip_camera_android_24,
                                contentDescription = "Flip Camera",
                                onClick = {
                                    val cameraTrack =
                                        room.localParticipant
                                            .getTrackPublication(Track.Source.CAMERA)
                                            ?.track as? LocalVideoTrack
                                            ?: return@ControlButton

                                    cameraPosition = cameraPosition.flipped()
                                    cameraTrack.switchCamera(position = cameraPosition)
                                }
                            )

//                            val screenShareResource =
//                                if (screenShareEnabled) R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24
//                            LKButton(
//                                resourceId = screenShareResource,
//                                contentDescription = "Screen share",
//                                onClick = { /* TODO */}
//                            )

                            var showMessageDialog by rememberSaveable { mutableStateOf(false) }
                            ControlButton(
                                resourceId = R.drawable.baseline_chat_24,
                                contentDescription = "Send Message",
                                onClick = { showMessageDialog = true }
                            )

                            if (showMessageDialog) {
                                SendMessageDialog(
                                    onDismissRequest = { showMessageDialog = false },
                                    onSendMessage = { /* TODO */ }
                                )
                            }

                            ControlButton(
                                resourceId = R.drawable.ic_baseline_cancel_24,
                                contentDescription = "Disconnect",
                                onClick = { finish() }
                            )

                        }

//                        Spacer(modifier = Modifier.height(10.dp))
//
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceEvenly,
//                            verticalAlignment = Alignment.Bottom,
//                        ) {
//                            var showAudioDeviceDialog by remember { mutableStateOf(false) }
//                            LKButton(
//                                resourceId = R.drawable.volume_up_48px,
//                                contentDescription = "Select Audio Device",
//                                onClick = { showAudioDeviceDialog = true },
//                            )
//                            if (showAudioDeviceDialog) {
//                                SelectAudioDeviceDialog(
//                                    onDismissRequest = { showAudioDeviceDialog = false },
//                                    selectDevice = { audioSwitchHandler?.selectDevice(it) },
//                                    currentDevice = audioSwitchHandler?.selectedAudioDevice,
//                                    availableDevices = audioSwitchHandler?.availableAudioDevices ?: emptyList(),
//                                )
//                            }
//                        }
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
        val e2eeKey: String?,
        val e2eeOn: Boolean,
    ) : Parcelable
}
