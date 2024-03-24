package io.livekit.android.compose.meet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private val controlSize = 40.dp
private val controlPadding = 4.dp

@Composable
fun ControlButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(controlSize)
            .padding(controlPadding),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White,
        )
    }
}

@Composable
fun ControlButton(
    resourceId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ControlButton(
        painter = painterResource(id = resourceId),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier
    )
}