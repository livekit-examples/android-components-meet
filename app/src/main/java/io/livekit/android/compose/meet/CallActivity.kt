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
        // 保持屏幕常亮
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
        // 状态追踪
        var enableScreenCapture by remember { mutableStateOf<Intent?>(null) }
        val isSharing = enableScreenCapture != null

        LKMeetAppTheme(darkTheme = true) {
            RoomScope(
                url = url,
                token = token,
                audio = false, // 默认不开启麦克风推流
                video = false, // 默认不开启摄像头推流
                connect = true,
                roomOptions = defaultRoomOptions { it.copy(e2eeOptions = e2eeOptions) },
                liveKitOverrides = DefaultLKOverrides(this),
                onError = { _, exception ->
                    Timber.e(exception)
                    Toast.makeText(this@CallActivity, "连接失败: $exception", Toast.LENGTH_LONG).show()
                }
            ) { room ->

                // 屏幕采集授权处理器
                val screenCaptureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        enableScreenCapture = result.data
                    } else {
                        enableScreenCapture = null
                    }
                }

                // 屏幕共享推流逻辑
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

                // --- UI 布局优化：增加醒目的状态提示 ---
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        // 共享时背景变成深蓝色，不共享时保持深黑色，视觉区分明显
                        .background(if (isSharing) Color(0xFF001A33) else Color(0xFF111111)),
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
                        // 1. 标题增强：共享时变绿并加粗
                        Text(
                            text = if (isSharing) "● 正在共享屏幕" else "恩信共享助手",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSharing) Color(0xFF00FF00) else Color(0xFF0A84FF),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 2. 描述增强：共享时文字变白变大
                        Text(
                            text = if (isSharing)
                                "您的实时画面已发送至会议\n已自动禁用音频接收，彻底杜绝回音"
                                else "连接成功\n请点击下方按钮开启共享",
                            fontSize = 18.sp,
                            fontWeight = if (isSharing) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSharing) Color.White else Color.LightGray,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // 底部控制栏
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
                        // 共享按钮：根据状态切换图标
                        val screenShareResource = if (isSharing)
                            R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24
                        
                        ControlButton(
                            resourceId = screenShareResource,
                            contentDescription = "Toggle Screen Share",
                            onClick = {
                                if (!isSharing) {
                                    val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                } else {
                                    enableScreenCapture = null
                                }
                            }
                        )

                        // 退出按钮
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

    // --- 核心修复点 1：关闭自动订阅 (彻底解决回音) ---
    private fun defaultRoomOptions(customizer: (RoomOptions) -> RoomOptions): RoomOptions {
        return customizer(RoomOptions(
            autoSubscribe = false, // 关键：设置为 false，不接收任何人的音频/视频
            adaptiveStream = false, // 助手不需要拉流，关闭自适应
            dynacast = true,
            videoTrackPublishDefaults = VideoTrackPublishDefaults(
                videoEncoding = VideoPreset169.H720.encoding.copy(maxBitrate = 3_000_000),
                simulcast = true,
            ),
        ))
    }

    // --- 核心修复点 2：保留结构但削弱音频处理 (防止焦点冲突) ---
    private fun DefaultLKOverrides(context: Context) = LiveKitOverrides(
        audioOptions = AudioOptions(
            audioHandler = AudioSwitchHandler(context).apply {
                // 虽然保留了处理器防止编译失败，但因为上面禁用了订阅，这里不会有声音输出
                preferredDeviceList = listOf(
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
