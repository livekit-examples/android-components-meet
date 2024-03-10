package io.livekit.android.compose.meet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SendMessageDialog(
    onDismissRequest: () -> Unit,
    onSendMessage: (String) -> Unit,
) {

    var messageToSend by rememberSaveable {
        mutableStateOf("")
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Send Message")
        },
        text = {
            OutlinedTextField(
                value = messageToSend,
                onValueChange = { messageToSend = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSendMessage(messageToSend)
                },
            ) { Text("Send") }
        },
        dismissButton = {
            Button(
                onClick = onDismissRequest,
            ) { Text("Cancel") }
        },
        containerColor = Color.Black,
    )
}