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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.github.ajalt.timberkt.Timber
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
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
        val isSharing = enableScreenCapture != null

        LKMeetAppTheme(darkTheme = true) {
            RoomScope(
                url = url,
                token = token,
                audio = false,
                video = false,
                connect = true,
                roomOptions = defaultRoomOptions { it.copy(e2eeOptions = e2eeOptions) },
                liveKitOverrides = DefaultLKOverrides(), // 内部已改为不处理音频
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

                // UI 布局：仅使用你原本就有的组件
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSharing) Color(0xFF001529) else Color(0xFF111111)),
                ) {
                    val (infoArea, buttonBar) = createRefs()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .constrainAs(infoArea) {
                                top.linkTo(parent.top)
                                bottom.linkTo(buttonBar.top)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 状态标题：开启后变大、加粗、变色
                        Text(
                            text = if (isSharing) "正在实时共享屏幕" else "恩信共享助手",
                            fontSize = if (isSharing) 30.sp else 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSharing) Color(0xFF00FF00) else Color(0xFF0A84FF),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // 提示文字：增加对比度
                        Text(
                            text = if (isSharing)
                                "【注意】共享已开启\n画面正在同步至会议\n助手已自动开启回音处理"
                                else "连接完毕\n请点击下方按钮开启屏幕共享",
                            fontSize = 16.sp,
                            color = if (isSharing) Color.White else Color.LightGray,
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSharing) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // 底部控制栏：保持原样，只改动按钮逻辑
                    Row(
                        modifier = Modifier
                            .padding(bottom = 60.dp)
                            .fillMaxWidth()
                            .constrainAs(buttonBar) {
                                bottom.linkTo(parent.bottom)
                            },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButton(
                            resourceId = if (isSharing) R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24,
                            contentDescription = "Share",
                            onClick = {
                                if (!isSharing) {
                                    val mm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    screenCaptureLauncher.launch(mm.createScreenCaptureIntent())
                                } else {
                                    enableScreenCapture = null
                                }
                            }
                        )

                        ControlButton(
                            resourceId = R.drawable.ic_baseline_cancel_24,
                            contentDescription = "Exit",
                            onClick = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun defaultRoomOptions(customizer: (RoomOptions) -> RoomOptions): RoomOptions {
        return customizer(RoomOptions(
            autoSubscribe = false, // 核心逻辑：不听别人说话，解决回音
            adaptiveStream = false,
            dynacast = true,
            videoTrackPublishDefaults = VideoTrackPublishDefaults(
                videoEncoding = VideoPreset169.H720.encoding.copy(maxBitrate = 3_000_000),
                simulcast = true,
            ),
        ))
    }

    private fun DefaultLKOverrides() = LiveKitOverrides(
        audioOptions = AudioOptions(
            audioHandler = null // 核心逻辑：不抢音频焦点
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
