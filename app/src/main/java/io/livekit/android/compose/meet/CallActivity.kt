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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

        // 呼吸灯动画逻辑：用于增强状态感知
        val infiniteTransition = rememberInfiniteTransition(label = "sharing")
        val breatheAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha"
        )

        LKMeetAppTheme(darkTheme = true) {
            RoomScope(
                url = url,
                token = token,
                audio = false, // 不发声
                video = false, // 不开摄
                connect = true,
                roomOptions = defaultRoomOptions { it.copy(e2eeOptions = e2eeOptions) },
                liveKitOverrides = DefaultLKOverrides(), // 释放音频焦点
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

                // --- 升级后的 UI 布局 ---
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF050505)), // 更纯净的深黑
                ) {
                    val (topGradient, infoArea, buttonBar, statusBadge) = createRefs()

                    // 1. 顶部状态流光：共享时出现，极具视觉冲击力
                    if (isSharing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF0A84FF).copy(alpha = 0.15f), Color.Transparent)
                                    )
                                )
                                .constrainAs(topGradient) { top.linkTo(parent.top) }
                        )
                    }

                    // 2. 状态勋章 (Status Badge)
                    Surface(
                        color = if (isSharing) Color(0xFF0A84FF).copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(top = 50.dp)
                            .constrainAs(statusBadge) {
                                top.linkTo(parent.top)
                                centerHorizontallyTo(parent)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 动态呼吸小圆点
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isSharing) Color(0xFF00FF00).copy(alpha = breatheAlpha) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isSharing) "LIVE | 正在共享屏幕" else "待命状态",
                                color = if (isSharing) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 3. 中间核心展示区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .constrainAs(infoArea) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 科技感中心图标
                        Box(contentAlignment = Alignment.Center) {
                            if (isSharing) {
                                // 扩散波纹效果
                                Canvas(modifier = Modifier.size(150.dp)) {
                                    drawCircle(color = Color(0xFF0A84FF), radius = 100f * breatheAlpha, alpha = 0.5f - (breatheAlpha * 0.5f))
                                }
                            }
                            Icon(
                                painter = painterResource(id = if (isSharing) R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = if (isSharing) Color(0xFF0A84FF) else Color(0xFF333333)
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // 状态大文字：增加发光和粗体
                        Text(
                            text = "恩信共享助手",
                            style = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                shadow = if (isSharing) Shadow(color = Color(0xFF0A84FF), blurRadius = 30f) else null,
                                letterSpacing = 2.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isSharing) "您的屏幕内容已实时同步至会议\n助手已自动屏蔽下行声音以消除回音"
                                   else "连接成功，音频下行已关闭\n请点击下方按钮启动共享",
                            fontSize = 14.sp,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }

                    // 4. 底部毛玻璃悬浮控制栏
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 50.dp, start = 40.dp, end = 40.dp)
                            .fillMaxWidth()
                            .height(100.dp)
                            .constrainAs(buttonBar) { bottom.linkTo(parent.bottom) },
                        shape = RoundedCornerShape(30.dp),
                        color = Color(0xFF1A1A1A).copy(alpha = 0.9f),
                        tonalElevation = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlButton(
                                resourceId = if (isSharing) R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24,
                                contentDescription = "Toggle",
                                onClick = {
                                    if (!isSharing) {
                                        val mm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        screenCaptureLauncher.launch(mm.createScreenCaptureIntent())
                                    } else {
                                        enableScreenCapture = null
                                    }
                                }
                            )

                            // 装饰性的分割线
                            Box(Modifier.width(1.dp).height(30.dp).background(Color(0xFF333333)))

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
    }

    // --- 底层逻辑配置 ---

    private fun defaultRoomOptions(customizer: (RoomOptions) -> RoomOptions): RoomOptions {
        return customizer(RoomOptions(
            autoSubscribe = false, // 【关键】彻底解决回音：不订阅任何人的声音
            adaptiveStream = false, // 既然不看画面，关掉下行自适应流
            dynacast = true,
            videoTrackPublishDefaults = VideoTrackPublishDefaults(
                videoEncoding = VideoPreset169.H720.encoding.copy(maxBitrate = 3_000_000),
                simulcast = true,
            ),
        ))
    }

    private fun DefaultLKOverrides() = LiveKitOverrides(
        audioOptions = AudioOptions(
            // 【关键】释放音频焦点：不设置音频处理器，让主 App 独占麦克风/扬声器
            audioHandler = null
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
