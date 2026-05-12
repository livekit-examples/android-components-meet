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
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Parcelable
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.github.ajalt.timberkt.Timber
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.meet.ui.ControlButton
import io.livekit.android.compose.meet.ui.theme.LKMeetAppTheme
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.VideoPreset169
import kotlinx.parcelize.Parcelize

class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val args = intent.getParcelableExtra<BundleArgs>(KEY_ARGS)
            ?: throw NullPointerException("args is null!")

        val e2eeOptions = if (args.e2eeOn && !args.e2eeKey.isNullOrEmpty()) {
            E2EEOptions().apply { this.keyProvider.setSharedKey(args.e2eeKey) }
        } else null

        setContent {
            Content(
                url = args.url,
                token = args.token,
                e2eeOptions = e2eeOptions,
            )
        }
    }

    @Composable
    fun Content(
        url: String,
        token: String,
        e2eeOptions: E2EEOptions?,
    ) {
        var enableScreenCapture by remember { mutableStateOf<Intent?>(null) }

        LKMeetAppTheme(darkTheme = true) {
            RoomScope(
                url = url,
                token = token,
                audio = false,
                video = false,
                connect = true,
                roomOptions = defaultRoomOptions { it.copy(e2eeOptions = e2eeOptions) },
                liveKitOverrides = DefaultLKOverrides(this),
                onError = { _, exception ->
                    Timber.e(exception)
                    Toast.makeText(this@CallActivity, "连接失败: $exception", Toast.LENGTH_LONG).show()
                }
            ) { room ->

                val screenCaptureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        enableScreenCapture = result.data
                    } else {
                        enableScreenCapture = null
                    }
                }

                LaunchedEffect(enableScreenCapture) {
                    val intent = enableScreenCapture
                    if (intent != null) {
                        val screencastTrack = room.localParticipant.createScreencastTrack(mediaProjectionPermissionResultData = intent)
                        room.localParticipant.publishVideoTrack(screencastTrack)
                        screencastTrack.startForegroundService(null, null)
                        screencastTrack.startCapture()
                    } else {
                        room.localParticipant.setScreenShareEnabled(false)
                    }
                }

                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF111111)),
                ) {
                    val (infoArea, buttonBar) = createRefs()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .constrainAs(infoArea) {
                                top.linkTo(parent.top)
                                bottom.linkTo(buttonBar.top)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "恩信共享助手",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0A84FF),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (enableScreenCapture == null)
                                "连接完毕\n请点击下方按钮开启屏幕共享"
                                else "正在共享屏幕中\n您的画面已实时同步至会议",
                            fontSize = 16.sp,
                            color = Color.LightGray,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier
                            .padding(bottom = 50.dp)
                            .fillMaxWidth()
                            .constrainAs(buttonBar) {
                                bottom.linkTo(parent.bottom)
                            },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val screenShareResource = if (enableScreenCapture != null)
                            R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24
                        
                        ControlButton(
                            resourceId = screenShareResource,
                            contentDescription = "Toggle Screen Share",
                            onClick = {
                                if (enableScreenCapture == null) {
                                    val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                } else {
                                    enableScreenCapture = null
                                }
                            }
                        )

                        ControlButton(
                            resourceId = R.drawable.ic_baseline_cancel_24,
                            contentDescription = "Disconnect",
                            onClick = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun defaultRoomOptions(customizer: (RoomOptions) -> RoomOptions): RoomOptions {
        // 这里还原，不改任何结构
        return customizer(RoomOptions(
            adaptiveStream = true,
            dynacast = true,
            videoTrackPublishDefaults = VideoTrackPublishDefaults(
                videoEncoding = VideoPreset169.H720.encoding.copy(maxBitrate = 3_000_000),
                simulcast = true,
            ),
        ))
    }

    private fun DefaultLKOverrides(context: Context) = LiveKitOverrides(
        audioOptions = AudioOptions(
            // 【关键修改点】关闭音频播放。
            // playAudio 设为 false，SDK 内部将不会把收到的音频流路由到扬声器。
            playAudio = false,
            audioHandler = AudioSwitchHandler(context).apply {
                preferredDeviceList = listOf(
                    AudioDevice.BluetoothHeadset::class.java,
                    AudioDevice.WiredHeadset::class.java,
                    AudioDevice.Speakerphone::class.java
                )
            }
        )
    )

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
