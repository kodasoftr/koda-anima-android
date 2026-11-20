package com.example.model

import java.util.UUID

/**
 * Local user account representation.
 * Tied to the device's persistent ID, with custom display name and avatar photo.
 */
data class UserAccount(
    val id: String = generateDeviceId(),
    val name: String = "Animador ${id.takeLast(4)}",
    val avatarUri: String? = null,
    val avatarBase64: String? = null,
    val avatarColor: Long = 0xFF00E5FF,
    val customCursorBase64: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun generateDeviceId(): String {
            val randomPart = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
            return "KD-$randomPart"
        }
    }
}

/**
 * Saved friend in the user's friend list.
 */
data class FriendEntry(
    val id: String,
    val name: String,
    val avatarUri: String? = null,
    val avatarBase64: String? = null,
    val avatarColor: Long = 0xFF987EDC,
    val status: FriendStatus = FriendStatus.ONLINE,
    val currentRoomId: String? = null,
    val lastKnownIp: String? = null,
    val lastKnownControlPort: Int = 0,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class FriendStatus(val labelPt: String) {
    ONLINE("Disponível"),
    IN_ROOM("Em Sala Colaborativa"),
    OFFLINE("Offline")
}

/**
 * Information about a connected collaborator in the active session.
 */
data class ConnectedFriendInfo(
    val id: String,
    val name: String,
    val avatarUri: String? = null,
    val avatarBase64: String? = null,
    val avatarColor: Long = 0xFF00E5FF,
    val customCursorBase64: String? = null,
    val isHost: Boolean = false,
    val pingMs: Int = 12
)

/**
 * Incoming friend request notification.
 */
data class FriendRequest(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val senderAvatarUri: String? = null,
    val senderAvatarBase64: String? = null,
    val senderAvatarColor: Long = 0xFFBE699A,
    val senderIp: String? = null,
    val senderControlPort: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Information about an active room discovered on the local network (Wi-Fi/LAN/Hotspot).
 */
data class RemoteRoomInfo(
    val roomId: String,
    val roomName: String,
    val hostId: String,
    val hostName: String,
    val hostAvatarUri: String? = null,
    val hostAvatarBase64: String? = null,
    val hostAvatarColor: Long = 0xFF00E5FF,
    val hostIp: String,
    val hostPort: Int = 8765,
    val frameCount: Int = 1,
    val fps: Int = 12,
    val connectedPeerCount: Int = 1,
    val lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Incoming room invite notification triggered when a friend selects a project and taps "Convidar".
 */
data class RoomInviteNotification(
    val id: String = UUID.randomUUID().toString(),
    val roomInfo: RemoteRoomInfo,
    val inviterAvatarBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Live position of a remote collaborator's stylus / cursor on the canvas.
 */
data class RemotePeerCursor(
    val peerId: String,
    val peerName: String,
    val color: Long,
    val frameIndex: Int,
    val x: Float,
    val y: Float,
    val isDrawing: Boolean = false,
    val lastUpdate: Long = System.currentTimeMillis()
)

/**
 * In-progress remote stroke preview streamed while a collaborator's pen is down.
 */
data class RemoteLiveStroke(
    val peerId: String,
    val frameIndex: Int,
    val layerIndex: Int,
    val stroke: DrawingStroke,
    val lastUpdate: Long = System.currentTimeMillis(),
    val isLocalLayer: Boolean = false
)

/**
 * Knock / Entry request sent from a friend trying to join the host's room.
 */
data class JoinKnockRequest(
    val connectionId: String = UUID.randomUUID().toString(),
    val clientIp: String,
    val clientPort: Int,
    val user: UserAccount,
    val roomId: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MultiplayerRole {
    NONE,
    HOST,
    CLIENT
}

enum class ConnectionStatus(val labelPt: String) {
    DISCONNECTED("Desconectado"),
    DISCOVERING("Buscando Salas na Rede..."),
    CONNECTING("Conectando ao Servidor..."),
    KNOCKING("Aguardando Amigo Permitir Entrada..."),
    CONNECTED("Conectado em Tempo Real"),
    REJECTED("Entrada Não Permitida pelo Anfitrião"),
    ERROR("Falha de Conexão")
}
