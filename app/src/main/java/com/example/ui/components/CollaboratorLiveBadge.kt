package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedFriendInfo

/**
 * Top-left live collaborator profile badge.
 * Displays the connected friend's profile picture, their name underneath,
 * and real-time LAN ping (latency in ms) for both Host and Client.
 */
@Composable
fun CollaboratorLiveBadge(
    friend: ConnectedFriendInfo?,
    pingMs: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = friend != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (friend == null) return@AnimatedVisibility

        val pingColor = when {
            pingMs in 1..45 -> Color(0xFF00E676) // Green
            pingMs in 46..120 -> Color(0xFFFFD600) // Yellow
            else -> Color(0xFFFF9100) // Orange
        }

        Surface(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, Color(friend.avatarColor), RoundedCornerShape(12.dp))
                .testTag("collaborator_live_badge"),
            color = Color(0xFF140A20).copy(alpha = 0.94f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Profile Photo / Avatar at the top
                UserAvatarImage(
                    name = friend.name,
                    avatarUri = friend.avatarUri,
                    avatarBase64 = friend.avatarBase64,
                    avatarColor = friend.avatarColor,
                    size = 34.dp,
                    borderColor = pingColor,
                    borderWidth = 1.5.dp
                )

                // Friend / Host Name underneath the photo
                Text(
                    text = friend.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Role and Ping indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pingColor)
                    )
                    Text(
                        text = if (pingMs > 0) "${pingMs}ms" else "<1ms",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pingColor
                    )
                    Text(
                        text = if (friend.isHost) "• Dono" else "• Amigo",
                        fontSize = 9.sp,
                        color = Color(0xFFEDE7F8).copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
