/*
 * Copyright 2023 LiveKit, Inc.
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

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.livekit.android.compose.meet.ui.theme.LKMeetAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainContent(
                // 默认从 ViewModel 读取你之前改好的私有服务器地址
                defaultUrl = viewModel.getSavedUrl(),
                defaultToken = viewModel.getSavedToken(),
                onConnect = { url, token ->
                    // 保存设置并跳转
                    viewModel.setSavedUrl(url)
                    viewModel.setSavedToken(token)

                    val intent = Intent(this@MainActivity, CallActivity::class.java).apply {
                        putExtra(
                            CallActivity.KEY_ARGS,
                            CallActivity.BundleArgs(
                                url = url,
                                token = token,
                                e2eeKey = null,
                                e2eeOn = false,
                            ),
                        )
                    }
                    startActivity(intent)
                },
                onReset = {
                    viewModel.reset()
                    Toast.makeText(this@MainActivity, "凭证已清空", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    @Composable
    fun MainContent(
        defaultUrl: String,
        defaultToken: String,
        onConnect: (url: String, token: String) -> Unit,
        onReset: () -> Unit,
    ) {
        LKMeetAppTheme(darkTheme = true) {
            var url by remember { mutableStateOf(defaultUrl) }
            var token by remember { mutableStateOf(defaultToken) }
            val scrollState = rememberScrollState()

            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.verticalScroll(scrollState)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 30.dp)
                            .fillMaxWidth(),
                    ) {
                        // 1. 顶部留白和标题（替代了原来的 Image 图标）
                        Spacer(modifier = Modifier.height(100.dp))
                        
                        Text(
                            text = "恩信共享助手",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A84FF), // 商务蓝
                            letterSpacing = 2.sp
                        )
                        
                        Text(
                            text = "专业屏幕共享·高效协同",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(80.dp))

                        // 2. 核心输入框：凭证输入
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("通话凭证") },
                            placeholder = { Text("请粘贴共享凭证") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        // 3. 开启按钮：点击即进入
                        Button(
                            onClick = {
                                if (token.isNotBlank()) {
                                    onConnect(url, token)
                                } else {
                                    Toast.makeText(this@MainActivity, "请先输入凭证", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                        ) {
                            Text("发起共享请求", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 4. 重置按钮：一键清空
                        TextButton(
                            onClick = {
                                onReset()
                                token = "" // 同时也清空当前页面的输入框
                            },
                        ) {
                            Text("重置凭证", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
