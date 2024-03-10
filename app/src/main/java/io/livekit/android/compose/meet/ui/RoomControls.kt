package io.livekit.android.compose.meet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.livekit.android.compose.meet.R

@Composable
fun RoomControls(
    micEnabled: Boolean,
    setMicEnabled: (Boolean) -> Unit,
    cameraEnabled: Boolean,
    setCameraEnabled: (Boolean) -> Unit,
    flipCamera: () -> Unit,
    screencastEnabled: Boolean,
    setScreencastEnabled: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    // Control bar for any switches such as mic/camera enable/disable.
    Column(
        modifier = modifier.padding(top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val controlSize = 40.dp
        val controlPadding = 4.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                onClick = { setMicEnabled(!micEnabled) },
                modifier = Modifier
                    .size(controlSize)
                    .padding(controlPadding),
            ) {
                val resource =
                    if (micEnabled) R.drawable.outline_mic_24 else R.drawable.outline_mic_off_24
                Icon(
                    painterResource(id = resource),
                    contentDescription = "Mic",
                    tint = Color.White,
                )
            }
            Surface(
                onClick = { setCameraEnabled(!cameraEnabled) },
                modifier = Modifier
                    .size(controlSize)
                    .padding(controlPadding),
            ) {
                val resource =
                    if (cameraEnabled) R.drawable.outline_videocam_24 else R.drawable.outline_videocam_off_24
                Icon(
                    painterResource(id = resource),
                    contentDescription = "Video",
                    tint = Color.White,
                )
            }
            Surface(
                onClick = { flipCamera() },
                modifier = Modifier
                    .size(controlSize)
                    .padding(controlPadding),
            ) {
                Icon(
                    painterResource(id = R.drawable.outline_flip_camera_android_24),
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                )
            }
            Surface(
                onClick = {
                    setScreencastEnabled(!screencastEnabled)
                },
                modifier = Modifier
                    .size(controlSize)
                    .padding(controlPadding),
            ) {
                val resource =
                    if (screencastEnabled) R.drawable.baseline_cast_connected_24 else R.drawable.baseline_cast_24
                Icon(
                    painterResource(id = resource),
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                )
            }

            var showMessageDialog by remember { mutableStateOf(false) }
            Surface(
                onClick = { showMessageDialog = true },
                modifier = Modifier
                    .size(controlSize)
                    .padding(controlPadding),
            ) {
                Icon(
                    painterResource(id = R.drawable.baseline_chat_24),
                    contentDescription = "Send Message",
                    tint = Color.White,
                )
            }

            if (showMessageDialog) {
                SendMessageDialog(
                    onDismissRequest = { showMessageDialog = false },
                    onSendMessage = { message -> onSendMessage(message) })
            }
            Surface(
                onClick = { onExitClick() },
                modifier = Modifier
                    .size(controlSize)
                    .padding(controlPadding),
            ) {
                Icon(
                    painterResource(id = R.drawable.ic_baseline_cancel_24),
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                )
            }
        }
    }
}