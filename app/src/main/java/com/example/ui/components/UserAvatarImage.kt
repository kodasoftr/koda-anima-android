package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AvatarTransferHelper

/**
 * Shared avatar renderer that prioritizes peer-transferred Base64 thumbnails and falls back
 * cleanly to local URIs or an initial/person placeholder.
 */
@Composable
fun UserAvatarImage(
    name: String,
    avatarUri: String?,
    avatarBase64: String?,
    avatarColor: Long,
    size: Dp = 40.dp,
    borderColor: Color = Color(avatarColor),
    borderWidth: Dp = 1.5.dp,
    useInitialFallback: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val decodedBitmap = remember(avatarUri, avatarBase64) {
        AvatarTransferHelper.decodeAvatarBitmap(context, avatarUri, avatarBase64)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(avatarColor).copy(alpha = 0.28f))
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (decodedBitmap != null && !decodedBitmap.isRecycled) {
            Image(
                bitmap = decodedBitmap.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (useInitialFallback && name.isNotBlank()) {
            Text(
                text = name.trim().take(1).uppercase(),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = name,
                tint = Color(avatarColor),
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
