package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.FileImportExportManager
import com.example.engine.GifEncoder
import com.example.engine.KodanimaGraphicsEngine
import com.example.engine.ProjectStorageManager
import com.example.engine.SampleProjects
import com.example.model.AnimationProject
import com.example.model.AppPreferences
import com.example.model.AppUiMode
import com.example.model.DrawingStroke
import com.example.model.Frame
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.model.LayerImageAttachment
import com.example.model.OnionSkinSettings
import android.net.Uri
import com.example.engine.MediaAssetManager
import com.example.model.PressureCurve
import com.example.model.PressureMode
import com.example.model.ProjectResolution
import com.example.model.ReferenceImageState
import com.example.model.SelectionRegion
import com.example.model.StrokePoint
import com.example.model.TabletDriverSettings
import com.example.model.ToolType
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

import com.example.engine.BluetoothCoopManager
import com.example.engine.BluetoothPeerItem
import com.example.engine.CursorHelper
import com.example.engine.HardwareCapabilitiesEngine
import com.example.engine.KodanimaBitmapPool
import com.example.engine.KodanimaDispatchers
import com.example.engine.KritaBrushImporter
import com.example.engine.LocalServerBridge
import com.example.engine.LocalServerForegroundService
import com.example.engine.MultiplayerStudioEngine
import com.example.engine.PreferencesStorageManager
import com.example.engine.ProjectCompressionManager
import com.example.engine.SavedProjectEntry
import com.example.engine.SelectionGeometryHelper
import com.example.engine.WifiDirectP2pHelper
import com.example.engine.WifiDirectPeerItem
import com.example.model.BezierNode
import com.example.model.BrushPreset
import com.example.model.AppScreen
import com.example.model.FriendBrushOffer
import com.example.model.GestureAction
import com.example.model.GridRulerSettings
import com.example.model.KeyboardShortcutBinding
import com.example.model.MultiFingerGesture
import com.example.model.NetworkTransportMode
import com.example.model.PerspectiveRulerSettings
import com.example.model.RemoteLiveStroke
import com.example.model.ShortcutAction
import com.example.model.ShortcutRegistry
import com.example.model.TouchGestureRegistry
import com.example.model.sampleCubicBezierNodesToStrokePoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class EditorDialog {
    NONE,
    PROJECT_SETTINGS,
    LAYERS,
    COLOR_PICKER,
    EXPORT,
    NEW_PROJECT,
    PROJECTS_LIST
}

sealed class ExportState {
    object Idle : ExportState()
    data class Progress(val message: String, val percent: Float) : ExportState()
    data class Success(val message: String, val filename: String) : ExportState()
    data class Error(val error: String) : ExportState()
}

data class EditorUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val preferences: AppPreferences = AppPreferences(),
    val savedProjects: List<AnimationProject> = emptyList(),
    val savedProjectEntries: List<SavedProjectEntry> = emptyList(),
    val project: AnimationProject = AnimationProject(name = "Novo Projeto"),
    val currentFrameIndex: Int = 0,
    val activeLayerIndex: Int = 1,
    val selectedTool: ToolType = ToolType.PEN,
    val activeBrushPreset: BrushPreset = BrushPreset.ALL_12_PRESETS[0],
    val currentColor: Long = 0xFF00D2FF, // Kodanima Cyan
    val brushSize: Float = 6f,
    val brushOpacity: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = true,
    val isStylusOnlyMode: Boolean = false,
    val onionSkinEnabled: Boolean = true,
    val mirrorCanvasHorizontal: Boolean = false,
    val activeDialog: EditorDialog = EditorDialog.NONE,
    val exportState: ExportState = ExportState.Idle,
    val referenceImage: ReferenceImageState = ReferenceImageState(),
    val zoom: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotation: Float = 0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String? = null,
    val recentColors: List<Long> = listOf(
        0xFF00D2FF, 0xFFFF7A00, 0xFFFF3366, 0xFF00E676,
        0xFFFFFFFF, 0xFF12131A, 0xFFFFD700, 0xFF7C4DFF
    ),
    val activeSelection: com.example.model.SelectionRegion = com.example.model.SelectionRegion(),
    val isZenModeActive: Boolean = false,
    val timelineZoom: Float = 1.0f,
    val importedKritaBrushes: List<BrushPreset> = emptyList(),
    val nativeBrushPresets: List<BrushPreset> = BrushPreset.ALL_12_PRESETS,
    val isBrushStudioOpen: Boolean = false,
    val bezierNodes: List<BezierNode> = emptyList(),
    val isBezierClosed: Boolean = false,
    val isVideoTransformMode: Boolean = false,
    val activeScrubTimeMs: Long? = null,
    // Multiplayer & Local Server State
    val userAccount: com.example.model.UserAccount = com.example.model.UserAccount(),
    val friendsList: List<com.example.model.FriendEntry> = emptyList(),
    val friendRequests: List<com.example.model.FriendRequest> = emptyList(),
    val multiplayerRole: com.example.model.MultiplayerRole = com.example.model.MultiplayerRole.NONE,
    val connectionStatus: com.example.model.ConnectionStatus = com.example.model.ConnectionStatus.DISCONNECTED,
    val activeRoomInfo: com.example.model.RemoteRoomInfo? = null,
    val discoveredRooms: List<com.example.model.RemoteRoomInfo> = emptyList(),
    val pendingKnockRequest: com.example.model.JoinKnockRequest? = null,
    val remoteCursors: Map<String, com.example.model.RemotePeerCursor> = emptyMap(),
    val remoteLiveStrokes: Map<String, RemoteLiveStroke> = emptyMap(),
    val connectedPeerCount: Int = 1,
    val isLocalServerActive: Boolean = false,
    val connectedFriend: com.example.model.ConnectedFriendInfo? = null,
    val connectedFriends: List<com.example.model.ConnectedFriendInfo> = emptyList(),
    val currentPingMs: Int = 0,
    val incomingFriendRequestModal: com.example.model.FriendRequest? = null,
    val incomingRoomInviteModal: com.example.model.RoomInviteNotification? = null,
    val hostShutdownAlertMessage: String? = null,
    val wifiDirectPeers: List<WifiDirectPeerItem> = emptyList(),
    val bluetoothPeers: List<BluetoothPeerItem> = emptyList(),
    val gridRulerSettings: GridRulerSettings = GridRulerSettings(),
    val perspectiveRulerSettings: PerspectiveRulerSettings = PerspectiveRulerSettings(),
    val pendingFriendBrushOffer: FriendBrushOffer? = null,
    val isQuickGestureMenuOpen: Boolean = false,
    val isFrameLayersPanelOpen: Boolean = false,
    val activeLocalLayerIndex: Int = 0,
    val activeTargetIsFrameLocalLayer: Boolean = false,
    val pendingReferenceImageUri: String? = null
)

class KodanimaViewModel : ViewModel() {

    private sealed class CanvasOp {
        abstract val frameId: String

        data class AddStroke(
            val authorId: String,
            override val frameId: String,
            val layerIndex: Int,
            val stroke: DrawingStroke,
            val isLocalLayer: Boolean = false
        ) : CanvasOp()

        data class RemoveStrokes(
            val authorId: String,
            override val frameId: String,
            val layerIndex: Int,
            val removedStrokes: List<DrawingStroke>,
            val isLocalLayer: Boolean = false
        ) : CanvasOp()

        data class ReplaceStrokes(
            val authorId: String,
            override val frameId: String,
            val layerIndex: Int,
            val oldStrokes: List<DrawingStroke>,
            val newStrokes: List<DrawingStroke>,
            val isLocalLayer: Boolean = false
        ) : CanvasOp()

        data class ClearFrame(
            val authorId: String,
            override val frameId: String,
            val previousLayers: List<Layer>,
            val previousLocalLayers: List<Layer> = emptyList()
        ) : CanvasOp()

        data class FrameSnapshot(
            override val frameId: String,
            val previousFrame: Frame
        ) : CanvasOp()
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val perFrameUndoOps = mutableMapOf<String, MutableList<CanvasOp>>()
    private val perFrameRedoOps = mutableMapOf<String, MutableList<CanvasOp>>()
    private val perFrameLocalTarget = mutableMapOf<String, Pair<Boolean, Int>>()
    private val maxHistorySize: Int
        get() = HardwareCapabilitiesEngine.currentProfile.maxUndoHistory

    private var playbackJob: Job? = null
    private var debouncedHostSaveJob: Job? = null
    private var appContext: Context? = null
    private var clipboardStrokes: List<DrawingStroke> = emptyList()
    private var isContextInitialized = false
    private var autoJoinOnWifiDirectConnect = false
    private val sentBrushFingerprints = mutableSetOf<String>()
    private val dismissedFriendBrushKeys = mutableSetOf<String>()

    private val multiplayerEngine = MultiplayerStudioEngine(viewModelScope)
    private var wifiDirectHelper: WifiDirectP2pHelper? = null
    private var bluetoothCoopManager: BluetoothCoopManager? = null

    init {
        setupMultiplayerCallbacks()
        LocalServerBridge.onStopCallback = {
            viewModelScope.launch(Dispatchers.Main) {
                stopMultiplayerSession()
            }
        }
        viewModelScope.launch {
            LocalServerBridge.stopRequestedEvents.collect {
                stopMultiplayerSession()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LocalServerBridge.onStopCallback = null
        playbackJob?.cancel()
        debouncedHostSaveJob?.cancel()
        MediaAssetManager.releaseAll()
        wifiDirectHelper?.stop()
        bluetoothCoopManager?.stopAll()
        multiplayerEngine.stopAll()
        appContext?.let { LocalServerForegroundService.stop(it) }
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        KodanimaBitmapPool.clear()
        appContext = null
    }

    private fun saveCurrentFrameTargetPreference(state: EditorUiState) {
        val currFrame = state.project.frames.getOrNull(state.currentFrameIndex) ?: return
        if (state.activeTargetIsFrameLocalLayer && currFrame.localLayers.isNotEmpty()) {
            perFrameLocalTarget[currFrame.id] = true to state.activeLocalLayerIndex.coerceIn(0, currFrame.localLayers.lastIndex)
        } else {
            perFrameLocalTarget[currFrame.id] = false to 0
        }
    }

    private fun resolveTargetStateForFrame(
        targetFrame: Frame?,
        fallbackActiveLocalIdx: Int,
        fallbackIsLocal: Boolean
    ): Pair<Boolean, Int> {
        if (targetFrame == null || targetFrame.localLayers.isEmpty()) return false to 0
        val remembered = perFrameLocalTarget[targetFrame.id]
        return if (remembered != null) {
            val useLocal = remembered.first
            val idx = if (useLocal) remembered.second.coerceIn(0, targetFrame.localLayers.lastIndex) else 0
            useLocal to idx
        } else {
            val safeIdx = fallbackActiveLocalIdx.coerceIn(0, targetFrame.localLayers.lastIndex)
            fallbackIsLocal to safeIdx
        }
    }

    private fun setupMultiplayerCallbacks() {
        multiplayerEngine.onKnockReceived = { req ->
            _uiState.update { it.copy(pendingKnockRequest = req) }
        }
        multiplayerEngine.onDiscoveredRoomsUpdated = { rooms ->
            val myId = _uiState.value.userAccount.id
            val myRoomId = _uiState.value.activeRoomInfo?.roomId
            val filtered = rooms.filterNot { r ->
                r.hostId.equals(myId, ignoreCase = true) ||
                    (!myRoomId.isNullOrBlank() && r.roomId == myRoomId)
            }
            _uiState.update { it.copy(discoveredRooms = filtered) }
        }
        multiplayerEngine.onStrokeReceived = { frameIndex, layerIndex, stroke, isLocalLayer ->
            receiveRemoteStroke(frameIndex, layerIndex, stroke, isLocalLayer)
        }
        multiplayerEngine.onClearFrameReceived = { frameIndex ->
            applyRemoteClearFrame(frameIndex)
        }
        multiplayerEngine.onAddFrameReceived = { insertAt ->
            applyRemoteAddFrame(insertAt)
        }
        multiplayerEngine.onAddLayerReceived = { layerName ->
            applyRemoteAddLayer(layerName)
        }
        multiplayerEngine.onFrameLocalLayersReceived = { frameIndex, localLayers ->
            applyRemoteFrameLocalLayers(frameIndex, localLayers)
        }
        multiplayerEngine.onRemoteCursorUpdated = { cursor ->
            _uiState.update { state ->
                val updated = state.remoteCursors.toMutableMap()
                updated[cursor.peerId] = cursor
                state.copy(remoteCursors = updated)
            }
        }
        multiplayerEngine.onPeerLeft = { leftPeerId ->
            _uiState.update { state ->
                val updatedCursors = state.remoteCursors.toMutableMap()
                updatedCursors.remove(leftPeerId)
                val updatedLive = state.remoteLiveStrokes.toMutableMap()
                updatedLive.remove(leftPeerId)
                state.copy(
                    remoteCursors = updatedCursors,
                    remoteLiveStrokes = updatedLive
                )
            }
        }
        multiplayerEngine.onRemoteLiveStrokeUpdated = { peerId, frameIndex, layerIndex, stroke, isLocalLayer ->
            _uiState.update { state ->
                val updatedLive = state.remoteLiveStrokes.toMutableMap()
                if (stroke == null || stroke.points.isEmpty()) {
                    updatedLive.remove(peerId)
                } else {
                    updatedLive[peerId] = RemoteLiveStroke(
                        peerId = peerId,
                        frameIndex = frameIndex,
                        layerIndex = layerIndex,
                        stroke = stroke,
                        isLocalLayer = isLocalLayer
                    )
                }
                state.copy(remoteLiveStrokes = updatedLive)
            }
        }
        multiplayerEngine.onRemoteUndoRedoOpReceived = { opType, authorId, frameIndex, layerIndex, strokeIds, strokes, isLocalLayer ->
            applyRemoteUndoRedoOp(opType, authorId, frameIndex, layerIndex, strokeIds, strokes, isLocalLayer)
        }
        multiplayerEngine.onPeerCursorIconUpdated = { peerId, cursorB64 ->
            _uiState.update { state ->
                val updatedFriends = state.connectedFriends.map { friend ->
                    if (friend.id == peerId) friend.copy(customCursorBase64 = cursorB64) else friend
                }
                val updatedPrimary = state.connectedFriend?.let { primary ->
                    if (primary.id == peerId) primary.copy(customCursorBase64 = cursorB64) else primary
                }
                state.copy(
                    connectedFriends = updatedFriends,
                    connectedFriend = updatedPrimary
                )
            }
        }
        multiplayerEngine.onBrushDefinitionReceived = { senderId, senderName, preset ->
            handleIncomingBrushDefinition(senderId, senderName, preset)
        }
        multiplayerEngine.onProjectSynced = { syncedProj ->
            perFrameUndoOps.clear()
            perFrameRedoOps.clear()
            perFrameLocalTarget.clear()
            _uiState.update {
                it.copy(
                    project = syncedProj,
                    currentFrameIndex = 0,
                    activeLayerIndex = (syncedProj.frames.firstOrNull()?.layers?.size ?: 1) - 1,
                    connectionStatus = com.example.model.ConnectionStatus.CONNECTED,
                    currentScreen = AppScreen.EDITOR,
                    canUndo = false,
                    canRedo = false,
                    remoteCursors = emptyMap(),
                    remoteLiveStrokes = emptyMap()
                )
            }
        }
        multiplayerEngine.onConnected = {
            _uiState.update {
                it.copy(
                    connectionStatus = com.example.model.ConnectionStatus.CONNECTED,
                    statusMessage = "Conectado à sala colaborativa!"
                )
            }
        }
        multiplayerEngine.onFriendConnected = { friend ->
            _uiState.update { state ->
                val updatedFriends = state.friendsList.map { f ->
                    if (f.id.equals(friend.id, ignoreCase = true)) {
                        f.copy(
                            name = friend.name,
                            avatarUri = friend.avatarUri ?: f.avatarUri,
                            avatarBase64 = friend.avatarBase64 ?: f.avatarBase64,
                            avatarColor = friend.avatarColor,
                            status = com.example.model.FriendStatus.IN_ROOM
                        )
                    } else f
                }
                appContext?.let { ctx -> PreferencesStorageManager.saveFriendsList(ctx, updatedFriends) }
                val existingList = state.connectedFriends.filterNot { it.id == friend.id } + friend
                state.copy(
                    friendsList = updatedFriends,
                    connectedFriend = friend,
                    connectedFriends = existingList,
                    statusMessage = "${friend.name} está conectado no Canvas!"
                )
            }
        }
        multiplayerEngine.onConnectedPeersUpdated = { peers ->
            _uiState.update { state ->
                val activeIds = peers.map { it.id }.toSet()
                val prunedCursors = if (state.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
                    state.remoteCursors.filterKeys { it in activeIds }
                } else emptyMap()
                val prunedLive = if (state.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
                    state.remoteLiveStrokes.filterKeys { it in activeIds }
                } else emptyMap()
                state.copy(
                    connectedFriends = peers,
                    connectedFriend = peers.firstOrNull(),
                    remoteCursors = prunedCursors,
                    remoteLiveStrokes = prunedLive
                )
            }
        }
        multiplayerEngine.onHostShutdown = { msg ->
            handleHostShutdownOnVisitor(msg)
        }
        multiplayerEngine.onPingUpdated = { ping ->
            _uiState.update { it.copy(currentPingMs = ping) }
        }
        multiplayerEngine.onPeerInfoUpdated = { peer ->
            _uiState.update { state ->
                val exists = state.friendsList.any {
                    it.id.equals(peer.id, ignoreCase = true) ||
                        it.name.equals(peer.name, ignoreCase = true)
                }
                if (!exists) return@update state
                val updatedFriends = state.friendsList.map { existing ->
                    if (existing.id.equals(peer.id, ignoreCase = true) ||
                        existing.name.equals(peer.name, ignoreCase = true)
                    ) {
                        existing.copy(
                            id = peer.id,
                            name = peer.name,
                            avatarUri = peer.avatarUri ?: existing.avatarUri,
                            avatarBase64 = peer.avatarBase64 ?: existing.avatarBase64,
                            avatarColor = peer.avatarColor,
                            status = peer.status,
                            currentRoomId = peer.currentRoomId,
                            lastKnownIp = peer.lastKnownIp ?: existing.lastKnownIp,
                            lastKnownControlPort = if (peer.lastKnownControlPort > 0) peer.lastKnownControlPort else existing.lastKnownControlPort
                        )
                    } else existing
                }
                appContext?.let { ctx -> PreferencesStorageManager.saveFriendsList(ctx, updatedFriends) }
                state.copy(friendsList = updatedFriends)
            }
        }
        multiplayerEngine.onFriendRequestReceived = { req, targetUserId, targetName ->
            val myAccount = _uiState.value.userAccount
            val isForMe = targetUserId.isBlank() || targetUserId.equals(myAccount.id, ignoreCase = true) ||
                (targetName.isNotBlank() && myAccount.name.contains(targetName, ignoreCase = true))
            if (isForMe) {
                _uiState.update { state ->
                    val updated = state.friendRequests.filterNot {
                        it.id == req.id || it.senderId.equals(req.senderId, ignoreCase = true)
                    } + req
                    state.copy(
                        friendRequests = updated,
                        incomingFriendRequestModal = req
                    )
                }
                appContext?.let { ctx ->
                    PreferencesStorageManager.saveFriendRequests(ctx, _uiState.value.friendRequests)
                }
            }
        }
        multiplayerEngine.onFriendAccepted = { friend, targetUserId ->
            val myAccount = _uiState.value.userAccount
            if (targetUserId.isBlank() || targetUserId.equals(myAccount.id, ignoreCase = true)) {
                _uiState.update { state ->
                    val updated = state.friendsList.filterNot {
                        it.id.equals(friend.id, ignoreCase = true) || it.name.equals(friend.name, ignoreCase = true)
                    } + friend
                    state.copy(
                        friendsList = updated,
                        statusMessage = "${friend.name} aceitou sua solicitação de amizade!"
                    )
                }
                appContext?.let { ctx ->
                    PreferencesStorageManager.saveFriendsList(ctx, _uiState.value.friendsList)
                }
            }
        }
        multiplayerEngine.onRoomInviteReceived = { invite, targetUserId, targetName ->
            val myAccount = _uiState.value.userAccount
            val isForMe = targetUserId.isBlank() ||
                targetUserId.equals(myAccount.id, ignoreCase = true) ||
                (targetName.isNotBlank() && myAccount.name.equals(targetName, ignoreCase = true))
            if (isForMe) {
                _uiState.update { state ->
                    state.copy(
                        incomingRoomInviteModal = invite,
                        statusMessage = "${invite.roomInfo.hostName} convidou você para o projeto ${invite.roomInfo.roomName}!"
                    )
                }
            }
        }
        multiplayerEngine.onDisconnected = { reason ->
            sentBrushFingerprints.clear()
            dismissedFriendBrushKeys.clear()
            _uiState.update {
                it.copy(
                    connectionStatus = com.example.model.ConnectionStatus.DISCONNECTED,
                    multiplayerRole = com.example.model.MultiplayerRole.NONE,
                    isLocalServerActive = false,
                    connectedFriend = null,
                    connectedFriends = emptyList(),
                    remoteCursors = emptyMap(),
                    remoteLiveStrokes = emptyMap(),
                    pendingFriendBrushOffer = null,
                    currentPingMs = 0,
                    statusMessage = reason
                )
            }
        }
        multiplayerEngine.onPeerCountChanged = { count ->
            _uiState.update { it.copy(connectedPeerCount = count) }
            val state = _uiState.value
            if (state.isLocalServerActive) {
                val room = state.activeRoomInfo
                appContext?.let { ctx ->
                    LocalServerForegroundService.startOrUpdate(
                        ctx,
                        room?.roomId ?: "KD-ROOM",
                        state.project.name,
                        transportLabel(state.preferences.networkTransportMode),
                        count
                    )
                }
            }
        }
    }

    private fun transportLabel(mode: NetworkTransportMode): String = when (mode) {
        NetworkTransportMode.WIFI_LAN -> "Wi-Fi 2.4G / 5G"
        NetworkTransportMode.WIFI_DIRECT -> "Wi-Fi Direct"
        NetworkTransportMode.BLUETOOTH -> "Bluetooth Offline"
    }

    private fun ensureTransportHelpers(ctx: Context) {
        if (wifiDirectHelper == null) {
            wifiDirectHelper = WifiDirectP2pHelper(
                context = ctx,
                onPeersChanged = { peers ->
                    _uiState.update { it.copy(wifiDirectPeers = peers) }
                },
                onP2pConnected = { isGroupOwner, groupOwnerIp ->
                    if (isGroupOwner) {
                        _uiState.update {
                            it.copy(statusMessage = "Grupo Wi-Fi Direct pronto ($groupOwnerIp). Aguardando amigo!")
                        }
                        multiplayerEngine.startDiscovery(_uiState.value.userAccount, forceSubnetScan = false)
                    } else {
                        val shouldAutoJoin = autoJoinOnWifiDirectConnect
                        autoJoinOnWifiDirectConnect = false
                        viewModelScope.launch {
                            multiplayerEngine.exchangePeerHelloOverTcp(
                                groupOwnerIp,
                                MultiplayerStudioEngine.DEFAULT_CONTROL_PORT
                            )
                            multiplayerEngine.startDiscovery(_uiState.value.userAccount, forceSubnetScan = false)
                            if (shouldAutoJoin) {
                                val matchedRoom = _uiState.value.discoveredRooms.firstOrNull {
                                    it.hostIp == groupOwnerIp
                                } ?: com.example.model.RemoteRoomInfo(
                                    roomId = "WFD-$groupOwnerIp",
                                    roomName = "Sala Wi-Fi Direct",
                                    hostId = "WFD_HOST",
                                    hostName = "Parceiro Wi-Fi Direct",
                                    hostAvatarColor = 0xFF00E5FF,
                                    hostIp = groupOwnerIp,
                                    hostPort = MultiplayerStudioEngine.DEFAULT_TCP_PORT
                                )
                                joinRoom(matchedRoom)
                            }
                        }
                    }
                },
                onStatusMessage = { msg ->
                    _uiState.update { it.copy(statusMessage = msg) }
                }
            )
        }
        if (bluetoothCoopManager == null) {
            bluetoothCoopManager = BluetoothCoopManager(
                context = ctx,
                scope = viewModelScope,
                onDevicesUpdated = { devices ->
                    _uiState.update { it.copy(bluetoothPeers = devices) }
                },
                onIncomingClientConnected = { input, output, remoteName, closeHandle ->
                    multiplayerEngine.attachExternalHostClientStream(
                        inputStream = input,
                        outputStream = output,
                        remoteLabel = remoteName,
                        remotePort = 0,
                        roomId = _uiState.value.activeRoomInfo?.roomId ?: "BT-ROOM",
                        closeHandle = closeHandle
                    )
                },
                onClientConnectedToHost = { input, output, remoteName, closeHandle ->
                    val account = _uiState.value.userAccount
                    val btRoom = com.example.model.RemoteRoomInfo(
                        roomId = "BT-${remoteName.take(8)}",
                        roomName = "Sala Bluetooth",
                        hostId = "BT_HOST",
                        hostName = remoteName,
                        hostAvatarColor = 0xFF00E5FF,
                        hostIp = "Bluetooth",
                        hostPort = 0
                    )
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                multiplayerRole = com.example.model.MultiplayerRole.CLIENT,
                                connectionStatus = com.example.model.ConnectionStatus.KNOCKING,
                                activeRoomInfo = btRoom,
                                statusMessage = "Conectado via Bluetooth com $remoteName! Aguardando permissão..."
                            )
                        }
                    }
                    multiplayerEngine.connectViaExternalStream(
                        inputStream = input,
                        outputStream = output,
                        user = account,
                        roomId = btRoom.roomId,
                        closeHandle = closeHandle
                    )
                },
                onStatusMessage = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update { it.copy(statusMessage = msg) }
                    }
                }
            )
        }
    }

    fun initializeWithContext(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        multiplayerEngine.bindContext(ctx)
        ensureTransportHelpers(ctx)

        val currentPrefs = if (isContextInitialized) {
            _uiState.value.preferences
        } else {
            PreferencesStorageManager.loadPreferences(ctx)
        }

        val entries = ProjectCompressionManager.listAllSavedProjects(ctx, currentPrefs.customProjectsDirectory)
        val projects = if (entries.isNotEmpty()) entries.map { it.project } else ProjectStorageManager.loadAllProjects(ctx)

        if (isContextInitialized) {
            _uiState.update {
                it.copy(
                    savedProjects = projects,
                    savedProjectEntries = entries
                )
            }
            return
        }
        isContextInitialized = true

        val loadedAccount = PreferencesStorageManager.loadUserAccount(ctx)
        multiplayerEngine.updateLocalUser(loadedAccount)

        val loadedFriends = PreferencesStorageManager.loadFriendsList(ctx)
        val loadedRequests = PreferencesStorageManager.loadFriendRequests(ctx)
        val loadedKritaBrushes = com.example.engine.KritaBrushImporter.loadImportedBrushes(ctx)
        val overrides = PreferencesStorageManager.loadBrushOverrides(ctx)
        val effectiveNativePresets = BrushPreset.ALL_12_PRESETS.map { BrushPreset.applyOverride(it, overrides[it.id]) }
        val activePresetNow = effectiveNativePresets.firstOrNull { it.id == _uiState.value.activeBrushPreset.id }
            ?: _uiState.value.activeBrushPreset
        _uiState.update {
            it.copy(
                savedProjects = projects,
                savedProjectEntries = entries,
                preferences = currentPrefs,
                userAccount = loadedAccount,
                friendsList = loadedFriends,
                friendRequests = loadedRequests,
                importedKritaBrushes = loadedKritaBrushes,
                nativeBrushPresets = effectiveNativePresets,
                activeBrushPreset = activePresetNow,
                brushSize = activePresetNow.defaultSize,
                brushOpacity = activePresetNow.defaultOpacity
            )
        }

        if (!loadedAccount.avatarUri.isNullOrBlank() && loadedAccount.avatarBase64.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val (persistedUri, b64) = com.example.engine.AvatarTransferHelper.prepareLocalAvatar(
                    ctx,
                    loadedAccount.avatarUri
                )
                if (b64 != null) {
                    val syncedAcc = _uiState.value.userAccount.copy(avatarUri = persistedUri, avatarBase64 = b64)
                    PreferencesStorageManager.saveUserAccount(ctx, syncedAcc)
                    multiplayerEngine.updateLocalUser(syncedAcc)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(userAccount = syncedAcc) }
                    }
                    multiplayerEngine.broadcastAccountPresence(syncedAcc)
                }
            }
        }

        if (!currentPrefs.tabletSettings.customCursorPath.isNullOrBlank() && loadedAccount.customCursorBase64.isNullOrBlank()) {
            val cursorPath = currentPrefs.tabletSettings.customCursorPath
            viewModelScope.launch(Dispatchers.IO) {
                val cursorB64 = CursorHelper.encodeCursorFileToBase64(cursorPath)
                if (cursorB64 != null) {
                    val syncedAcc = _uiState.value.userAccount.copy(customCursorBase64 = cursorB64)
                    PreferencesStorageManager.saveUserAccount(ctx, syncedAcc)
                    multiplayerEngine.updateLocalUser(syncedAcc)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(userAccount = syncedAcc) }
                    }
                }
            }
        }

        if (currentPrefs.isMultiplayerEnabled) {
            multiplayerEngine.startDiscovery(loadedAccount)
        }
    }

    fun refreshSavedProjects(context: Context) {
        val customPath = _uiState.value.preferences.customProjectsDirectory
        val entries = ProjectCompressionManager.listAllSavedProjects(context, customPath)
        val projects = entries.map { it.project }
        _uiState.update { it.copy(savedProjects = projects, savedProjectEntries = entries) }
    }

    // --- Persistent Preferences Helper ---

    private fun updatePreferences(block: (AppPreferences) -> AppPreferences) {
        _uiState.update { state ->
            val newPrefs = block(state.preferences)
            appContext?.let { ctx ->
                PreferencesStorageManager.savePreferences(ctx, newPrefs)
            }
            state.copy(preferences = newPrefs)
        }
    }

    // --- Navigation Screens & Project Lifecycle ---

    fun navigateTo(screen: AppScreen) {
        val state = _uiState.value
        if (state.currentScreen == AppScreen.EDITOR && screen != AppScreen.EDITOR) {
            pausePlayback()
            if (state.multiplayerRole == com.example.model.MultiplayerRole.CLIENT) {
                leaveRemoteSessionAsVisitor(appContext, targetScreen = screen)
                return
            } else {
                appContext?.let { ctx ->
                    closeAndSaveCurrentProject(ctx)
                }
            }
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setUiMode(mode: AppUiMode) {
        updatePreferences { it.copy(uiMode = mode) }
    }

    fun setTheme(theme: com.example.model.KodanimaTheme) {
        updatePreferences { it.copy(theme = theme) }
    }

    fun setTimelineLayoutMode(mode: com.example.model.TimelineLayoutMode) {
        updatePreferences { it.copy(timelineLayoutMode = mode) }
    }

    fun updateTabletSettings(settings: TabletDriverSettings) {
        updatePreferences { it.copy(tabletSettings = settings) }
    }

    fun setUiScale(scale: Float) {
        updatePreferences { it.copy(uiScale = scale.coerceIn(0.65f, 1.30f)) }
    }

    fun updateKeyboardShortcuts(bindings: Map<ShortcutAction, KeyboardShortcutBinding>) {
        val merged = ShortcutRegistry.defaultMap().toMutableMap()
        bindings.forEach { (action, binding) ->
            merged[action] = binding.copy(action = action)
        }
        updatePreferences { it.copy(keyboardShortcuts = merged) }
        _uiState.update { it.copy(statusMessage = "Atalhos de teclado salvos!") }
    }

    fun resetKeyboardShortcutsToFactory(): Map<ShortcutAction, KeyboardShortcutBinding> {
        val defaults = ShortcutRegistry.defaultMap()
        updatePreferences { it.copy(keyboardShortcuts = defaults) }
        _uiState.update { it.copy(statusMessage = "Atalhos redefinidos para o padrão de fábrica.") }
        return defaults
    }

    fun updateTouchGestures(gestures: Map<MultiFingerGesture, GestureAction>) {
        val merged = TouchGestureRegistry.defaultMap().toMutableMap()
        gestures.forEach { (g, act) -> merged[g] = act }
        updatePreferences { it.copy(touchGestures = merged) }
        _uiState.update { it.copy(statusMessage = "Gestos de toque salvos!") }
    }

    fun resetTouchGesturesToFactory(): Map<MultiFingerGesture, GestureAction> {
        val defaults = TouchGestureRegistry.defaultMap()
        updatePreferences { it.copy(touchGestures = defaults) }
        _uiState.update { it.copy(statusMessage = "Gestos redefinidos para o padrão de fábrica.") }
        return defaults
    }

    fun setAutoPromptFriendBrushes(enabled: Boolean) {
        updatePreferences { it.copy(autoPromptFriendBrushes = enabled) }
        if (!enabled) {
            _uiState.update { it.copy(pendingFriendBrushOffer = null) }
        }
    }

    fun executeMultiFingerGesture(gesture: MultiFingerGesture) {
        val action = _uiState.value.preferences.touchGestures[gesture]
            ?: TouchGestureRegistry.defaultMap()[gesture]
            ?: GestureAction.DISABLED
        when (action) {
            GestureAction.UNDO -> undo()
            GestureAction.REDO -> redo()
            GestureAction.COPY_PASTE_MENU -> {
                _uiState.update { it.copy(isQuickGestureMenuOpen = !it.isQuickGestureMenuOpen) }
            }
            GestureAction.CLEAR_ACTIVE_LAYER -> clearActiveLayer()
            GestureAction.TOGGLE_ZEN_FULLSCREEN -> toggleZenMode()
            GestureAction.PREV_FRAME -> prevFrame()
            GestureAction.NEXT_FRAME -> nextFrame()
            GestureAction.RESET_CAMERA -> resetCanvasTransform()
            GestureAction.TOGGLE_GRID_RULER -> toggleGridRuler()
            GestureAction.DISABLED -> Unit
        }
    }

    fun closeQuickGestureMenu() {
        _uiState.update { it.copy(isQuickGestureMenuOpen = false) }
    }

    fun toggleGridRuler() {
        _uiState.update {
            val next = it.gridRulerSettings.copy(isEnabled = !it.gridRulerSettings.isEnabled)
            it.copy(
                gridRulerSettings = next,
                statusMessage = if (next.isEnabled) "Régua quadriculada ativada!" else "Régua quadriculada desativada."
            )
        }
    }

    fun updateGridRulerSettings(settings: GridRulerSettings) {
        _uiState.update {
            it.copy(
                gridRulerSettings = settings.copy(
                    cellSizePx = settings.cellSizePx.coerceIn(12f, 256f),
                    opacity = settings.opacity.coerceIn(0.1f, 1.0f)
                )
            )
        }
    }

    fun togglePerspectiveRuler() {
        _uiState.update {
            val next = it.perspectiveRulerSettings.copy(isEnabled = !it.perspectiveRulerSettings.isEnabled)
            it.copy(
                perspectiveRulerSettings = next,
                statusMessage = if (next.isEnabled) "Régua de perspectiva ativada (${next.vanishingPointsCount} ponto(s))!" else "Régua de perspectiva desativada."
            )
        }
    }

    fun updatePerspectiveRulerSettings(settings: PerspectiveRulerSettings) {
        _uiState.update {
            it.copy(
                perspectiveRulerSettings = settings.copy(
                    vanishingPointsCount = settings.vanishingPointsCount.coerceIn(1, 3),
                    opacity = settings.opacity.coerceIn(0.1f, 1.0f)
                )
            )
        }
    }

    fun updatePerspectiveVanishingPoint(vpIdx: Int, xRatio: Float, yRatio: Float) {
        val cx = xRatio.coerceIn(-0.4f, 1.4f)
        val cy = yRatio.coerceIn(-0.4f, 1.4f)
        _uiState.update { state ->
            val p = state.perspectiveRulerSettings
            val updated = when (vpIdx) {
                0 -> p.copy(vp1XRatio = cx, vp1YRatio = cy)
                1 -> p.copy(vp2XRatio = cx, vp2YRatio = cy)
                else -> p.copy(vp3XRatio = cx, vp3YRatio = cy)
            }
            state.copy(perspectiveRulerSettings = updated)
        }
    }

    internal fun handleIncomingBrushDefinition(senderId: String, senderName: String, preset: BrushPreset) {
        val currState = _uiState.value
        val isFromSelf = senderId.equals(currState.userAccount.id, ignoreCase = true)
        if (!isFromSelf && currState.preferences.autoPromptFriendBrushes) {
            val fp = KritaBrushImporter.brushFingerprint(preset)
            val alreadyOwned = currState.importedKritaBrushes.any {
                KritaBrushImporter.brushFingerprint(it) == fp
            }
            val dismissKey = "$senderId|$fp"
            if (!alreadyOwned && dismissKey !in dismissedFriendBrushKeys) {
                _uiState.update {
                    it.copy(
                        pendingFriendBrushOffer = FriendBrushOffer(
                            senderId = senderId,
                            senderName = senderName,
                            brushPreset = preset
                        )
                    )
                }
            }
        }
    }

    fun acceptFriendBrushOffer(context: Context) {
        val offer = _uiState.value.pendingFriendBrushOffer ?: return
        val currentImported = _uiState.value.importedKritaBrushes
        val (updatedList, savedPreset) = KritaBrushImporter.saveSingleImportedBrush(context, offer.brushPreset, currentImported)
        _uiState.update { state ->
            state.copy(
                importedKritaBrushes = updatedList,
                pendingFriendBrushOffer = null,
                statusMessage = "Brush '${savedPreset.namePt}' de ${offer.senderName} baixado para seus pincéis!"
            )
        }
    }

    fun declineFriendBrushOffer() {
        val offer = _uiState.value.pendingFriendBrushOffer ?: return
        val fp = KritaBrushImporter.brushFingerprint(offer.brushPreset)
        dismissedFriendBrushKeys.add("${offer.senderId}|$fp")
        _uiState.update { it.copy(pendingFriendBrushOffer = null) }
    }

    fun setBezierNodes(nodes: List<BezierNode>) {
        _uiState.update { it.copy(bezierNodes = nodes) }
    }

    fun toggleBezierClosed() {
        _uiState.update { it.copy(isBezierClosed = !it.isBezierClosed) }
    }

    fun undoLastBezierNode() {
        _uiState.update { state ->
            if (state.bezierNodes.isEmpty()) state
            else state.copy(bezierNodes = state.bezierNodes.dropLast(1))
        }
    }

    fun clearBezierNodes() {
        _uiState.update { it.copy(bezierNodes = emptyList(), isBezierClosed = false) }
    }

    fun confirmBezierCurve() {
        val state = _uiState.value
        if (state.bezierNodes.isEmpty()) return
        val sampledPts = sampleCubicBezierNodesToStrokePoints(
            nodes = state.bezierNodes,
            closed = state.isBezierClosed,
            samplesPerSegment = 24
        )
        if (sampledPts.isNotEmpty()) {
            addStrokeToCurrentLayer(
                DrawingStroke(
                    tool = ToolType.BEZIER_PEN,
                    color = state.currentColor,
                    brushSize = state.brushSize,
                    opacity = state.brushOpacity,
                    points = sampledPts,
                    brushPresetId = state.activeBrushPreset.id,
                    customBrushParams = state.activeBrushPreset.dynamicParams.toCompactJson()
                )
            )
        }
        _uiState.update { it.copy(bezierNodes = emptyList(), isBezierClosed = false) }
    }

    fun setCursorColor(color: Long) {
        updatePreferences { it.copy(tabletSettings = it.tabletSettings.copy(cursorColor = color)) }
    }

    fun setChunkCullingEnabled(enabled: Boolean) {
        updatePreferences { it.copy(enableChunkCulling = enabled) }
    }

    fun setGpuAccelerationEnabled(enabled: Boolean) {
        updatePreferences { it.copy(enableGpuAcceleration = enabled) }
    }

    fun setLowPowerMode(enabled: Boolean) {
        updatePreferences { it.copy(lowPowerMode = enabled) }
    }

    fun setAutoCompressOnClose(enabled: Boolean) {
        updatePreferences { it.copy(autoCompressOnClose = enabled) }
    }

    fun setCustomProjectsDirectory(path: String) {
        updatePreferences { it.copy(customProjectsDirectory = path) }
    }

    fun compactAllProjectsNow(context: Context): Int {
        val customPath = _uiState.value.preferences.customProjectsDirectory
        val count = ProjectCompressionManager.compactAllWorkingProjects(context, customPath)
        refreshSavedProjects(context)
        return count
    }

    fun selectBrushPreset(preset: BrushPreset) {
        val state = _uiState.value
        val resolved = state.nativeBrushPresets.firstOrNull { it.id == preset.id }
            ?: state.importedKritaBrushes.firstOrNull { it.id == preset.id }
            ?: preset
        _uiState.update {
            it.copy(
                activeBrushPreset = resolved,
                selectedTool = resolved.toolType,
                brushSize = resolved.defaultSize,
                brushOpacity = resolved.defaultOpacity
            )
        }
        shareImportedBrushIfNeeded(resolved)
    }

    private fun shareImportedBrushIfNeeded(preset: BrushPreset) {
        val state = _uiState.value
        if (!preset.isImportedKrita) return
        if (state.multiplayerRole == com.example.model.MultiplayerRole.NONE) return
        val fp = KritaBrushImporter.brushFingerprint(preset)
        if (sentBrushFingerprints.add(fp)) {
            multiplayerEngine.sendBrushDefinition(
                senderId = state.userAccount.id,
                senderName = state.userAccount.name,
                preset = preset
            )
        }
    }

    fun openBrushStudio() {
        _uiState.update { it.copy(selectedTool = ToolType.BRUSH, isBrushStudioOpen = true) }
    }

    fun closeBrushStudio() {
        _uiState.update { it.copy(isBrushStudioOpen = false) }
    }

    fun toggleBrushStudio() {
        _uiState.update {
            val opening = !it.isBrushStudioOpen
            it.copy(
                selectedTool = if (opening) ToolType.BRUSH else it.selectedTool,
                isBrushStudioOpen = opening
            )
        }
    }

    fun saveBrushCustomization(
        context: Context,
        preset: BrushPreset,
        newSize: Float,
        newOpacity: Float,
        newParams: com.example.model.BrushDynamicParams
    ) {
        val clampedSize = newSize.coerceIn(1f, 100f)
        val clampedOpacity = newOpacity.coerceIn(0.05f, 1.0f)
        val updatedPreset = preset.copy(
            defaultSize = clampedSize,
            defaultOpacity = clampedOpacity,
            engineKind = newParams.engineKind,
            dynamicParams = newParams
        )
        if (preset.isImportedKrita) {
            val updatedList = com.example.engine.KritaBrushImporter.updateImportedBrush(context, updatedPreset)
            _uiState.update { state ->
                val isCurrent = state.activeBrushPreset.id == updatedPreset.id
                state.copy(
                    importedKritaBrushes = updatedList,
                    activeBrushPreset = if (isCurrent) updatedPreset else state.activeBrushPreset,
                    brushSize = if (isCurrent) clampedSize else state.brushSize,
                    brushOpacity = if (isCurrent) clampedOpacity else state.brushOpacity,
                    statusMessage = "Brush '${updatedPreset.namePt}' salvo!"
                )
            }
        } else {
            val override = com.example.model.BrushOverride(
                brushId = preset.id,
                defaultSize = clampedSize,
                defaultOpacity = clampedOpacity,
                dynamicParams = newParams
            )
            val overrides = PreferencesStorageManager.saveBrushOverride(context, override)
            val refreshedNatives = BrushPreset.ALL_12_PRESETS.map { BrushPreset.applyOverride(it, overrides[it.id]) }
            val finalPreset = refreshedNatives.firstOrNull { it.id == preset.id } ?: updatedPreset
            _uiState.update { state ->
                val isCurrent = state.activeBrushPreset.id == preset.id
                state.copy(
                    nativeBrushPresets = refreshedNatives,
                    activeBrushPreset = if (isCurrent) finalPreset else state.activeBrushPreset,
                    brushSize = if (isCurrent) finalPreset.defaultSize else state.brushSize,
                    brushOpacity = if (isCurrent) finalPreset.defaultOpacity else state.brushOpacity,
                    statusMessage = "Configurações de '${finalPreset.namePt}' salvas!"
                )
            }
        }
    }

    fun resetBrushCustomization(context: Context, preset: BrushPreset): BrushPreset {
        if (preset.isImportedKrita) {
            val (updatedList, restored) = com.example.engine.KritaBrushImporter.resetImportedBrushToFactory(context, preset.id)
            val finalPreset = restored ?: preset
            _uiState.update { state ->
                val isCurrent = state.activeBrushPreset.id == preset.id
                state.copy(
                    importedKritaBrushes = updatedList,
                    activeBrushPreset = if (isCurrent) finalPreset else state.activeBrushPreset,
                    brushSize = if (isCurrent) finalPreset.defaultSize else state.brushSize,
                    brushOpacity = if (isCurrent) finalPreset.defaultOpacity else state.brushOpacity,
                    statusMessage = "Brush '${finalPreset.namePt}' restaurado ao padrão."
                )
            }
            return finalPreset
        } else {
            val overrides = PreferencesStorageManager.resetBrushOverride(context, preset.id)
            val refreshedNatives = BrushPreset.ALL_12_PRESETS.map { BrushPreset.applyOverride(it, overrides[it.id]) }
            val factoryPreset = BrushPreset.getFactoryNativePresetById(preset.id) ?: preset
            _uiState.update { state ->
                val isCurrent = state.activeBrushPreset.id == preset.id
                state.copy(
                    nativeBrushPresets = refreshedNatives,
                    activeBrushPreset = if (isCurrent) factoryPreset else state.activeBrushPreset,
                    brushSize = if (isCurrent) factoryPreset.defaultSize else state.brushSize,
                    brushOpacity = if (isCurrent) factoryPreset.defaultOpacity else state.brushOpacity,
                    statusMessage = "'${factoryPreset.namePt}' restaurado ao padrão de fábrica."
                )
            }
            return factoryPreset
        }
    }

    fun importKritaBrushesFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                com.example.engine.KritaBrushImporter.importFromUri(
                    context,
                    uri,
                    _uiState.value.importedKritaBrushes
                )
            }
            val updatedList = withContext(Dispatchers.IO) {
                com.example.engine.KritaBrushImporter.loadImportedBrushes(context)
            }
            if (imported.isNotEmpty()) {
                val first = imported.first()
                _uiState.update {
                    it.copy(
                        importedKritaBrushes = updatedList,
                        activeBrushPreset = first,
                        selectedTool = first.toolType,
                        brushSize = first.defaultSize,
                        brushOpacity = first.defaultOpacity,
                        statusMessage = "${imported.size} brush(es) do Krita importado(s): ${first.namePt}!"
                    )
                }
                shareImportedBrushIfNeeded(first)
            } else {
                _uiState.update {
                    it.copy(
                        importedKritaBrushes = updatedList,
                        statusMessage = "Nenhum brush .kpp, .gbr ou .bundle válido encontrado neste arquivo."
                    )
                }
            }
        }
    }

    fun deleteImportedKritaBrush(context: Context, brushId: Int) {
        val remaining = com.example.engine.KritaBrushImporter.deleteImportedBrush(context, brushId)
        _uiState.update { state ->
            val fallbackPreset = if (state.activeBrushPreset.id == brushId) BrushPreset.ALL_12_PRESETS.first() else state.activeBrushPreset
            state.copy(
                importedKritaBrushes = remaining,
                activeBrushPreset = fallbackPreset,
                statusMessage = "Brush removido dos importados."
            )
        }
    }

    fun setShowCanvasCollaboratorsHud(enabled: Boolean) {
        updatePreferences { it.copy(showCanvasCollaboratorsHud = enabled) }
    }

    fun toggleMirrorCanvasHorizontal() {
        _uiState.update { it.copy(mirrorCanvasHorizontal = !it.mirrorCanvasHorizontal) }
    }

    fun dismissHostShutdownAlert() {
        _uiState.update { it.copy(hostShutdownAlertMessage = null) }
    }

    private fun ensureUniqueProjectName(desiredName: String, excludeProjectId: String? = null): String {
        val base = desiredName.trim().ifEmpty { "Sem Título" }
        val existingNames = LinkedHashSet<String>()
        _uiState.value.savedProjects.forEach { p ->
            if (p.id != excludeProjectId) existingNames.add(p.name.trim().lowercase())
        }
        _uiState.value.savedProjectEntries.forEach { entry ->
            if (entry.project.id != excludeProjectId) existingNames.add(entry.project.name.trim().lowercase())
        }
        if (!existingNames.contains(base.lowercase())) return base
        var idx = 2
        while (idx < 1000) {
            val candidate = "$base ($idx)"
            if (!existingNames.contains(candidate.lowercase())) return candidate
            idx++
        }
        return "$base (${System.currentTimeMillis() % 10000})"
    }

    fun loadNewEmptyProject(name: String, resolution: ProjectResolution, fps: Int) {
        pausePlayback()
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val uniqueName = ensureUniqueProjectName(name)
        val newProj = SampleProjects.createEmptyProject(uniqueName, resolution, fps)
        _uiState.update {
            it.copy(
                project = newProj,
                currentFrameIndex = 0,
                activeLayerIndex = 0,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                isFrameLayersPanelOpen = false,
                activeDialog = EditorDialog.NONE,
                bezierNodes = emptyList(),
                isBezierClosed = false,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        updateUndoRedoAvailability()
    }

    fun openProject(project: AnimationProject) {
        pausePlayback()
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val initialFrame = project.lastActiveFrameIndex.coerceIn(0, (project.frames.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                project = project,
                currentFrameIndex = initialFrame,
                activeLayerIndex = 0,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                isFrameLayersPanelOpen = false,
                currentScreen = AppScreen.EDITOR,
                activeDialog = EditorDialog.NONE,
                bezierNodes = emptyList(),
                isBezierClosed = false,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        updateUndoRedoAvailability()
    }

    fun openProjectEntry(context: Context, entry: SavedProjectEntry) {
        pausePlayback()
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val loadedProject = if (entry.isCompressed) {
            try {
                ProjectCompressionManager.decompressProject(context, entry.fileOnDisk)
            } catch (e: Exception) {
                entry.project
            }
        } else {
            entry.project
        }
        val initialFrame = loadedProject.lastActiveFrameIndex.coerceIn(0, (loadedProject.frames.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                project = loadedProject,
                currentFrameIndex = initialFrame,
                activeLayerIndex = 0,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                isFrameLayersPanelOpen = false,
                currentScreen = AppScreen.EDITOR,
                activeDialog = EditorDialog.NONE,
                bezierNodes = emptyList(),
                isBezierClosed = false,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        updateUndoRedoAvailability()
    }

    fun importKoda2dFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val customPath = _uiState.value.preferences.customProjectsDirectory
                val kodaFile = ProjectCompressionManager.importExternalKoda2d(context, uri, customPath)
                val loadedProject = ProjectCompressionManager.decompressProject(context, kodaFile)
                val uniqueName = ensureUniqueProjectName(loadedProject.name, excludeProjectId = loadedProject.id)
                val finalProj = if (uniqueName != loadedProject.name) {
                    loadedProject.copy(name = uniqueName).also {
                        ProjectCompressionManager.compressAndCloseProject(context, it, customPath)
                    }
                } else loadedProject
                openProject(finalProj)
                refreshSavedProjects(context)
                _uiState.update { it.copy(statusMessage = "Projeto .koda2d importado com sucesso!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Erro ao importar: ${e.localizedMessage}") }
            }
        }
    }

    fun createAndOpenNewProject(context: Context, name: String, resolution: ProjectResolution, fps: Int) {
        pausePlayback()
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val uniqueName = ensureUniqueProjectName(name)
        val newProj = SampleProjects.createEmptyProject(uniqueName, resolution, fps)
        ProjectCompressionManager.saveWorkingProject(context, newProj)
        ProjectStorageManager.saveProject(context, newProj)
        refreshSavedProjects(context)
        _uiState.update {
            it.copy(
                project = newProj,
                currentFrameIndex = 0,
                activeLayerIndex = 0,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                isFrameLayersPanelOpen = false,
                currentScreen = AppScreen.EDITOR,
                activeDialog = EditorDialog.NONE,
                bezierNodes = emptyList(),
                isBezierClosed = false,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        updateUndoRedoAvailability()
    }

    /**
     * Saves active project uncompressed to working directory (used during active editing / Ctrl+S / Save button).
     * Strictly skipped for Multiplayer CLIENT visitors: only the Host owns and saves the shared project.
     */
    fun saveCurrentProject(context: Context) {
        if (_uiState.value.multiplayerRole == com.example.model.MultiplayerRole.CLIENT) {
            _uiState.update {
                it.copy(statusMessage = "Modo Visitante: apenas o dono do servidor salva este projeto.")
            }
            return
        }
        val current = _uiState.value.project.copy(
            lastActiveFrameIndex = _uiState.value.currentFrameIndex,
            updatedAt = System.currentTimeMillis()
        )
        ProjectCompressionManager.saveWorkingProject(context, current)
        ProjectStorageManager.saveProject(context, current)
        refreshSavedProjects(context)
        _uiState.update {
            it.copy(
                project = current,
                statusMessage = "Projeto salvo com sucesso na pasta de trabalho!"
            )
        }
    }

    /**
     * Closes current project: if autoCompressOnClose is enabled, compacts into .koda2d in the
     * chosen projects folder and deletes the uncompressed working directory to keep storage lightweight.
     * Strictly no-op when running as a remote LAN visitor (CLIENT), and discards untouched default blank shells.
     */
    fun closeAndSaveCurrentProject(context: Context) {
        if (_uiState.value.multiplayerRole == com.example.model.MultiplayerRole.CLIENT) {
            return
        }
        val current = _uiState.value.project.copy(
            lastActiveFrameIndex = _uiState.value.currentFrameIndex,
            updatedAt = System.currentTimeMillis()
        )
        val customPath = _uiState.value.preferences.customProjectsDirectory
        val alreadySaved = _uiState.value.savedProjects.any { it.id == current.id } ||
            _uiState.value.savedProjectEntries.any { it.project.id == current.id }
        val isUntouchedBlankShell = !alreadySaved &&
            current.name == "Novo Projeto" &&
            current.frames.size <= 1 &&
            current.frames.all { frame -> frame.layers.all { layer -> layer.strokes.isEmpty() && layer.imageAttachment == null } && frame.localLayers.isEmpty() } &&
            current.mediaAssets.isEmpty()
        if (isUntouchedBlankShell) {
            ProjectCompressionManager.deleteProjectEntirely(context, current.id, customPath)
            ProjectStorageManager.deleteProject(context, current.id)
            refreshSavedProjects(context)
            return
        }

        if (_uiState.value.preferences.autoCompressOnClose) {
            try {
                ProjectCompressionManager.compressAndCloseProject(context, current, customPath)
            } catch (_: Exception) {
                ProjectCompressionManager.saveWorkingProject(context, current)
            }
        } else {
            ProjectCompressionManager.saveWorkingProject(context, current)
        }
        ProjectStorageManager.saveProject(context, current)
        refreshSavedProjects(context)
    }

    /**
     * Visitor clean exit: leaves the collaborative canvas ONLY for this visitor without writing
     * any host project files to this device.
     */
    fun leaveRemoteSessionAsVisitor(
        context: Context? = appContext,
        targetScreen: AppScreen = AppScreen.HOME
    ) {
        pausePlayback()
        val myId = _uiState.value.userAccount.id
        viewModelScope.launch(KodanimaDispatchers.networkIo) {
            multiplayerEngine.sendClientLeave(myId)
            withContext(Dispatchers.Main) {
                multiplayerEngine.stopSessionOnly()
                bluetoothCoopManager?.stopServerOnly()
            }
        }
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val blankFallback = SampleProjects.createEmptyProject("Novo Projeto", ProjectResolution.PRESETS[0], 12)
        val entries = context?.let {
            ProjectCompressionManager.listAllSavedProjects(it, _uiState.value.preferences.customProjectsDirectory)
        }
        _uiState.update { state ->
            state.copy(
                project = blankFallback,
                savedProjects = entries?.map { it.project } ?: state.savedProjects,
                savedProjectEntries = entries ?: state.savedProjectEntries,
                currentFrameIndex = 0,
                activeLayerIndex = 0,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                isFrameLayersPanelOpen = false,
                multiplayerRole = com.example.model.MultiplayerRole.NONE,
                connectionStatus = com.example.model.ConnectionStatus.DISCONNECTED,
                activeRoomInfo = null,
                isLocalServerActive = false,
                connectedFriend = null,
                connectedFriends = emptyList(),
                remoteCursors = emptyMap(),
                remoteLiveStrokes = emptyMap(),
                currentPingMs = 0,
                currentScreen = targetScreen,
                statusMessage = "Você saiu da sala colaborativa."
            )
        }
        updateUndoRedoAvailability()
    }

    private fun handleHostShutdownOnVisitor(message: String) {
        pausePlayback()
        multiplayerEngine.stopSessionOnly()
        bluetoothCoopManager?.stopServerOnly()
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val ctx = appContext
        val entries = ctx?.let {
            ProjectCompressionManager.listAllSavedProjects(it, _uiState.value.preferences.customProjectsDirectory)
        }
        val blankFallback = SampleProjects.createEmptyProject("Novo Projeto", ProjectResolution.PRESETS[0], 12)
        _uiState.update { state ->
            state.copy(
                project = blankFallback,
                savedProjects = entries?.map { it.project } ?: state.savedProjects,
                savedProjectEntries = entries ?: state.savedProjectEntries,
                currentFrameIndex = 0,
                activeLayerIndex = 0,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                isFrameLayersPanelOpen = false,
                multiplayerRole = com.example.model.MultiplayerRole.NONE,
                connectionStatus = com.example.model.ConnectionStatus.DISCONNECTED,
                activeRoomInfo = null,
                isLocalServerActive = false,
                connectedFriend = null,
                connectedFriends = emptyList(),
                remoteCursors = emptyMap(),
                remoteLiveStrokes = emptyMap(),
                currentPingMs = 0,
                currentScreen = AppScreen.HOME,
                hostShutdownAlertMessage = message.ifBlank { "o dono da casa saiu :(" }
            )
        }
        updateUndoRedoAvailability()
    }

    fun deleteProject(context: Context, projectId: String) {
        val targetProj = _uiState.value.savedProjects.firstOrNull { it.id == projectId }
            ?: if (_uiState.value.project.id == projectId) _uiState.value.project else null
        targetProj?.mediaAssets?.forEach { asset ->
            MediaAssetManager.deleteMediaFile(asset.uriString)
        }
        val customPath = _uiState.value.preferences.customProjectsDirectory
        ProjectCompressionManager.deleteProjectEntirely(context, projectId, customPath)
        ProjectStorageManager.deleteProject(context, projectId)
        refreshSavedProjects(context)
    }

    fun duplicateProject(context: Context, projectId: String) {
        val original = _uiState.value.savedProjects.firstOrNull { it.id == projectId }
            ?: _uiState.value.savedProjectEntries.firstOrNull { it.project.id == projectId }?.project
        val baseCopyName = ensureUniqueProjectName("${original?.name ?: "Projeto"} (Cópia)")
        val prefs = _uiState.value.preferences
        val duplicated = ProjectCompressionManager.duplicateSavedProject(
            context = context,
            sourceProjectId = projectId,
            newName = baseCopyName,
            customPath = prefs.customProjectsDirectory,
            compressCopy = prefs.autoCompressOnClose
        )
        if (duplicated == null) {
            ProjectStorageManager.duplicateProject(context, projectId, baseCopyName)
        }
        refreshSavedProjects(context)
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- Tablet / Stylus Pressure Curve Mapping ---

    fun computeEffectivePressure(rawPressure: Float): Float {
        val curve = _uiState.value.preferences.tabletSettings.pressureCurve
        val clamped = rawPressure.coerceIn(0.01f, 1.0f)
        return clamped.pow(curve.exponent).coerceIn(0.15f, 2.0f)
    }

    // --- Drawing & Tool Selection ---

    fun selectTool(tool: ToolType) {
        _uiState.update { it.copy(selectedTool = tool) }
    }

    fun setColor(color: Long) {
        _uiState.update { state ->
            val updatedRecents = if (!state.recentColors.contains(color)) {
                listOf(color) + state.recentColors.take(7)
            } else {
                state.recentColors
            }
            state.copy(currentColor = color, recentColors = updatedRecents)
        }
    }

    fun setBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size.coerceIn(1f, 100f)) }
    }

    fun setBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity.coerceIn(0.05f, 1.0f)) }
    }

    fun setStylusOnlyMode(enabled: Boolean) {
        _uiState.update { it.copy(isStylusOnlyMode = enabled) }
    }

    fun toggleTimelineSnapAssistant() {
        _uiState.update { state ->
            val next = !state.preferences.timelineSnapAssistant
            val updatedPrefs = state.preferences.copy(timelineSnapAssistant = next)
            appContext?.let { ctx -> PreferencesStorageManager.savePreferences(ctx, updatedPrefs) }
            state.copy(preferences = updatedPrefs)
        }
    }

    fun setEraserUsesPressure(enabled: Boolean) {
        _uiState.update { state ->
            val updatedPrefs = state.preferences.copy(eraserUsesPressure = enabled)
            appContext?.let { ctx -> PreferencesStorageManager.savePreferences(ctx, updatedPrefs) }
            state.copy(preferences = updatedPrefs)
        }
    }

    fun setEraserBrushPresetId(presetId: Int) {
        _uiState.update { state ->
            val updatedPrefs = state.preferences.copy(eraserBrushPresetId = presetId)
            appContext?.let { ctx -> PreferencesStorageManager.savePreferences(ctx, updatedPrefs) }
            state.copy(preferences = updatedPrefs)
        }
    }

    fun toggleOnionSkin() {
        _uiState.update { state ->
            val nextState = !state.onionSkinEnabled
            val updatedProject = state.project.copy(
                onionSkin = state.project.onionSkin.copy(isEnabled = nextState)
            )
            state.copy(
                onionSkinEnabled = nextState,
                project = updatedProject
            )
        }
    }

    fun updateOnionSkinSettings(settings: OnionSkinSettings) {
        _uiState.update {
            it.copy(
                onionSkinEnabled = settings.isEnabled,
                project = it.project.copy(onionSkin = settings)
            )
        }
    }

    fun openDialog(dialog: EditorDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(activeDialog = EditorDialog.NONE) }
    }

    // --- Keyboard Shortcut Handler (PC Mode & Physical Keyboard) ---

    private fun normalizeShortcutKeyCode(code: Int): Int {
        return when (code) {
            KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyEvent.KEYCODE_ENTER
            KeyEvent.KEYCODE_NUMPAD_ADD -> KeyEvent.KEYCODE_EQUALS
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> KeyEvent.KEYCODE_MINUS
            else -> code
        }
    }

    private fun executeShortcutAction(action: ShortcutAction, context: Context): Boolean {
        when (action) {
            ShortcutAction.TOOL_PEN -> selectTool(ToolType.PEN)
            ShortcutAction.TOOL_PENCIL -> selectTool(ToolType.PENCIL)
            ShortcutAction.TOOL_BEZIER_PEN -> selectTool(ToolType.BEZIER_PEN)
            ShortcutAction.TOOL_ERASER -> selectTool(ToolType.ERASER)
            ShortcutAction.TOOL_BUCKET -> selectTool(ToolType.BUCKET)
            ShortcutAction.TOOL_MAGIC_WAND -> selectTool(ToolType.MAGIC_WAND)
            ShortcutAction.TOOL_LASSO -> selectTool(ToolType.LASSO)
            ShortcutAction.TOOL_EYEDROPPER -> selectTool(ToolType.EYEDROPPER)
            ShortcutAction.TOOL_LINE -> selectTool(ToolType.LINE)
            ShortcutAction.TOOL_RECTANGLE -> selectTool(ToolType.RECTANGLE)
            ShortcutAction.TOOL_CIRCLE -> selectTool(ToolType.CIRCLE)
            ShortcutAction.CONFIRM_BEZIER -> {
                if (_uiState.value.selectedTool == ToolType.BEZIER_PEN && _uiState.value.bezierNodes.isNotEmpty()) {
                    confirmBezierCurve()
                } else {
                    return false
                }
            }
            ShortcutAction.BRUSH_SIZE_DOWN -> setBrushSize((_uiState.value.brushSize - 2f).coerceAtLeast(1f))
            ShortcutAction.BRUSH_SIZE_UP -> setBrushSize((_uiState.value.brushSize + 2f).coerceAtMost(100f))
            ShortcutAction.TOGGLE_MIRROR -> toggleMirrorCanvasHorizontal()
            ShortcutAction.PLAY_PAUSE -> togglePlayback()
            ShortcutAction.PREV_FRAME -> prevFrame()
            ShortcutAction.NEXT_FRAME -> nextFrame()
            ShortcutAction.TOGGLE_ONION_SKIN -> toggleOnionSkin()
            ShortcutAction.ADD_FRAME -> addFrame()
            ShortcutAction.CLEAR_FRAME -> clearCurrentFrame()
            ShortcutAction.UNDO -> undo()
            ShortcutAction.REDO -> redo()
            ShortcutAction.SAVE_PROJECT -> saveCurrentProject(context)
            ShortcutAction.TOGGLE_ZEN -> toggleZenMode()
            ShortcutAction.OPEN_EXPORT -> openDialog(EditorDialog.EXPORT)
            ShortcutAction.TOGGLE_FRAME_LAYERS -> toggleFrameLayersPanel()
        }
        return true
    }

    fun handlePhysicalKeyEvent(event: KeyEvent, context: Context): Boolean {
        if (!_uiState.value.preferences.tabletSettings.keyboardShortcutsEnabled) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val state = _uiState.value
        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed
        val normKey = normalizeShortcutKeyCode(event.keyCode)

        // Video Transform Mode physical keyboard shortcuts (Arrows move, +/- scale, [/] rotate, R reset, Enter/Esc exit)
        if (state.isVideoTransformMode) {
            val videoAsset = state.project.mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }
            if (videoAsset != null) {
                when (normKey) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { moveVideoAsset(videoAsset.id, -14.0, 0.0); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { moveVideoAsset(videoAsset.id, 14.0, 0.0); return true }
                    KeyEvent.KEYCODE_DPAD_UP -> { moveVideoAsset(videoAsset.id, 0.0, -14.0); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { moveVideoAsset(videoAsset.id, 0.0, 14.0); return true }
                    KeyEvent.KEYCODE_EQUALS -> { scaleVideoAsset(videoAsset.id, 1.08); return true }
                    KeyEvent.KEYCODE_MINUS -> { scaleVideoAsset(videoAsset.id, 0.92); return true }
                    KeyEvent.KEYCODE_LEFT_BRACKET -> { rotateVideoAsset(videoAsset.id, -5.0); return true }
                    KeyEvent.KEYCODE_RIGHT_BRACKET -> { rotateVideoAsset(videoAsset.id, 5.0); return true }
                    KeyEvent.KEYCODE_R -> { resetVideoTransform(videoAsset.id); return true }
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE -> { setVideoTransformMode(false); return true }
                }
            }
        }

        val bindings = state.preferences.keyboardShortcuts.ifEmpty { ShortcutRegistry.defaultMap() }

        // Always allow Enter to confirm an active Caneta Vetorial (Bézier) curve first
        if (state.selectedTool == ToolType.BEZIER_PEN && state.bezierNodes.isNotEmpty()) {
            val confirmBinding = bindings[ShortcutAction.CONFIRM_BEZIER]
            val matchesConfigured = confirmBinding != null &&
                normalizeShortcutKeyCode(confirmBinding.keyCode) == normKey &&
                confirmBinding.ctrl == isCtrl &&
                confirmBinding.shift == isShift
            if (matchesConfigured || (!isCtrl && !isShift && normKey == KeyEvent.KEYCODE_ENTER)) {
                confirmBezierCurve()
                return true
            }
        }

        // Dispatch user-configurable shortcut table (exact Ctrl + Shift + Key match first)
        for (action in ShortcutAction.entries) {
            val binding = bindings[action] ?: continue
            if (normalizeShortcutKeyCode(binding.keyCode) == normKey &&
                binding.ctrl == isCtrl &&
                binding.shift == isShift
            ) {
                if (executeShortcutAction(action, context)) {
                    return true
                }
            }
        }

        // Handle DELETE alias for FORWARD_DEL when user didn't remap CLEAR_FRAME away from Delete
        if (event.keyCode == KeyEvent.KEYCODE_DEL) {
            val clearBinding = bindings[ShortcutAction.CLEAR_FRAME]
            if (clearBinding != null &&
                clearBinding.keyCode == KeyEvent.KEYCODE_FORWARD_DEL &&
                clearBinding.ctrl == isCtrl &&
                clearBinding.shift == isShift
            ) {
                clearCurrentFrame()
                return true
            }
        }
        return false
    }

    // --- Zen / Focus Mode (Ocultação de Barras com TAB) & Timeline Zoom ---

    fun toggleZenMode() {
        _uiState.update { it.copy(isZenModeActive = !it.isZenModeActive) }
    }

    fun setZenModeActive(active: Boolean) {
        _uiState.update { it.copy(isZenModeActive = active) }
    }

    fun updateZenModeSettings(settings: com.example.model.ZenModeSettings) {
        _uiState.update {
            it.copy(preferences = it.preferences.copy(zenModeSettings = settings))
        }
        appContext?.let { ctx ->
            PreferencesStorageManager.savePreferences(ctx, _uiState.value.preferences)
        }
    }

    fun setTimelineZoom(zoom: Float) {
        _uiState.update { it.copy(timelineZoom = zoom.coerceIn(0.5f, 3.5f)) }
    }

    fun zoomTimeline(factor: Float) {
        _uiState.update {
            val newZ = (it.timelineZoom * factor).coerceIn(0.5f, 3.5f)
            it.copy(timelineZoom = newZ)
        }
    }

    // --- Frame & Layer Manipulation ---

    fun goToFrame(index: Int) {
        val state = _uiState.value
        val total = state.project.frames.size
        if (total == 0) return
        val clamped = index.coerceIn(0, total - 1)
        if (clamped != state.currentFrameIndex || state.activeScrubTimeMs != null) {
            _uiState.update { s ->
                saveCurrentFrameTargetPreference(s)
                val targetFrame = s.project.frames[clamped]
                val (nextIsLocal, nextLocalIdx) = resolveTargetStateForFrame(
                    targetFrame = targetFrame,
                    fallbackActiveLocalIdx = s.activeLocalLayerIndex,
                    fallbackIsLocal = s.activeTargetIsFrameLocalLayer
                )
                s.copy(
                    currentFrameIndex = clamped,
                    activeScrubTimeMs = null,
                    bezierNodes = emptyList(),
                    isBezierClosed = false,
                    activeSelection = SelectionRegion(isActive = false),
                    activeLocalLayerIndex = nextLocalIdx,
                    activeTargetIsFrameLocalLayer = nextIsLocal,
                    project = s.project.copy(lastActiveFrameIndex = clamped, updatedAt = System.currentTimeMillis())
                )
            }
            if (state.isPlaying) {
                val timeMs = MediaAssetManager.computeTimeMsForFrame(state.project.frames, clamped, state.project.fps)
                MediaAssetManager.seekAudio(timeMs.toInt())
            }
            updateUndoRedoAvailability()
        }
    }

    /**
     * Interactive timeline Pinça (Playhead) scrubbing: seeks frames & triggers real-time audio scrubbing for lip-sync.
     */
    fun scrubToTimelineMs(timeMs: Long) {
        val state = _uiState.value
        if (state.isPlaying) {
            pausePlayback()
        }
        val proj = state.project
        val totalDurationMs = MediaAssetManager.computeTotalDurationMs(proj.frames, proj.fps)
        val audioAsset = proj.mediaAssets.firstOrNull { it.type == "audio" }
        val maxMs = max(totalDurationMs, (audioAsset?.durationSeconds?.times(1000.0)?.toLong() ?: 0L)).coerceAtLeast(100L)
        val clampedMs = timeMs.coerceIn(0L, maxMs)
        val targetFrameIdx = MediaAssetManager.findFrameIndexAtTimeMs(proj.frames, clampedMs, proj.fps)

        val frameChanged = targetFrameIdx != state.currentFrameIndex
        _uiState.update { s ->
            if (frameChanged) {
                saveCurrentFrameTargetPreference(s)
            }
            val targetFrame = s.project.frames[targetFrameIdx]
            val (nextIsLocal, nextLocalIdx) = resolveTargetStateForFrame(
                targetFrame = targetFrame,
                fallbackActiveLocalIdx = s.activeLocalLayerIndex,
                fallbackIsLocal = s.activeTargetIsFrameLocalLayer
            )
            s.copy(
                currentFrameIndex = targetFrameIdx,
                activeScrubTimeMs = clampedMs,
                activeLocalLayerIndex = nextLocalIdx,
                activeTargetIsFrameLocalLayer = nextIsLocal,
                project = if (frameChanged) s.project.copy(lastActiveFrameIndex = targetFrameIdx) else s.project
            )
        }
        if (frameChanged) {
            updateUndoRedoAvailability()
        }
        if (audioAsset != null && audioAsset.uriString.isNotBlank()) {
            MediaAssetManager.scrubAudioAtMs(
                filePath = audioAsset.uriString,
                targetMs = clampedMs,
                volumePercent = audioAsset.audioVolumePercent,
                muted = audioAsset.audioMuted
            )
        }
    }

    fun endAudioScrub() {
        MediaAssetManager.endAudioScrub()
        _uiState.update { it.copy(activeScrubTimeMs = null) }
    }

    fun setFrameHoldDuration(frameIndex: Int, holdCount: Int) {
        val clampedHold = holdCount.coerceIn(1, 120)
        _uiState.update { state ->
            val updated = state.project.frames.toMutableList()
            if (frameIndex in updated.indices) {
                updated[frameIndex] = updated[frameIndex].copy(holdFrames = clampedHold)
                state.copy(
                    project = state.project.copy(frames = updated, updatedAt = System.currentTimeMillis())
                )
            } else state
        }
    }

    fun performBucketFill(x: Float, y: Float) {
        val state = _uiState.value
        val proj = state.project
        val currFrame = proj.frames.getOrNull(state.currentFrameIndex) ?: return
        val fillColor = state.currentColor
        val fillOpacity = state.brushOpacity
        val activeSel = state.activeSelection
        val selectionClipPoly = if (activeSel.isActive && activeSel.points.size >= 3) {
            SelectionGeometryHelper.transformSelectionPolygon(activeSel)
        } else null

        viewModelScope.launch {
            val spans = withContext(KodanimaDispatchers.backgroundRender) {
                var bmp: Bitmap? = null
                try {
                    bmp = KodanimaGraphicsEngine.renderFrameToBitmap(
                        currFrame,
                        proj.resolution.width,
                        proj.resolution.height,
                        android.graphics.Color.WHITE
                    )
                    val px = x.toInt().coerceIn(0, bmp.width - 1)
                    val py = y.toInt().coerceIn(0, bmp.height - 1)
                    KodanimaGraphicsEngine.createFloodFillSpans(
                        bitmap = bmp,
                        startX = px,
                        startY = py,
                        tolerance = 20,
                        selectionPoly = selectionClipPoly
                    )
                } finally {
                    KodanimaBitmapPool.release(bmp)
                }
            }
            if (spans.isNotEmpty()) {
                val stroke = DrawingStroke(
                    tool = ToolType.BUCKET,
                    color = fillColor,
                    brushSize = 2.5f,
                    opacity = fillOpacity,
                    points = spans,
                    isFilled = true,
                    clipPolygon = selectionClipPoly
                )
                addStrokeToCurrentLayer(stroke)
            }
        }
    }

    fun performMagicWand(x: Float, y: Float) {
        val state = _uiState.value
        val proj = state.project
        val currFrame = proj.frames.getOrNull(state.currentFrameIndex) ?: return
        val useLocal = state.activeTargetIsFrameLocalLayer && currFrame.localLayers.isNotEmpty()
        val targetList = if (useLocal) currFrame.localLayers else currFrame.layers
        val validLayerIdx = state.let {
            val raw = if (useLocal) it.activeLocalLayerIndex else it.activeLayerIndex
            raw.coerceIn(0, targetList.lastIndex)
        }
        val layerStrokes = targetList.getOrNull(validLayerIdx)?.strokes ?: emptyList()

        viewModelScope.launch {
            val wandResult = withContext(KodanimaDispatchers.backgroundRender) {
                var bmp: Bitmap? = null
                try {
                    bmp = KodanimaGraphicsEngine.renderFrameToBitmap(
                        currFrame,
                        proj.resolution.width,
                        proj.resolution.height,
                        android.graphics.Color.WHITE
                    )
                    val px = x.toInt().coerceIn(0, bmp.width - 1)
                    val py = y.toInt().coerceIn(0, bmp.height - 1)
                    KodanimaGraphicsEngine.createMagicWandRegion(bmp, px, py, tolerance = 24)
                } finally {
                    KodanimaBitmapPool.release(bmp)
                }
            }
            if (wandResult.boundary.size < 3) {
                clearSelection()
                return@launch
            }
            val split = SelectionGeometryHelper.splitStrokesByPolygon(layerStrokes, wandResult.boundary)
            _uiState.update {
                it.copy(
                    activeSelection = SelectionRegion(
                        isActive = true,
                        points = wandResult.boundary,
                        minX = wandResult.minX,
                        minY = wandResult.minY,
                        maxX = wandResult.maxX,
                        maxY = wandResult.maxY,
                        capturedStrokes = split.capturedInside,
                        hiddenSourceStrokeIds = split.affectedOriginalIds,
                        outsideReplacementStrokes = split.stationaryOutside
                    )
                )
            }
        }
    }

    fun setLassoSelection(points: List<StrokePoint>) {
        if (points.size < 3) {
            clearSelection()
            return
        }
        val closed = if (points.first() != points.last()) points + points.first() else points
        val minX = closed.minOf { it.x }
        val minY = closed.minOf { it.y }
        val maxX = closed.maxOf { it.x }
        val maxY = closed.maxOf { it.y }

        val state = _uiState.value
        val currFrame = state.project.frames.getOrNull(state.currentFrameIndex)
        val useLocal = currFrame != null && state.activeTargetIsFrameLocalLayer && currFrame.localLayers.isNotEmpty()
        val targetList = if (useLocal) currFrame!!.localLayers else (currFrame?.layers ?: emptyList())
        val validLayerIdx = if (targetList.isNotEmpty()) {
            val raw = if (useLocal) state.activeLocalLayerIndex else state.activeLayerIndex
            raw.coerceIn(0, targetList.lastIndex)
        } else 0
        val strokes = targetList.getOrNull(validLayerIdx)?.strokes ?: emptyList()

        // Split strokes cleanly along polygon intersections so only segments INSIDE are captured
        val split = SelectionGeometryHelper.splitStrokesByPolygon(strokes, closed)

        _uiState.update {
            it.copy(
                activeSelection = SelectionRegion(
                    isActive = true,
                    points = closed,
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    capturedStrokes = split.capturedInside,
                    hiddenSourceStrokeIds = split.affectedOriginalIds,
                    outsideReplacementStrokes = split.stationaryOutside
                )
            )
        }
    }

    fun flipSelectionH() {
        _uiState.update { state ->
            val sel = state.activeSelection
            if (!sel.isActive) return@update state
            val cx = sel.centerX + sel.offsetX
            val flippedPts = sel.points.map { it.copy(x = 2 * cx - it.x) }
            val flippedStrokes = sel.capturedStrokes.map { stroke ->
                SelectionGeometryHelper.withRecomputedBounds(
                    stroke.copy(
                        points = stroke.points.map { it.copy(x = 2 * cx - it.x) },
                        clipPolygon = stroke.clipPolygon?.map { it.copy(x = 2 * cx - it.x) }
                    )
                )
            }
            state.copy(
                activeSelection = sel.copy(
                    points = flippedPts,
                    capturedStrokes = flippedStrokes
                )
            )
        }
    }

    fun flipSelectionV() {
        _uiState.update { state ->
            val sel = state.activeSelection
            if (!sel.isActive) return@update state
            val cy = sel.centerY + sel.offsetY
            val flippedPts = sel.points.map { it.copy(y = 2 * cy - it.y) }
            val flippedStrokes = sel.capturedStrokes.map { stroke ->
                SelectionGeometryHelper.withRecomputedBounds(
                    stroke.copy(
                        points = stroke.points.map { it.copy(y = 2 * cy - it.y) },
                        clipPolygon = stroke.clipPolygon?.map { it.copy(y = 2 * cy - it.y) }
                    )
                )
            }
            state.copy(
                activeSelection = sel.copy(
                    points = flippedPts,
                    capturedStrokes = flippedStrokes
                )
            )
        }
    }

    fun copySelection() {
        val sel = _uiState.value.activeSelection
        if (sel.isActive && sel.capturedStrokes.isNotEmpty()) {
            clipboardStrokes = sel.capturedStrokes.map { it.copy(id = UUID.randomUUID().toString()) }
            _uiState.update { it.copy(statusMessage = "${clipboardStrokes.size} traços copiados!") }
        }
    }

    private fun isUsingLocalLayer(state: EditorUiState, frame: Frame): Boolean {
        return state.activeTargetIsFrameLocalLayer && frame.localLayers.isNotEmpty()
    }

    fun pasteSelection() {
        if (clipboardStrokes.isEmpty()) return
        val stateBefore = _uiState.value
        val frameIdx = stateBefore.currentFrameIndex
        val currFrameBefore = stateBefore.project.frames.getOrNull(frameIdx) ?: return
        val useLocal = isUsingLocalLayer(stateBefore, currFrameBefore)
        val myId = stateBefore.userAccount.id
        val pasted = clipboardStrokes.map { stroke ->
            SelectionGeometryHelper.withRecomputedBounds(
                stroke.copy(
                    id = UUID.randomUUID().toString(),
                    authorId = myId,
                    points = stroke.points.map { it.copy(x = it.x + 24f, y = it.y + 24f) },
                    clipPolygon = stroke.clipPolygon?.map { it.copy(x = it.x + 24f, y = it.y + 24f) }
                )
            )
        }
        var appliedLayerIdx = 0
        _uiState.update { state ->
            val currFrame = state.project.frames[state.currentFrameIndex]
            val updatedFrame = if (useLocal && currFrame.localLayers.isNotEmpty()) {
                val validIdx = state.activeLocalLayerIndex.coerceIn(0, currFrame.localLayers.lastIndex)
                appliedLayerIdx = validIdx
                val currLayer = currFrame.localLayers[validIdx]
                val updatedLayers = currFrame.localLayers.toMutableList()
                updatedLayers[validIdx] = currLayer.copy(strokes = currLayer.strokes + pasted)
                currFrame.copy(localLayers = updatedLayers)
            } else {
                val validIdx = state.activeLayerIndex.coerceIn(0, currFrame.layers.lastIndex)
                appliedLayerIdx = validIdx
                val currLayer = currFrame.layers[validIdx]
                val updatedLayers = currFrame.layers.toMutableList()
                updatedLayers[validIdx] = currLayer.copy(strokes = currLayer.strokes + pasted)
                currFrame.copy(layers = updatedLayers)
            }
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[state.currentFrameIndex] = updatedFrame

            val minX = pasted.flatMap { it.points }.minOfOrNull { it.x } ?: 0f
            val minY = pasted.flatMap { it.points }.minOfOrNull { it.y } ?: 0f
            val maxX = pasted.flatMap { it.points }.maxOfOrNull { it.x } ?: 100f
            val maxY = pasted.flatMap { it.points }.maxOfOrNull { it.y } ?: 100f

            val box = listOf(
                StrokePoint(minX, minY),
                StrokePoint(maxX, minY),
                StrokePoint(maxX, maxY),
                StrokePoint(minX, maxY),
                StrokePoint(minX, minY)
            )
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(
                project = updatedProj,
                activeSelection = SelectionRegion(
                    isActive = true,
                    points = box,
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    capturedStrokes = pasted,
                    hiddenSourceStrokeIds = emptySet(),
                    outsideReplacementStrokes = emptyList()
                )
            )
        }
        pasted.forEach { s ->
            pushUndoOp(CanvasOp.AddStroke(myId, currFrameBefore.id, appliedLayerIdx, s, isLocalLayer = useLocal))
            if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
                multiplayerEngine.sendStroke(frameIdx, appliedLayerIdx, s, isLocalLayer = useLocal)
            }
        }
        updateUndoRedoAvailability()
    }

    fun deleteSelection() {
        val stateBefore = _uiState.value
        val sel = stateBefore.activeSelection
        if (!sel.isActive) return
        val frameIdx = stateBefore.currentFrameIndex
        val currFrame = stateBefore.project.frames.getOrNull(frameIdx) ?: return
        val useLocal = isUsingLocalLayer(stateBefore, currFrame)
        val targetList = if (useLocal) currFrame.localLayers else currFrame.layers
        val validLayerIdx = (if (useLocal) stateBefore.activeLocalLayerIndex else stateBefore.activeLayerIndex)
            .coerceIn(0, targetList.lastIndex)
        val currLayer = targetList[validLayerIdx]
        val removeIds = if (sel.hiddenSourceStrokeIds.isNotEmpty()) {
            sel.hiddenSourceStrokeIds
        } else {
            sel.capturedStrokes.map { it.id }.toSet()
        }
        val removedList = currLayer.strokes.filter { it.id in removeIds }
        if (removedList.isEmpty()) {
            clearSelection()
            return
        }
        val myId = stateBefore.userAccount.id
        val outsideKeep = sel.outsideReplacementStrokes
        if (outsideKeep.isEmpty()) {
            pushUndoOp(CanvasOp.RemoveStrokes(myId, currFrame.id, validLayerIdx, removedList, isLocalLayer = useLocal))
        } else {
            pushUndoOp(CanvasOp.ReplaceStrokes(myId, currFrame.id, validLayerIdx, removedList, outsideKeep, isLocalLayer = useLocal))
        }
        _uiState.update { state ->
            val f = state.project.frames[state.currentFrameIndex]
            val updatedFrame = if (useLocal && f.localLayers.isNotEmpty()) {
                val l = f.localLayers[validLayerIdx]
                val remainingStrokes = l.strokes.filter { it.id !in removeIds } + outsideKeep
                val updatedLayers = f.localLayers.toMutableList()
                updatedLayers[validLayerIdx] = l.copy(strokes = remainingStrokes)
                f.copy(localLayers = updatedLayers)
            } else {
                val l = f.layers[validLayerIdx]
                val remainingStrokes = l.strokes.filter { it.id !in removeIds } + outsideKeep
                val updatedLayers = f.layers.toMutableList()
                updatedLayers[validLayerIdx] = l.copy(strokes = remainingStrokes)
                f.copy(layers = updatedLayers)
            }
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[state.currentFrameIndex] = updatedFrame
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(
                project = updatedProj,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendUndoRemoveStrokes(myId, frameIdx, validLayerIdx, removedList.map { it.id }, isLocalLayer = useLocal)
            if (outsideKeep.isNotEmpty()) {
                multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, validLayerIdx, outsideKeep, isLocalLayer = useLocal)
            }
        }
        updateUndoRedoAvailability()
    }

    fun fillSelectionWithCurrentColor() {
        val stateBefore = _uiState.value
        val sel = stateBefore.activeSelection
        if (!sel.isActive || sel.points.size < 3) return
        val frameIdx = stateBefore.currentFrameIndex
        val currFrame = stateBefore.project.frames.getOrNull(frameIdx) ?: return
        val useLocal = isUsingLocalLayer(stateBefore, currFrame)
        val targetList = if (useLocal) currFrame.localLayers else currFrame.layers
        val validLayerIdx = (if (useLocal) stateBefore.activeLocalLayerIndex else stateBefore.activeLayerIndex)
            .coerceIn(0, targetList.lastIndex)
        val myId = stateBefore.userAccount.id
        val transformedPoly = SelectionGeometryHelper.transformSelectionPolygon(sel)
        val fillStroke = SelectionGeometryHelper.withRecomputedBounds(
            DrawingStroke(
                authorId = myId,
                tool = ToolType.LASSO,
                color = stateBefore.currentColor,
                brushSize = 4f,
                opacity = stateBefore.brushOpacity,
                points = transformedPoly,
                isFilled = true
            )
        )
        pushUndoOp(CanvasOp.AddStroke(myId, currFrame.id, validLayerIdx, fillStroke, isLocalLayer = useLocal))
        _uiState.update { state ->
            val f = state.project.frames[state.currentFrameIndex]
            val updatedFrame = if (useLocal && f.localLayers.isNotEmpty()) {
                val l = f.localLayers[validLayerIdx]
                val updatedLayers = f.localLayers.toMutableList()
                updatedLayers[validLayerIdx] = l.copy(strokes = l.strokes + fillStroke)
                f.copy(localLayers = updatedLayers)
            } else {
                val l = f.layers[validLayerIdx]
                val updatedLayers = f.layers.toMutableList()
                updatedLayers[validLayerIdx] = l.copy(strokes = l.strokes + fillStroke)
                f.copy(layers = updatedLayers)
            }
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[state.currentFrameIndex] = updatedFrame
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(
                project = updatedProj,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendStroke(frameIdx, validLayerIdx, fillStroke, isLocalLayer = useLocal)
        }
        updateUndoRedoAvailability()
    }

    fun updateSelectionOffset(dx: Float, dy: Float) {
        _uiState.update { state ->
            val sel = state.activeSelection
            if (!sel.isActive) return@update state
            state.copy(
                activeSelection = sel.copy(
                    offsetX = sel.offsetX + dx,
                    offsetY = sel.offsetY + dy
                )
            )
        }
    }

    fun rotateSelection(degrees: Float) {
        _uiState.update { state ->
            val sel = state.activeSelection
            if (!sel.isActive) return@update state
            state.copy(activeSelection = sel.copy(rotationDegrees = (sel.rotationDegrees + degrees) % 360f))
        }
    }

    fun scaleSelection(factor: Float) {
        _uiState.update { state ->
            val sel = state.activeSelection
            if (!sel.isActive) return@update state
            val newX = (sel.scaleX * factor).coerceIn(0.1f, 10f)
            val newY = (sel.scaleY * factor).coerceIn(0.1f, 10f)
            state.copy(activeSelection = sel.copy(scaleX = newX, scaleY = newY))
        }
    }

    fun resetSelectionTransform() {
        _uiState.update { state ->
            val sel = state.activeSelection
            if (!sel.isActive) return@update state
            state.copy(activeSelection = sel.copy(offsetX = 0f, offsetY = 0f, scaleX = 1f, scaleY = 1f, rotationDegrees = 0f))
        }
    }

    fun applySelectionTransform() {
        val stateBefore = _uiState.value
        val sel = stateBefore.activeSelection
        if (!sel.isActive) return
        if (sel.offsetX != 0f || sel.offsetY != 0f || sel.scaleX != 1f || sel.scaleY != 1f || sel.rotationDegrees != 0f) {
            val frameIdx = stateBefore.currentFrameIndex
            val currFrame = stateBefore.project.frames.getOrNull(frameIdx) ?: return
            val useLocal = isUsingLocalLayer(stateBefore, currFrame)
            val targetList = if (useLocal) currFrame.localLayers else currFrame.layers
            val validLayerIdx = (if (useLocal) stateBefore.activeLocalLayerIndex else stateBefore.activeLayerIndex)
                .coerceIn(0, targetList.lastIndex)
            val currLayer = targetList[validLayerIdx]
            val targetOldIds = if (sel.hiddenSourceStrokeIds.isNotEmpty()) {
                sel.hiddenSourceStrokeIds
            } else {
                sel.capturedStrokes.map { it.id }.toSet()
            }
            val oldStrokes = currLayer.strokes.filter { it.id in targetOldIds }
            val myId = stateBefore.userAccount.id
            val transformed = sel.capturedStrokes.map { stroke ->
                SelectionGeometryHelper.transformStrokeWithSelection(stroke, sel, myId)
            }
            val replacementStrokes = sel.outsideReplacementStrokes + transformed

            if (oldStrokes.isNotEmpty() || replacementStrokes.isNotEmpty()) {
                pushUndoOp(CanvasOp.ReplaceStrokes(myId, currFrame.id, validLayerIdx, oldStrokes, replacementStrokes, isLocalLayer = useLocal))
            }

            _uiState.update { state ->
                val f = state.project.frames[state.currentFrameIndex]
                val updatedFrame = if (useLocal && f.localLayers.isNotEmpty()) {
                    val l = f.localLayers[validLayerIdx]
                    val otherStrokes = l.strokes.filter { it.id !in targetOldIds }
                    val updatedLayers = f.localLayers.toMutableList()
                    updatedLayers[validLayerIdx] = l.copy(strokes = otherStrokes + replacementStrokes)
                    f.copy(localLayers = updatedLayers)
                } else {
                    val l = f.layers[validLayerIdx]
                    val otherStrokes = l.strokes.filter { it.id !in targetOldIds }
                    val updatedLayers = f.layers.toMutableList()
                    updatedLayers[validLayerIdx] = l.copy(strokes = otherStrokes + replacementStrokes)
                    f.copy(layers = updatedLayers)
                }
                val updatedFrames = state.project.frames.toMutableList()
                updatedFrames[state.currentFrameIndex] = updatedFrame
                val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                state.copy(
                    project = updatedProj,
                    activeSelection = SelectionRegion(isActive = false)
                )
            }
            if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
                if (oldStrokes.isNotEmpty()) {
                    multiplayerEngine.sendUndoRemoveStrokes(myId, frameIdx, validLayerIdx, oldStrokes.map { it.id }, isLocalLayer = useLocal)
                }
                if (replacementStrokes.isNotEmpty()) {
                    multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, validLayerIdx, replacementStrokes, isLocalLayer = useLocal)
                }
            }
            updateUndoRedoAvailability()
        } else {
            clearSelection()
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(activeSelection = SelectionRegion(isActive = false))
        }
    }

    fun nextFrame() {
        val nextIdx = (_uiState.value.currentFrameIndex + 1) % _uiState.value.project.frames.size
        goToFrame(nextIdx)
    }

    fun prevFrame() {
        val total = _uiState.value.project.frames.size
        val prevIdx = if (_uiState.value.currentFrameIndex - 1 < 0) total - 1 else _uiState.value.currentFrameIndex - 1
        goToFrame(prevIdx)
    }

    fun addFrame() {
        pushCurrentFrameToUndo()
        var insertedIdx = 0
        _uiState.update { state ->
            saveCurrentFrameTargetPreference(state)
            val templateLayers = state.project.frames.firstOrNull()?.layers ?: listOf(Layer(name = "Camada 1"))
            val newFrameLayers = templateLayers.map { it.copy(id = UUID.randomUUID().toString(), strokes = emptyList()) }
            val newFrame = Frame(layers = newFrameLayers)
            val insertAt = state.currentFrameIndex + 1
            insertedIdx = insertAt
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames.add(insertAt, newFrame)
            val updatedProject = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProject)
            state.copy(
                project = updatedProject,
                currentFrameIndex = insertAt,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false
            )
        }
        updateUndoRedoAvailability()
        if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendAddFrame(insertedIdx)
        }
    }

    fun duplicateCurrentFrame() {
        _uiState.update { state ->
            saveCurrentFrameTargetPreference(state)
            val curr = state.project.frames[state.currentFrameIndex]
            val duplicatedLayers = curr.layers.map { layer ->
                layer.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    strokes = layer.strokes.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
                )
            }
            val duplicatedLocalLayers = curr.localLayers.map { layer ->
                layer.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    strokes = layer.strokes.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
                )
            }
            val duplicatedFrame = Frame(
                id = java.util.UUID.randomUUID().toString(),
                layers = duplicatedLayers,
                holdFrames = curr.holdFrames,
                localLayers = duplicatedLocalLayers
            )
            if (duplicatedLocalLayers.isNotEmpty() && state.activeTargetIsFrameLocalLayer) {
                perFrameLocalTarget[duplicatedFrame.id] = true to state.activeLocalLayerIndex.coerceIn(0, duplicatedLocalLayers.lastIndex)
            }
            val insertAt = state.currentFrameIndex + 1
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames.add(insertAt, duplicatedFrame)
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(
                project = updatedProj,
                currentFrameIndex = insertAt
            )
        }
        updateUndoRedoAvailability()
    }

    fun deleteCurrentFrame() {
        if (_uiState.value.project.frames.size <= 1) return
        val removedFrameId = _uiState.value.project.frames.getOrNull(_uiState.value.currentFrameIndex)?.id
        if (removedFrameId != null) {
            perFrameUndoOps.remove(removedFrameId)
            perFrameRedoOps.remove(removedFrameId)
            perFrameLocalTarget.remove(removedFrameId)
        }
        _uiState.update { state ->
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames.removeAt(state.currentFrameIndex)
            val newIndex = min(state.currentFrameIndex, updatedFrames.size - 1)
            val targetFrame = updatedFrames.getOrNull(newIndex)
            val (nextIsLocal, nextLocalIdx) = resolveTargetStateForFrame(
                targetFrame = targetFrame,
                fallbackActiveLocalIdx = state.activeLocalLayerIndex,
                fallbackIsLocal = state.activeTargetIsFrameLocalLayer
            )
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(
                project = updatedProj,
                currentFrameIndex = newIndex,
                activeLocalLayerIndex = nextLocalIdx,
                activeTargetIsFrameLocalLayer = nextIsLocal
            )
        }
        updateUndoRedoAvailability()
    }

    fun clearCurrentFrame() {
        val stateBefore = _uiState.value
        val clearedIdx = stateBefore.currentFrameIndex
        val currFrameBefore = stateBefore.project.frames.getOrNull(clearedIdx) ?: return
        val myId = stateBefore.userAccount.id
        pushUndoOp(
            CanvasOp.ClearFrame(
                authorId = myId,
                frameId = currFrameBefore.id,
                previousLayers = currFrameBefore.layers,
                previousLocalLayers = currFrameBefore.localLayers
            )
        )
        _uiState.update { state ->
            val currFrame = state.project.frames[state.currentFrameIndex]
            val clearedLayers = currFrame.layers.map { it.copy(strokes = emptyList()) }
            val clearedLocalLayers = currFrame.localLayers.map { it.copy(strokes = emptyList()) }
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[state.currentFrameIndex] = currFrame.copy(
                layers = clearedLayers,
                localLayers = clearedLocalLayers
            )
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj)
        }
        updateUndoRedoAvailability()
        if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendClearFrame(clearedIdx)
        }
    }

    fun addStrokeToCurrentLayer(stroke: DrawingStroke) {
        val stateBefore = _uiState.value
        val frameIndex = stateBefore.currentFrameIndex
        val currFrame = stateBefore.project.frames.getOrNull(frameIndex) ?: return
        val useLocal = isUsingLocalLayer(stateBefore, currFrame)
        val targetList = if (useLocal) currFrame.localLayers else currFrame.layers
        val validLayerIndex = (if (useLocal) stateBefore.activeLocalLayerIndex else stateBefore.activeLayerIndex)
            .coerceIn(0, targetList.lastIndex)
        val targetLayer = targetList[validLayerIndex]
        if (targetLayer.isLocked || !targetLayer.isVisible) {
            return
        }

        val myId = stateBefore.userAccount.id
        val activeSel = stateBefore.activeSelection
        val selectionClipPoly = if (activeSel.isActive && activeSel.points.size >= 3 && stroke.tool != ToolType.LASSO) {
            SelectionGeometryHelper.transformSelectionPolygon(activeSel)
        } else null
        val stampedStroke = SelectionGeometryHelper.withRecomputedBounds(
            stroke.copy(
                authorId = stroke.authorId.ifBlank { myId },
                clipPolygon = stroke.clipPolygon ?: selectionClipPoly
            )
        )
        val optimizedStroke = stampedStroke

        pushUndoOp(CanvasOp.AddStroke(myId, currFrame.id, validLayerIndex, optimizedStroke, isLocalLayer = useLocal))

        _uiState.update { state ->
            val f = state.project.frames[frameIndex]
            val updatedFrame = if (useLocal && f.localLayers.isNotEmpty()) {
                val safeIdx = validLayerIndex.coerceIn(0, f.localLayers.lastIndex)
                val l = f.localLayers[safeIdx]
                val updatedLayers = f.localLayers.toMutableList()
                updatedLayers[safeIdx] = l.copy(strokes = l.strokes + optimizedStroke)
                f.copy(localLayers = updatedLayers)
            } else {
                val safeIdx = validLayerIndex.coerceIn(0, f.layers.lastIndex)
                val l = f.layers[safeIdx]
                val updatedLayers = f.layers.toMutableList()
                updatedLayers[safeIdx] = l.copy(strokes = l.strokes + optimizedStroke)
                f.copy(layers = updatedLayers)
            }

            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIndex] = updatedFrame

            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj)
        }
        updateUndoRedoAvailability()

        // Broadcast committed stroke to multiplayer peers using exact validLayerIndex + local flag
        val currentMultiState = _uiState.value
        if (currentMultiState.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            shareImportedBrushIfNeeded(currentMultiState.activeBrushPreset)
            multiplayerEngine.sendStroke(
                frameIndex = frameIndex,
                layerIndex = validLayerIndex,
                stroke = optimizedStroke,
                isLocalLayer = useLocal
            )
        }
    }

    fun clearActiveLayer() {
        val stateBefore = _uiState.value
        val frameIdx = stateBefore.currentFrameIndex
        val currFrameBefore = stateBefore.project.frames.getOrNull(frameIdx) ?: return
        val useLocal = isUsingLocalLayer(stateBefore, currFrameBefore)
        val targetList = if (useLocal) currFrameBefore.localLayers else currFrameBefore.layers
        val validLayerIdx = (if (useLocal) stateBefore.activeLocalLayerIndex else stateBefore.activeLayerIndex)
            .coerceIn(0, targetList.lastIndex)
        val currLayer = targetList[validLayerIdx]
        if (currLayer.isLocked || currLayer.strokes.isEmpty()) return
        val removedList = currLayer.strokes
        val myId = stateBefore.userAccount.id
        pushUndoOp(CanvasOp.RemoveStrokes(myId, currFrameBefore.id, validLayerIdx, removedList, isLocalLayer = useLocal))
        _uiState.update { state ->
            val f = state.project.frames[frameIdx]
            val updatedFrame = if (useLocal && f.localLayers.isNotEmpty()) {
                val l = f.localLayers[validLayerIdx]
                val updatedLayers = f.localLayers.toMutableList()
                updatedLayers[validLayerIdx] = l.copy(strokes = emptyList())
                f.copy(localLayers = updatedLayers)
            } else {
                val l = f.layers[validLayerIdx]
                val updatedLayers = f.layers.toMutableList()
                updatedLayers[validLayerIdx] = l.copy(strokes = emptyList())
                f.copy(layers = updatedLayers)
            }
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIdx] = updatedFrame
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj, statusMessage = "Camada limpa!")
        }
        updateUndoRedoAvailability()
        if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendUndoRemoveStrokes(
                myId,
                frameIdx,
                validLayerIdx,
                removedList.map { it.id },
                isLocalLayer = useLocal
            )
        }
    }

    private fun scheduleDebouncedHostSave(ctx: Context, proj: AnimationProject) {
        debouncedHostSaveJob?.cancel()
        debouncedHostSaveJob = viewModelScope.launch(KodanimaDispatchers.networkIo) {
            delay(1200L)
            com.example.engine.ProjectStorageManager.saveProject(ctx, proj)
        }
    }

    fun receiveRemoteStroke(
        frameIndex: Int,
        layerIndex: Int,
        stroke: DrawingStroke,
        isLocalLayer: Boolean = false
    ) {
        var hostProjectToSave: AnimationProject? = null
        _uiState.update { state ->
            if (frameIndex !in state.project.frames.indices) return@update state
            val targetFrame = state.project.frames[frameIndex]
            val updatedFrame = if (isLocalLayer && targetFrame.localLayers.isNotEmpty()) {
                val validIdx = layerIndex.coerceIn(0, targetFrame.localLayers.lastIndex)
                val targetLayer = targetFrame.localLayers[validIdx]
                if (targetLayer.strokes.any { it.id == stroke.id }) return@update state
                val updatedLayers = targetFrame.localLayers.toMutableList()
                updatedLayers[validIdx] = targetLayer.copy(strokes = targetLayer.strokes + stroke)
                targetFrame.copy(localLayers = updatedLayers)
            } else {
                val validIdx = layerIndex.coerceIn(0, targetFrame.layers.lastIndex)
                val targetLayer = targetFrame.layers[validIdx]
                if (targetLayer.strokes.any { it.id == stroke.id }) return@update state
                val updatedLayers = targetFrame.layers.toMutableList()
                updatedLayers[validIdx] = targetLayer.copy(strokes = targetLayer.strokes + stroke)
                targetFrame.copy(layers = updatedLayers)
            }

            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIndex] = updatedFrame

            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            if (state.multiplayerRole == com.example.model.MultiplayerRole.HOST) {
                hostProjectToSave = updatedProj
            }
            val cleanedLive = if (stroke.authorId.isNotBlank()) {
                state.remoteLiveStrokes - stroke.authorId
            } else {
                state.remoteLiveStrokes
            }
            state.copy(project = updatedProj, remoteLiveStrokes = cleanedLive)
        }
        hostProjectToSave?.let { proj ->
            appContext?.let { ctx -> scheduleDebouncedHostSave(ctx, proj) }
        }
    }

    fun applyRemoteUndoRedoOp(
        opType: String,
        @Suppress("UNUSED_PARAMETER") authorId: String,
        frameIndex: Int,
        layerIndex: Int,
        strokeIds: List<String>,
        strokes: List<DrawingStroke>,
        isLocalLayer: Boolean = false
    ) {
        var hostProjectToSave: AnimationProject? = null
        _uiState.update { state ->
            if (frameIndex !in state.project.frames.indices) return@update state
            val targetFrame = state.project.frames[frameIndex]
            val useLocal = isLocalLayer && targetFrame.localLayers.isNotEmpty()
            val targetList = if (useLocal) targetFrame.localLayers else targetFrame.layers
            if (targetList.isEmpty()) return@update state
            val validLayerIndex = layerIndex.coerceIn(0, targetList.lastIndex)
            val targetLayer = targetList[validLayerIndex]

            val updatedStrokes = when (opType) {
                "UNDO_STROKE" -> {
                    val removeSet = strokeIds.toSet()
                    if (removeSet.isEmpty()) return@update state
                    targetLayer.strokes.filter { it.id !in removeSet }
                }
                "REDO_STROKE" -> {
                    val existingIds = targetLayer.strokes.map { it.id }.toSet()
                    val toAdd = strokes.filter { it.id !in existingIds }
                    if (toAdd.isEmpty()) return@update state
                    targetLayer.strokes + toAdd
                }
                else -> return@update state
            }

            val updatedList = targetList.toMutableList()
            updatedList[validLayerIndex] = targetLayer.copy(strokes = updatedStrokes)
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIndex] = if (useLocal) {
                targetFrame.copy(localLayers = updatedList)
            } else {
                targetFrame.copy(layers = updatedList)
            }

            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            if (state.multiplayerRole == com.example.model.MultiplayerRole.HOST) {
                hostProjectToSave = updatedProj
            }
            state.copy(project = updatedProj)
        }
        hostProjectToSave?.let { proj ->
            appContext?.let { ctx -> scheduleDebouncedHostSave(ctx, proj) }
        }
    }

    private fun applyRemoteFrameLocalLayers(frameIndex: Int, localLayers: List<Layer>) {
        var hostProjectToSave: AnimationProject? = null
        _uiState.update { state ->
            if (frameIndex !in state.project.frames.indices) return@update state
            val targetFrame = state.project.frames[frameIndex]
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIndex] = targetFrame.copy(localLayers = localLayers)
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            if (state.multiplayerRole == com.example.model.MultiplayerRole.HOST) {
                hostProjectToSave = updatedProj
            }
            val nextActiveLocalIdx = if (frameIndex == state.currentFrameIndex && localLayers.isNotEmpty()) {
                state.activeLocalLayerIndex.coerceIn(0, localLayers.lastIndex)
            } else if (frameIndex == state.currentFrameIndex) {
                0
            } else state.activeLocalLayerIndex
            val nextTargetIsLocal = if (frameIndex == state.currentFrameIndex && localLayers.isEmpty()) {
                false
            } else state.activeTargetIsFrameLocalLayer
            state.copy(
                project = updatedProj,
                activeLocalLayerIndex = nextActiveLocalIdx,
                activeTargetIsFrameLocalLayer = nextTargetIsLocal
            )
        }
        hostProjectToSave?.let { proj ->
            appContext?.let { ctx -> scheduleDebouncedHostSave(ctx, proj) }
        }
    }

    private fun applyRemoteClearFrame(frameIndex: Int) {
        _uiState.update { state ->
            if (frameIndex !in state.project.frames.indices) return@update state
            val targetFrame = state.project.frames[frameIndex]
            val clearedLayers = targetFrame.layers.map { it.copy(strokes = emptyList()) }
            val clearedLocalLayers = targetFrame.localLayers.map { it.copy(strokes = emptyList()) }
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIndex] = targetFrame.copy(layers = clearedLayers, localLayers = clearedLocalLayers)
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj)
        }
    }

    private fun applyRemoteAddFrame(insertAt: Int) {
        _uiState.update { state ->
            val templateLayers = state.project.frames.firstOrNull()?.layers ?: listOf(Layer(name = "Camada 1"))
            val newFrameLayers = templateLayers.map { it.copy(id = UUID.randomUUID().toString(), strokes = emptyList()) }
            val newFrame = Frame(layers = newFrameLayers)
            val updatedFrames = state.project.frames.toMutableList()
            val pos = insertAt.coerceIn(0, updatedFrames.size)
            updatedFrames.add(pos, newFrame)
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj)
        }
    }

    private fun applyRemoteAddLayer(layerName: String) {
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { frame ->
                frame.copy(layers = frame.layers + Layer(name = layerName))
            }
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj)
        }
    }

    // --- Multiplayer & Local Server Management ---

    fun updateUserAccount(name: String, avatarUri: String?, avatarColor: Long) {
        val currentAcc = _uiState.value.userAccount
        val ctx = appContext
        val cleanName = name.trim().ifEmpty { currentAcc.name.ifBlank { "Animador" } }

        // 1. Immediately persist basic fields synchronously so returning or switching tabs never loses changes
        val immediateUpdated = currentAcc.copy(
            name = cleanName,
            avatarUri = avatarUri ?: currentAcc.avatarUri,
            avatarBase64 = if (avatarUri == null) null else currentAcc.avatarBase64,
            avatarColor = avatarColor
        )
        ctx?.let { PreferencesStorageManager.saveUserAccount(it, immediateUpdated) }
        multiplayerEngine.updateLocalUser(immediateUpdated)
        _uiState.update { it.copy(userAccount = immediateUpdated) }

        // 2. If a new photo URI was supplied, encode + copy thumbnail to app storage on IO and re-commit
        if (ctx != null && !avatarUri.isNullOrBlank() &&
            (avatarUri != currentAcc.avatarUri || currentAcc.avatarBase64.isNullOrBlank())
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val (finalUri, finalBase64) = com.example.engine.AvatarTransferHelper.prepareLocalAvatar(ctx, avatarUri)
                val finalAccount = _uiState.value.userAccount.copy(
                    name = cleanName,
                    avatarUri = finalUri ?: avatarUri,
                    avatarBase64 = finalBase64,
                    avatarColor = avatarColor
                )
                PreferencesStorageManager.saveUserAccount(ctx, finalAccount)
                multiplayerEngine.updateLocalUser(finalAccount)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(userAccount = finalAccount) }
                }
                multiplayerEngine.broadcastAccountPresence(finalAccount)
            }
        } else {
            multiplayerEngine.broadcastAccountPresence(immediateUpdated)
        }
    }

    fun startHostingLocalServer(project: AnimationProject): com.example.model.RemoteRoomInfo {
        val account = _uiState.value.userAccount
        val transport = _uiState.value.preferences.networkTransportMode
        appContext?.let { PreferencesStorageManager.saveUserAccount(it, account) }

        val roomInfo = multiplayerEngine.startHosting(account, project)
        if (transport == NetworkTransportMode.WIFI_DIRECT) {
            wifiDirectHelper?.createGroupForHosting()
        } else if (transport == NetworkTransportMode.BLUETOOTH) {
            bluetoothCoopManager?.startRfcommServer()
        }

        appContext?.let { ctx ->
            LocalServerForegroundService.startOrUpdate(
                context = ctx,
                roomId = roomInfo.roomId,
                projectName = project.name,
                transportLabel = transportLabel(transport),
                peerCount = 1
            )
        }

        _uiState.update {
            it.copy(
                project = project,
                currentFrameIndex = 0,
                multiplayerRole = com.example.model.MultiplayerRole.HOST,
                connectionStatus = com.example.model.ConnectionStatus.CONNECTED,
                activeRoomInfo = roomInfo,
                isLocalServerActive = true,
                statusMessage = "Servidor Local ativo na sala ${roomInfo.roomId} (${transportLabel(transport)})!"
            )
        }
        return roomInfo
    }

    fun stopMultiplayerSession() {
        val wasHost = _uiState.value.multiplayerRole == com.example.model.MultiplayerRole.HOST ||
            _uiState.value.isLocalServerActive
        if (wasHost) {
            multiplayerEngine.shutdownHostAndNotify("o dono da casa saiu :(")
        } else {
            multiplayerEngine.stopSessionOnly()
        }
        bluetoothCoopManager?.stopServerOnly()
        appContext?.let { LocalServerForegroundService.stop(it) }
        sentBrushFingerprints.clear()
        dismissedFriendBrushKeys.clear()
        _uiState.update {
            it.copy(
                multiplayerRole = com.example.model.MultiplayerRole.NONE,
                connectionStatus = com.example.model.ConnectionStatus.DISCONNECTED,
                activeRoomInfo = null,
                isLocalServerActive = false,
                pendingKnockRequest = null,
                connectedFriend = null,
                connectedFriends = emptyList(),
                remoteCursors = emptyMap(),
                remoteLiveStrokes = emptyMap(),
                pendingFriendBrushOffer = null,
                statusMessage = if (wasHost) "Servidor Local encerrado." else "Você saiu da sala colaborativa."
            )
        }
    }

    fun startDiscoveringRooms() {
        if (!_uiState.value.preferences.isMultiplayerEnabled) return
        multiplayerEngine.startDiscovery(_uiState.value.userAccount, forceSubnetScan = true)
    }

    fun stopDiscoveringRooms() {
        // Keep background discovery running while multiplayer is enabled so friend requests & invites never drop.
    }

    fun setNetworkTransportMode(mode: NetworkTransportMode) {
        updatePreferences { it.copy(networkTransportMode = mode) }
        when (mode) {
            NetworkTransportMode.WIFI_LAN -> {
                startDiscoveringRooms()
            }
            NetworkTransportMode.WIFI_DIRECT -> {
                wifiDirectHelper?.discoverPeers()
                if (_uiState.value.isLocalServerActive) {
                    wifiDirectHelper?.createGroupForHosting()
                }
            }
            NetworkTransportMode.BLUETOOTH -> {
                bluetoothCoopManager?.refreshPairedAndScan()
                if (_uiState.value.isLocalServerActive) {
                    bluetoothCoopManager?.startRfcommServer()
                }
            }
        }
    }

    fun markInitialPermissionsPrompted() {
        updatePreferences { it.copy(hasPromptedInitialPermissions = true) }
    }

    fun refreshWifiDirectPeers() {
        wifiDirectHelper?.discoverPeers()
    }

    fun connectWifiDirectPeer(deviceAddress: String) {
        autoJoinOnWifiDirectConnect = true
        wifiDirectHelper?.connectToPeer(deviceAddress)
    }

    fun refreshBluetoothPeers() {
        bluetoothCoopManager?.refreshPairedAndScan()
    }

    fun connectBluetoothHost(deviceAddress: String) {
        _uiState.update {
            it.copy(
                multiplayerRole = com.example.model.MultiplayerRole.CLIENT,
                connectionStatus = com.example.model.ConnectionStatus.KNOCKING,
                statusMessage = "Conectando via Bluetooth..."
            )
        }
        bluetoothCoopManager?.connectToHostDevice(deviceAddress)
    }

    fun joinRoom(room: com.example.model.RemoteRoomInfo) {
        val account = _uiState.value.userAccount
        _uiState.update {
            it.copy(
                multiplayerRole = com.example.model.MultiplayerRole.CLIENT,
                connectionStatus = com.example.model.ConnectionStatus.KNOCKING,
                activeRoomInfo = room,
                statusMessage = "Aguardando aprovação de ${room.hostName}..."
            )
        }
        multiplayerEngine.connectToRoom(room.hostIp, room.hostPort, account, room.roomId)
    }

    fun respondToKnock(allow: Boolean) {
        val pending = _uiState.value.pendingKnockRequest ?: return
        val currentProj = _uiState.value.project
        val myAccount = _uiState.value.userAccount
        _uiState.update { state ->
            state.copy(
                pendingKnockRequest = null,
                currentScreen = if (allow) AppScreen.EDITOR else state.currentScreen,
                statusMessage = if (allow) "${pending.user.name} entrou na sala!" else "Entrada recusada"
            )
        }
        multiplayerEngine.answerKnock(pending.connectionId, allow, currentProj, myAccount)
    }

    fun broadcastLocalCursor(x: Float, y: Float, isDrawing: Boolean) {
        val state = _uiState.value
        if (state.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendCursor(
                state.userAccount,
                state.currentFrameIndex,
                x,
                y,
                isDrawing
            )
        }
    }

    fun broadcastLocalLiveStroke(stroke: DrawingStroke?) {
        val state = _uiState.value
        if (state.multiplayerRole == com.example.model.MultiplayerRole.NONE) return
        val frameIdx = state.currentFrameIndex
        val currFrame = state.project.frames.getOrNull(frameIdx) ?: return
        val useLocal = isUsingLocalLayer(state, currFrame)
        val targetList = if (useLocal) currFrame.localLayers else currFrame.layers
        val validLayerIdx = (if (useLocal) state.activeLocalLayerIndex else state.activeLayerIndex)
            .coerceIn(0, targetList.lastIndex)
        val stampedStroke = stroke?.copy(authorId = state.userAccount.id)
        multiplayerEngine.sendLiveStrokePreview(
            peerId = state.userAccount.id,
            frameIndex = frameIdx,
            layerIndex = validLayerIdx,
            stroke = stampedStroke,
            isLocalLayer = useLocal
        )
    }

    // --- Friends, Partnerships & Project Room Invites ---

    fun sendFriendRequest(targetInput: String) {
        val target = targetInput.trim()
        if (target.isEmpty()) return
        val myAccount = _uiState.value.userAccount
        val targetId = if (target.startsWith("KD-", ignoreCase = true)) target.uppercase() else ""
        val targetName = if (targetId.isEmpty()) target else ""

        multiplayerEngine.broadcastFriendRequest(myAccount, targetId, targetName)

        val targetFriend = com.example.model.FriendEntry(
            id = if (targetId.isNotEmpty()) targetId else "KD-${(1000..9999).random()}",
            name = target,
            avatarColor = 0xFFBE699A,
            status = com.example.model.FriendStatus.ONLINE
        )

        val updatedFriends = _uiState.value.friendsList.filterNot {
            it.name.equals(target, ignoreCase = true) || (targetId.isNotEmpty() && it.id.equals(targetId, ignoreCase = true))
        } + targetFriend
        appContext?.let { ctx ->
            PreferencesStorageManager.saveFriendsList(ctx, updatedFriends)
        }
        _uiState.update {
            it.copy(
                friendsList = updatedFriends,
                statusMessage = "Pedido de amizade transmitido na rede para $target!"
            )
        }
    }

    fun acceptFriendRequest(requestId: String) {
        val req = _uiState.value.friendRequests.firstOrNull { it.id == requestId } ?: return
        val myAccount = _uiState.value.userAccount

        multiplayerEngine.broadcastFriendAccept(
            acceptor = myAccount,
            targetUserId = req.senderId,
            targetIp = req.senderIp,
            targetControlPort = req.senderControlPort
        )

        val newFriend = com.example.model.FriendEntry(
            id = req.senderId,
            name = req.senderName,
            avatarUri = req.senderAvatarUri,
            avatarBase64 = req.senderAvatarBase64,
            avatarColor = req.senderAvatarColor,
            status = com.example.model.FriendStatus.ONLINE,
            lastKnownIp = req.senderIp,
            lastKnownControlPort = req.senderControlPort
        )
        val updatedRequests = _uiState.value.friendRequests.filter { it.id != requestId }
        val updatedFriends = _uiState.value.friendsList.filterNot { it.id.equals(req.senderId, ignoreCase = true) } + newFriend
        appContext?.let { ctx ->
            PreferencesStorageManager.saveFriendRequests(ctx, updatedRequests)
            PreferencesStorageManager.saveFriendsList(ctx, updatedFriends)
        }
        _uiState.update {
            it.copy(
                friendRequests = updatedRequests,
                friendsList = updatedFriends,
                incomingFriendRequestModal = null,
                statusMessage = "Agora você e ${newFriend.name} são parceiros!"
            )
        }
    }

    fun rejectFriendRequest(requestId: String) {
        val updatedRequests = _uiState.value.friendRequests.filter { it.id != requestId }
        appContext?.let { ctx ->
            PreferencesStorageManager.saveFriendRequests(ctx, updatedRequests)
        }
        _uiState.update {
            it.copy(
                friendRequests = updatedRequests,
                incomingFriendRequestModal = null,
                statusMessage = "Pedido recusado."
            )
        }
    }

    fun dismissIncomingFriendRequest() {
        _uiState.update { it.copy(incomingFriendRequestModal = null) }
    }

    fun inviteFriendToProject(friend: com.example.model.FriendEntry, project: AnimationProject) {
        val state = _uiState.value
        val activeRoom = state.activeRoomInfo
        val roomInfo = if (state.isLocalServerActive && activeRoom != null && state.project.id == project.id) {
            activeRoom
        } else {
            startHostingLocalServer(project)
        }
        multiplayerEngine.sendRoomInvite(_uiState.value.userAccount, roomInfo, friend)
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.EDITOR,
                statusMessage = "Convite para '${project.name}' enviado para ${friend.name}!"
            )
        }
    }

    fun acceptRoomInvite(invite: com.example.model.RoomInviteNotification) {
        _uiState.update { it.copy(incomingRoomInviteModal = null) }
        joinRoom(invite.roomInfo)
    }

    fun dismissIncomingRoomInvite() {
        _uiState.update { it.copy(incomingRoomInviteModal = null) }
    }

    fun removeFriend(friendId: String) {
        val updated = _uiState.value.friendsList.filter { it.id != friendId }
        appContext?.let { ctx ->
            PreferencesStorageManager.saveFriendsList(ctx, updated)
        }
        _uiState.update { it.copy(friendsList = updated) }
    }

    fun setMultiplayerEnabled(enabled: Boolean) {
        updatePreferences { it.copy(isMultiplayerEnabled = enabled) }
        if (!enabled) {
            if (_uiState.value.multiplayerRole == com.example.model.MultiplayerRole.HOST || _uiState.value.isLocalServerActive) {
                multiplayerEngine.shutdownHostAndNotify("o dono da casa saiu :(")
            }
            wifiDirectHelper?.stop()
            bluetoothCoopManager?.stopAll()
            multiplayerEngine.stopAll()
            appContext?.let { LocalServerForegroundService.stop(it) }
            _uiState.update {
                it.copy(
                    multiplayerRole = com.example.model.MultiplayerRole.NONE,
                    connectionStatus = com.example.model.ConnectionStatus.DISCONNECTED,
                    activeRoomInfo = null,
                    isLocalServerActive = false,
                    pendingKnockRequest = null,
                    connectedFriend = null,
                    connectedFriends = emptyList(),
                    remoteCursors = emptyMap(),
                    remoteLiveStrokes = emptyMap(),
                    discoveredRooms = emptyList()
                )
            }
        } else {
            startDiscoveringRooms()
        }
    }

    // --- Layers Management ---

    fun setActiveLayer(index: Int) {
        val state = _uiState.value
        val currFrame = state.project.frames.getOrNull(state.currentFrameIndex) ?: return
        val clamped = index.coerceIn(0, (currFrame.layers.size - 1).coerceAtLeast(0))
        perFrameLocalTarget[currFrame.id] = false to 0
        _uiState.update { it.copy(activeLayerIndex = clamped, activeTargetIsFrameLocalLayer = false) }
    }

    fun addLayer() {
        pushCurrentFrameToUndo()
        var createdName = "Camada"
        _uiState.update { state ->
            val currLayers = state.project.frames.firstOrNull()?.layers ?: emptyList()
            val newLayerName = "Camada ${currLayers.size + 1}"
            createdName = newLayerName
            val updatedFrames = state.project.frames.map { frame ->
                frame.copy(layers = frame.layers + Layer(name = newLayerName))
            }
            val updatedProj = state.project.copy(frames = updatedFrames)
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(
                project = updatedProj,
                activeLayerIndex = currLayers.size
            )
        }
        updateUndoRedoAvailability()
        if (_uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendAddLayer(createdName)
        }
    }

    fun deleteActiveLayer() {
        val currLayers = _uiState.value.project.frames.firstOrNull()?.layers ?: emptyList()
        if (currLayers.size <= 1) return
        pushCurrentFrameToUndo()
        _uiState.update { state ->
            val deleteIdx = state.activeLayerIndex
            val updatedFrames = state.project.frames.map { frame ->
                if (deleteIdx in frame.layers.indices) {
                    val list = frame.layers.toMutableList()
                    list.removeAt(deleteIdx)
                    frame.copy(layers = list)
                } else frame
            }
            val newActiveIndex = min(deleteIdx, (updatedFrames.firstOrNull()?.layers?.size ?: 1) - 1).coerceAtLeast(0)
            state.copy(
                project = state.project.copy(frames = updatedFrames),
                activeLayerIndex = newActiveIndex
            )
        }
        updateUndoRedoAvailability()
    }

    fun toggleLayerVisibility(layerIndex: Int) {
        _uiState.update { state ->
            val currVis = state.project.frames.firstOrNull()?.layers?.getOrNull(layerIndex)?.isVisible ?: true
            val newVis = !currVis
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex in frame.layers.indices) {
                    val updatedLayers = frame.layers.mapIndexed { idx, layer ->
                        if (idx == layerIndex) layer.copy(isVisible = newVis) else layer
                    }
                    frame.copy(layers = updatedLayers)
                } else frame
            }
            state.copy(project = state.project.copy(frames = updatedFrames))
        }
    }

    fun toggleLayerLock(layerIndex: Int) {
        _uiState.update { state ->
            val currLock = state.project.frames.firstOrNull()?.layers?.getOrNull(layerIndex)?.isLocked ?: false
            val newLock = !currLock
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex in frame.layers.indices) {
                    val updatedLayers = frame.layers.mapIndexed { idx, layer ->
                        if (idx == layerIndex) layer.copy(isLocked = newLock) else layer
                    }
                    frame.copy(layers = updatedLayers)
                } else frame
            }
            state.copy(project = state.project.copy(frames = updatedFrames))
        }
    }

    fun setLayerOpacity(layerIndex: Int, opacity: Float) {
        _uiState.update { state ->
            val clamped = opacity.coerceIn(0f, 1f)
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex in frame.layers.indices) {
                    val updatedLayers = frame.layers.mapIndexed { idx, layer ->
                        if (idx == layerIndex) layer.copy(opacity = clamped) else layer
                    }
                    frame.copy(layers = updatedLayers)
                } else frame
            }
            state.copy(project = state.project.copy(frames = updatedFrames))
        }
    }

    // --- Per-Frame Isolated Sub-Layers ("Camadas do Quadro" + Efeitos de Mesclagem + Recorte) ---

    fun toggleFrameLayersPanel() {
        _uiState.update { it.copy(isFrameLayersPanelOpen = !it.isFrameLayersPanelOpen) }
    }

    fun openFrameLayersPanel() {
        _uiState.update { it.copy(isFrameLayersPanelOpen = true) }
    }

    fun closeFrameLayersPanel() {
        _uiState.update { it.copy(isFrameLayersPanelOpen = false) }
    }

    fun selectFrameLocalLayer(localIndex: Int) {
        _uiState.update { state ->
            val currFrame = state.project.frames.getOrNull(state.currentFrameIndex) ?: return@update state
            if (currFrame.localLayers.isEmpty()) {
                perFrameLocalTarget[currFrame.id] = false to 0
                state.copy(activeLocalLayerIndex = 0, activeTargetIsFrameLocalLayer = false)
            } else {
                val clamped = localIndex.coerceIn(0, currFrame.localLayers.lastIndex)
                perFrameLocalTarget[currFrame.id] = true to clamped
                state.copy(activeLocalLayerIndex = clamped, activeTargetIsFrameLocalLayer = true)
            }
        }
    }

    fun useTimelineLayerTarget() {
        _uiState.update { state ->
            state.project.frames.getOrNull(state.currentFrameIndex)?.let { currFrame ->
                perFrameLocalTarget[currFrame.id] = false to 0
            }
            state.copy(activeTargetIsFrameLocalLayer = false)
        }
    }

    private fun updateCurrentFrameLocalLayers(
        transform: (List<Layer>, Int) -> Pair<List<Layer>, Int?>
    ) {
        var syncedFrameIdx = -1
        var syncedLocals: List<Layer>? = null
        var hostProjectToSave: AnimationProject? = null

        _uiState.update { state ->
            val frameIdx = state.currentFrameIndex
            val currFrame = state.project.frames.getOrNull(frameIdx) ?: return@update state
            val (nextLocals, preferredActive) = transform(currFrame.localLayers, state.activeLocalLayerIndex)
            val updatedFrames = state.project.frames.toMutableList()
            updatedFrames[frameIdx] = currFrame.copy(localLayers = nextLocals)
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            if (state.multiplayerRole == com.example.model.MultiplayerRole.HOST) {
                hostProjectToSave = updatedProj
            }
            syncedFrameIdx = frameIdx
            syncedLocals = nextLocals

            val nextActiveIdx = if (nextLocals.isEmpty()) {
                0
            } else {
                (preferredActive ?: state.activeLocalLayerIndex).coerceIn(0, nextLocals.lastIndex)
            }
            val nextTargetIsLocal = when {
                nextLocals.isEmpty() -> false
                preferredActive != null -> true
                else -> state.activeTargetIsFrameLocalLayer
            }
            perFrameLocalTarget[currFrame.id] = nextTargetIsLocal to nextActiveIdx
            state.copy(
                project = updatedProj,
                activeLocalLayerIndex = nextActiveIdx,
                activeTargetIsFrameLocalLayer = nextTargetIsLocal
            )
        }

        if (syncedFrameIdx >= 0 && syncedLocals != null && _uiState.value.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
            multiplayerEngine.sendSyncFrameLocalLayers(syncedFrameIdx, syncedLocals!!)
        }
        hostProjectToSave?.let { proj ->
            appContext?.let { ctx -> scheduleDebouncedHostSave(ctx, proj) }
        }
    }

    fun addFrameLocalLayer(name: String? = null) {
        pushCurrentFrameToUndo()
        updateCurrentFrameLocalLayers { locals, _ ->
            val layerName = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Subcamada ${locals.size + 1}"
            val updated = locals + Layer(name = layerName)
            updated to updated.lastIndex
        }
        updateUndoRedoAvailability()
    }

    fun deleteFrameLocalLayer(localIndex: Int) {
        val currFrame = _uiState.value.project.frames.getOrNull(_uiState.value.currentFrameIndex) ?: return
        if (localIndex !in currFrame.localLayers.indices) return
        pushCurrentFrameToUndo()
        updateCurrentFrameLocalLayers { locals, activeIdx ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable.removeAt(localIndex)
            val nextIdx = if (mutable.isEmpty()) null else min(activeIdx, mutable.lastIndex).coerceAtLeast(0)
            mutable to nextIdx
        }
        updateUndoRedoAvailability()
    }

    fun renameFrameLocalLayer(localIndex: Int, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        updateCurrentFrameLocalLayers { locals, _ ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable[localIndex] = mutable[localIndex].copy(name = clean)
            mutable to null
        }
    }

    fun moveFrameLocalLayerUp(localIndex: Int) {
        // Rendered bottom-to-top (higher index is above lower index), UI displays top-most first:
        // moving "up" visually shifts item toward locals.lastIndex.
        updateCurrentFrameLocalLayers { locals, activeIdx ->
            if (localIndex !in locals.indices || localIndex >= locals.lastIndex) {
                return@updateCurrentFrameLocalLayers locals to null
            }
            val mutable = locals.toMutableList()
            val item = mutable.removeAt(localIndex)
            mutable.add(localIndex + 1, item)
            val nextActive = if (activeIdx == localIndex) localIndex + 1 else if (activeIdx == localIndex + 1) localIndex else activeIdx
            mutable to nextActive
        }
    }

    fun moveFrameLocalLayerDown(localIndex: Int) {
        updateCurrentFrameLocalLayers { locals, activeIdx ->
            if (localIndex <= 0 || localIndex !in locals.indices) {
                return@updateCurrentFrameLocalLayers locals to null
            }
            val mutable = locals.toMutableList()
            val item = mutable.removeAt(localIndex)
            mutable.add(localIndex - 1, item)
            val nextActive = if (activeIdx == localIndex) localIndex - 1 else if (activeIdx == localIndex - 1) localIndex else activeIdx
            mutable to nextActive
        }
    }

    fun toggleFrameLocalLayerVisibility(localIndex: Int) {
        updateCurrentFrameLocalLayers { locals, _ ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable[localIndex] = mutable[localIndex].copy(isVisible = !mutable[localIndex].isVisible)
            mutable to null
        }
    }

    fun toggleFrameLocalLayerLock(localIndex: Int) {
        updateCurrentFrameLocalLayers { locals, _ ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable[localIndex] = mutable[localIndex].copy(isLocked = !mutable[localIndex].isLocked)
            mutable to null
        }
    }

    fun setFrameLocalLayerOpacity(localIndex: Int, opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        updateCurrentFrameLocalLayers { locals, _ ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable[localIndex] = mutable[localIndex].copy(opacity = clamped)
            mutable to null
        }
    }

    fun setFrameLocalLayerBlendEffect(localIndex: Int, effect: LayerBlendEffect) {
        updateCurrentFrameLocalLayers { locals, _ ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable[localIndex] = mutable[localIndex].copy(blendEffect = effect)
            mutable to null
        }
    }

    fun toggleFrameLocalLayerClippingMask(localIndex: Int) {
        updateCurrentFrameLocalLayers { locals, _ ->
            if (localIndex !in locals.indices) return@updateCurrentFrameLocalLayers locals to null
            val mutable = locals.toMutableList()
            mutable[localIndex] = mutable[localIndex].copy(isClippingMask = !mutable[localIndex].isClippingMask)
            mutable to null
        }
    }

    // --- Per-User Independent Undo / Redo (Synced Over LAN) ---

    private fun pushUndoOp(op: CanvasOp) {
        val list = perFrameUndoOps.getOrPut(op.frameId) { mutableListOf() }
        list.add(op)
        if (list.size > maxHistorySize) {
            list.removeAt(0)
        }
        perFrameRedoOps[op.frameId]?.clear()
    }

    private fun pushCurrentFrameToUndo() {
        val curr = _uiState.value.project.frames.getOrNull(_uiState.value.currentFrameIndex) ?: return
        pushUndoOp(CanvasOp.FrameSnapshot(curr.id, curr))
    }

    fun undo() {
        val stateBefore = _uiState.value
        val frameIdx = stateBefore.currentFrameIndex
        val currFrame = stateBefore.project.frames.getOrNull(frameIdx) ?: return
        val undoStack = perFrameUndoOps[currFrame.id] ?: return
        if (undoStack.isEmpty()) return

        val op = undoStack.removeAt(undoStack.lastIndex)
        val redoStack = perFrameRedoOps.getOrPut(currFrame.id) { mutableListOf() }
        redoStack.add(op)
        if (redoStack.size > maxHistorySize) {
            redoStack.removeAt(0)
        }

        val myId = stateBefore.userAccount.id
        val isCoop = stateBefore.multiplayerRole != com.example.model.MultiplayerRole.NONE

        when (op) {
            is CanvasOp.AddStroke -> {
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedFrame = if (op.isLocalLayer && f.localLayers.isNotEmpty()) {
                        val validL = op.layerIndex.coerceIn(0, f.localLayers.lastIndex)
                        val l = f.localLayers[validL]
                        val updatedLayers = f.localLayers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = l.strokes.filter { it.id != op.stroke.id })
                        f.copy(localLayers = updatedLayers)
                    } else {
                        val validL = op.layerIndex.coerceIn(0, f.layers.lastIndex)
                        val l = f.layers[validL]
                        val updatedLayers = f.layers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = l.strokes.filter { it.id != op.stroke.id })
                        f.copy(layers = updatedLayers)
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = updatedFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendUndoRemoveStrokes(myId, frameIdx, op.layerIndex, listOf(op.stroke.id), isLocalLayer = op.isLocalLayer)
                }
            }
            is CanvasOp.RemoveStrokes -> {
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedFrame = if (op.isLocalLayer && f.localLayers.isNotEmpty()) {
                        val validL = op.layerIndex.coerceIn(0, f.localLayers.lastIndex)
                        val l = f.localLayers[validL]
                        val existingIds = l.strokes.map { it.id }.toSet()
                        val restored = op.removedStrokes.filter { it.id !in existingIds }
                        val updatedLayers = f.localLayers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = l.strokes + restored)
                        f.copy(localLayers = updatedLayers)
                    } else {
                        val validL = op.layerIndex.coerceIn(0, f.layers.lastIndex)
                        val l = f.layers[validL]
                        val existingIds = l.strokes.map { it.id }.toSet()
                        val restored = op.removedStrokes.filter { it.id !in existingIds }
                        val updatedLayers = f.layers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = l.strokes + restored)
                        f.copy(layers = updatedLayers)
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = updatedFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, op.layerIndex, op.removedStrokes, isLocalLayer = op.isLocalLayer)
                }
            }
            is CanvasOp.ReplaceStrokes -> {
                val newIds = op.newStrokes.map { it.id }.toSet()
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedFrame = if (op.isLocalLayer && f.localLayers.isNotEmpty()) {
                        val validL = op.layerIndex.coerceIn(0, f.localLayers.lastIndex)
                        val l = f.localLayers[validL]
                        val filtered = l.strokes.filter { it.id !in newIds }
                        val existingIds = filtered.map { it.id }.toSet()
                        val restoredOld = op.oldStrokes.filter { it.id !in existingIds }
                        val updatedLayers = f.localLayers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = filtered + restoredOld)
                        f.copy(localLayers = updatedLayers)
                    } else {
                        val validL = op.layerIndex.coerceIn(0, f.layers.lastIndex)
                        val l = f.layers[validL]
                        val filtered = l.strokes.filter { it.id !in newIds }
                        val existingIds = filtered.map { it.id }.toSet()
                        val restoredOld = op.oldStrokes.filter { it.id !in existingIds }
                        val updatedLayers = f.layers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = filtered + restoredOld)
                        f.copy(layers = updatedLayers)
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = updatedFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendUndoRemoveStrokes(myId, frameIdx, op.layerIndex, op.newStrokes.map { it.id }, isLocalLayer = op.isLocalLayer)
                    multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, op.layerIndex, op.oldStrokes, isLocalLayer = op.isLocalLayer)
                }
            }
            is CanvasOp.ClearFrame -> {
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedLayers = f.layers.mapIndexed { idx, layer ->
                        val prevLayer = op.previousLayers.getOrNull(idx)
                        if (prevLayer != null) {
                            val existingIds = layer.strokes.map { it.id }.toSet()
                            val missing = prevLayer.strokes.filter { it.id !in existingIds }
                            layer.copy(strokes = missing + layer.strokes)
                        } else layer
                    }
                    val updatedLocalLayers = f.localLayers.mapIndexed { idx, layer ->
                        val prevLayer = op.previousLocalLayers.getOrNull(idx)
                        if (prevLayer != null) {
                            val existingIds = layer.strokes.map { it.id }.toSet()
                            val missing = prevLayer.strokes.filter { it.id !in existingIds }
                            layer.copy(strokes = missing + layer.strokes)
                        } else layer
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = f.copy(layers = updatedLayers, localLayers = updatedLocalLayers)
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    op.previousLayers.forEachIndexed { idx, prevLayer ->
                        if (prevLayer.strokes.isNotEmpty()) {
                            multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, idx, prevLayer.strokes, isLocalLayer = false)
                        }
                    }
                    op.previousLocalLayers.forEachIndexed { idx, prevLayer ->
                        if (prevLayer.strokes.isNotEmpty()) {
                            multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, idx, prevLayer.strokes, isLocalLayer = true)
                        }
                    }
                }
            }
            is CanvasOp.FrameSnapshot -> {
                redoStack[redoStack.lastIndex] = CanvasOp.FrameSnapshot(currFrame.id, currFrame)
                _uiState.update { state ->
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = op.previousFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
            }
        }
        updateUndoRedoAvailability()
    }

    fun redo() {
        val stateBefore = _uiState.value
        val frameIdx = stateBefore.currentFrameIndex
        val currFrame = stateBefore.project.frames.getOrNull(frameIdx) ?: return
        val redoStack = perFrameRedoOps[currFrame.id] ?: return
        if (redoStack.isEmpty()) return

        val op = redoStack.removeAt(redoStack.lastIndex)
        val undoStack = perFrameUndoOps.getOrPut(currFrame.id) { mutableListOf() }
        undoStack.add(op)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }

        val myId = stateBefore.userAccount.id
        val isCoop = stateBefore.multiplayerRole != com.example.model.MultiplayerRole.NONE

        when (op) {
            is CanvasOp.AddStroke -> {
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedFrame = if (op.isLocalLayer && f.localLayers.isNotEmpty()) {
                        val validL = op.layerIndex.coerceIn(0, f.localLayers.lastIndex)
                        val l = f.localLayers[validL]
                        val updatedLayers = f.localLayers.toMutableList()
                        val nextStrokes = if (l.strokes.any { it.id == op.stroke.id }) l.strokes else l.strokes + op.stroke
                        updatedLayers[validL] = l.copy(strokes = nextStrokes)
                        f.copy(localLayers = updatedLayers)
                    } else {
                        val validL = op.layerIndex.coerceIn(0, f.layers.lastIndex)
                        val l = f.layers[validL]
                        val updatedLayers = f.layers.toMutableList()
                        val nextStrokes = if (l.strokes.any { it.id == op.stroke.id }) l.strokes else l.strokes + op.stroke
                        updatedLayers[validL] = l.copy(strokes = nextStrokes)
                        f.copy(layers = updatedLayers)
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = updatedFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, op.layerIndex, listOf(op.stroke), isLocalLayer = op.isLocalLayer)
                }
            }
            is CanvasOp.RemoveStrokes -> {
                val removeIds = op.removedStrokes.map { it.id }.toSet()
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedFrame = if (op.isLocalLayer && f.localLayers.isNotEmpty()) {
                        val validL = op.layerIndex.coerceIn(0, f.localLayers.lastIndex)
                        val l = f.localLayers[validL]
                        val updatedLayers = f.localLayers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = l.strokes.filter { it.id !in removeIds })
                        f.copy(localLayers = updatedLayers)
                    } else {
                        val validL = op.layerIndex.coerceIn(0, f.layers.lastIndex)
                        val l = f.layers[validL]
                        val updatedLayers = f.layers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = l.strokes.filter { it.id !in removeIds })
                        f.copy(layers = updatedLayers)
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = updatedFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendUndoRemoveStrokes(myId, frameIdx, op.layerIndex, removeIds.toList(), isLocalLayer = op.isLocalLayer)
                }
            }
            is CanvasOp.ReplaceStrokes -> {
                val oldIds = op.oldStrokes.map { it.id }.toSet()
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val updatedFrame = if (op.isLocalLayer && f.localLayers.isNotEmpty()) {
                        val validL = op.layerIndex.coerceIn(0, f.localLayers.lastIndex)
                        val l = f.localLayers[validL]
                        val filtered = l.strokes.filter { it.id !in oldIds }
                        val existingIds = filtered.map { it.id }.toSet()
                        val restoredNew = op.newStrokes.filter { it.id !in existingIds }
                        val updatedLayers = f.localLayers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = filtered + restoredNew)
                        f.copy(localLayers = updatedLayers)
                    } else {
                        val validL = op.layerIndex.coerceIn(0, f.layers.lastIndex)
                        val l = f.layers[validL]
                        val filtered = l.strokes.filter { it.id !in oldIds }
                        val existingIds = filtered.map { it.id }.toSet()
                        val restoredNew = op.newStrokes.filter { it.id !in existingIds }
                        val updatedLayers = f.layers.toMutableList()
                        updatedLayers[validL] = l.copy(strokes = filtered + restoredNew)
                        f.copy(layers = updatedLayers)
                    }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = updatedFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendUndoRemoveStrokes(myId, frameIdx, op.layerIndex, oldIds.toList(), isLocalLayer = op.isLocalLayer)
                    multiplayerEngine.sendRedoRestoreStrokes(myId, frameIdx, op.layerIndex, op.newStrokes, isLocalLayer = op.isLocalLayer)
                }
            }
            is CanvasOp.ClearFrame -> {
                _uiState.update { state ->
                    val f = state.project.frames[frameIdx]
                    val clearedLayers = f.layers.map { it.copy(strokes = emptyList()) }
                    val clearedLocalLayers = f.localLayers.map { it.copy(strokes = emptyList()) }
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = f.copy(layers = clearedLayers, localLayers = clearedLocalLayers)
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
                if (isCoop) {
                    multiplayerEngine.sendClearFrame(frameIdx)
                }
            }
            is CanvasOp.FrameSnapshot -> {
                undoStack[undoStack.lastIndex] = CanvasOp.FrameSnapshot(currFrame.id, currFrame)
                _uiState.update { state ->
                    val updatedFrames = state.project.frames.toMutableList()
                    updatedFrames[frameIdx] = op.previousFrame
                    val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
                    multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                    state.copy(project = updatedProj)
                }
            }
        }
        updateUndoRedoAvailability()
    }

    private fun updateUndoRedoAvailability() {
        _uiState.update { state ->
            val currFrameId = state.project.frames.getOrNull(state.currentFrameIndex)?.id
            val hasUndo = currFrameId != null && (perFrameUndoOps[currFrameId]?.isNotEmpty() == true)
            val hasRedo = currFrameId != null && (perFrameRedoOps[currFrameId]?.isNotEmpty() == true)
            state.copy(canUndo = hasUndo, canRedo = hasRedo)
        }
    }

    // --- Playback & Animation Loop ---

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        playbackJob?.cancel()
        MediaAssetManager.endAudioScrub()
        val initialState = _uiState.value
        val startMs = MediaAssetManager.computeTimeMsForFrame(
            initialState.project.frames,
            initialState.currentFrameIndex,
            initialState.project.fps
        ).toInt()
        val audioAsset = initialState.project.mediaAssets.firstOrNull { it.type == "audio" }
        if (audioAsset != null && audioAsset.uriString.isNotBlank()) {
            MediaAssetManager.playAudioTrack(
                filePath = audioAsset.uriString,
                startMs = startMs,
                isLooping = initialState.isLooping,
                volumePercent = audioAsset.audioVolumePercent,
                muted = audioAsset.audioMuted
            )
        }

        _uiState.update { it.copy(isPlaying = true, activeScrubTimeMs = null) }
        playbackJob = viewModelScope.launch {
            while (isActive && _uiState.value.isPlaying) {
                val curState = _uiState.value
                val frames = curState.project.frames
                val fps = max(1, curState.project.fps)
                val currIdx = curState.currentFrameIndex.coerceIn(0, max(0, frames.lastIndex))
                val holdCount = max(1, frames.getOrNull(currIdx)?.holdFrames ?: 1)
                val delayTime = ((1000L * holdCount) / fps).coerceAtLeast(16L)
                delay(delayTime)

                if (!isActive || !_uiState.value.isPlaying) break
                val latest = _uiState.value
                val total = latest.project.frames.size
                if (total <= 1) {
                    if (!latest.isLooping) {
                        pausePlayback()
                        break
                    }
                    continue
                }
                val isAtEnd = latest.currentFrameIndex >= total - 1
                if (isAtEnd && !latest.isLooping) {
                    pausePlayback()
                    break
                }
                val next = if (isAtEnd) 0 else latest.currentFrameIndex + 1
                if (isAtEnd && latest.isLooping) {
                    val loopAudio = latest.project.mediaAssets.firstOrNull { it.type == "audio" }
                    if (loopAudio != null && loopAudio.uriString.isNotBlank()) {
                        MediaAssetManager.seekAudio(0)
                    }
                }
                _uiState.update { s -> s.copy(currentFrameIndex = next) }
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        MediaAssetManager.pauseAudio()
        MediaAssetManager.endAudioScrub()
        _uiState.update { it.copy(isPlaying = false, activeScrubTimeMs = null) }
    }

    fun toggleLoop() {
        _uiState.update { it.copy(isLooping = !it.isLooping) }
    }

    fun setFps(fps: Int) {
        val clamped = fps.coerceIn(1, 120)
        _uiState.update { it.copy(project = it.project.copy(fps = clamped)) }
    }

    fun setResolution(res: ProjectResolution) {
        _uiState.update { it.copy(project = it.project.copy(resolution = res)) }
    }

    fun saveCustomResolution(name: String, width: Int, height: Int): ProjectResolution {
        val safeW = width.coerceIn(128, 4096)
        val safeH = height.coerceIn(128, 4096)
        val cleanName = name.trim().ifBlank { "Personalizada ${safeW}x${safeH}" }
        val customRes = ProjectResolution(
            width = safeW,
            height = safeH,
            name = cleanName,
            isCustom = true
        )
        _uiState.update { state ->
            val currentList = state.preferences.savedCustomResolutions
            val filtered = currentList.filterNot {
                it.width == customRes.width &&
                    it.height == customRes.height &&
                    it.name.equals(customRes.name, ignoreCase = true)
            }
            val updatedList = listOf(customRes) + filtered
            val updatedPrefs = state.preferences.copy(savedCustomResolutions = updatedList)
            appContext?.let { ctx -> PreferencesStorageManager.savePreferences(ctx, updatedPrefs) }
            state.copy(
                preferences = updatedPrefs,
                statusMessage = "Resolução '${customRes.name}' (${safeW}x${safeH}) salva!"
            )
        }
        return customRes
    }

    fun deleteCustomResolution(resolution: ProjectResolution) {
        _uiState.update { state ->
            val updatedList = state.preferences.savedCustomResolutions.filterNot {
                it.width == resolution.width &&
                    it.height == resolution.height &&
                    it.name == resolution.name
            }
            val updatedPrefs = state.preferences.copy(savedCustomResolutions = updatedList)
            appContext?.let { ctx -> PreferencesStorageManager.savePreferences(ctx, updatedPrefs) }
            state.copy(
                preferences = updatedPrefs,
                statusMessage = "Resolução '${resolution.name}' removida."
            )
        }
    }

    fun setProjectName(name: String) {
        val currentId = _uiState.value.project.id
        val unique = ensureUniqueProjectName(name.ifBlank { "Sem Título" }, excludeProjectId = currentId)
        _uiState.update { it.copy(project = it.project.copy(name = unique)) }
    }

    // --- Canvas Zoom & Pan ---

    fun updateTransform(zoomChange: Float, panChangeX: Float, panChangeY: Float, rotationChange: Float = 0f) {
        _uiState.update {
            val newZoom = (it.zoom * zoomChange).coerceIn(0.15f, 48.0f)
            it.copy(
                zoom = newZoom,
                panX = it.panX + panChangeX,
                panY = it.panY + panChangeY,
                rotation = (it.rotation + rotationChange) % 360f
            )
        }
    }

    fun resetCanvasTransform() {
        _uiState.update { it.copy(zoom = 1.0f, panX = 0f, panY = 0f, rotation = 0f) }
    }

    fun resetRotation() {
        _uiState.update { it.copy(rotation = 0f) }
    }

    // --- Reference Image (Timeline Layer vs Floating Window) ---

    fun setReferenceImageUri(uriString: String) {
        _uiState.update { it.copy(pendingReferenceImageUri = uriString) }
    }

    fun dismissReferenceImageChoice() {
        _uiState.update { it.copy(pendingReferenceImageUri = null) }
    }

    private fun persistPickedImageToInternalStorage(
        context: Context,
        uriString: String,
        folderName: String
    ): String {
        return try {
            val dir = java.io.File(context.filesDir, folderName).apply { if (!exists()) mkdirs() }
            val outFile = java.io.File(dir, "img_${UUID.randomUUID()}.png")
            val rawBytes: ByteArray? = when {
                uriString.startsWith("/") -> java.io.File(uriString).takeIf { it.exists() }?.readBytes()
                uriString.startsWith("file://") -> java.io.File(uriString.removePrefix("file://")).takeIf { it.exists() }?.readBytes()
                else -> context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
            }
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    var inSample = 1
                    val maxDim = 2048
                    while ((bounds.outWidth / inSample) > maxDim || (bounds.outHeight / inSample) > maxDim) {
                        inSample = inSample shl 1
                    }
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = inSample }
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                    if (bmp != null) {
                        java.io.FileOutputStream(outFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
                        }
                        bmp.recycle()
                        return outFile.absolutePath
                    }
                }
            }
            uriString
        } catch (_: Exception) {
            uriString
        }
    }

    fun applyPickedImageAsTimelineLayer(context: Context, uriString: String) {
        _uiState.update { it.copy(pendingReferenceImageUri = null) }
        val commitLayer: (String) -> Unit = { resolvedPath ->
            pushCurrentFrameToUndo()
            _uiState.update { state ->
                val currCount = state.project.frames.firstOrNull()?.layers?.size ?: 0
                val layerName = "Imagem ${currCount + 1}"
                val attachment = LayerImageAttachment(uriString = resolvedPath)
                val updatedFrames = state.project.frames.map { frame ->
                    frame.copy(
                        layers = frame.layers + Layer(
                            name = layerName,
                            imageAttachment = attachment
                        )
                    )
                }
                val currFrame = updatedFrames.getOrNull(state.currentFrameIndex)
                currFrame?.let { perFrameLocalTarget[it.id] = false to 0 }
                val updatedProj = state.project.copy(
                    frames = updatedFrames,
                    updatedAt = System.currentTimeMillis()
                )
                multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
                state.copy(
                    project = updatedProj,
                    activeLayerIndex = currCount,
                    activeTargetIsFrameLocalLayer = false,
                    statusMessage = "Imagem adicionada como camada da Timeline!"
                )
            }
            updateUndoRedoAvailability()
        }
        val isLocal = uriString.startsWith("/") || uriString.startsWith("file://")
        if (isLocal) {
            commitLayer(persistPickedImageToInternalStorage(context, uriString, "timeline_images"))
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val savedPath = persistPickedImageToInternalStorage(context, uriString, "timeline_images")
                withContext(Dispatchers.Main) {
                    commitLayer(savedPath)
                }
            }
        }
    }

    fun updateLayerImageTransform(
        layerIndex: Int,
        offsetXDelta: Float = 0f,
        offsetYDelta: Float = 0f,
        scalePercent: Float? = null,
        rotationDegrees: Float? = null
    ) {
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex !in frame.layers.indices) return@map frame
                val target = frame.layers[layerIndex]
                val att = target.imageAttachment ?: return@map frame
                val nextAtt = att.copy(
                    offsetX = (att.offsetX + offsetXDelta).coerceIn(-2f, 2f),
                    offsetY = (att.offsetY + offsetYDelta).coerceIn(-2f, 2f),
                    scalePercent = (scalePercent ?: att.normalizedScaleFactor()).coerceIn(0.1f, 5.0f),
                    rotationDegrees = (rotationDegrees ?: att.rotationDegrees).coerceIn(-360f, 360f)
                )
                val updatedLayers = frame.layers.toMutableList()
                updatedLayers[layerIndex] = target.copy(imageAttachment = nextAtt)
                frame.copy(layers = updatedLayers)
            }
            val updatedProj = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis())
            multiplayerEngine.updateHostedProjectSnapshot(updatedProj)
            state.copy(project = updatedProj)
        }
    }

    fun setLayerImageOpacity(layerIndex: Int, opacity: Float) {
        val clamped = opacity.coerceIn(0.05f, 1.0f)
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex !in frame.layers.indices) return@map frame
                val target = frame.layers[layerIndex]
                val att = target.imageAttachment ?: return@map frame
                val updatedLayers = frame.layers.toMutableList()
                updatedLayers[layerIndex] = target.copy(imageAttachment = att.copy(opacity = clamped))
                frame.copy(layers = updatedLayers)
            }
            state.copy(project = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis()))
        }
    }

    fun resetLayerImageTransform(layerIndex: Int) {
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex !in frame.layers.indices) return@map frame
                val target = frame.layers[layerIndex]
                val att = target.imageAttachment ?: return@map frame
                val updatedLayers = frame.layers.toMutableList()
                updatedLayers[layerIndex] = target.copy(
                    imageAttachment = att.copy(
                        scalePercent = 1.0f,
                        rotationDegrees = 0f,
                        offsetX = 0f,
                        offsetY = 0f,
                        opacity = 1.0f
                    )
                )
                frame.copy(layers = updatedLayers)
            }
            state.copy(project = state.project.copy(frames = updatedFrames, updatedAt = System.currentTimeMillis()))
        }
    }

    fun applyPickedImageAsFloatingWindow(context: Context, uriString: String) {
        _uiState.update { it.copy(pendingReferenceImageUri = null) }
        val commitFloating: (String) -> Unit = { resolvedPath ->
            _uiState.update {
                it.copy(
                    referenceImage = ReferenceImageState(
                        uriString = resolvedPath,
                        isVisible = true,
                        scale = 1.0f,
                        rotationDegrees = 0f,
                        offsetX = 0f,
                        offsetY = 0f,
                        opacity = 0.85f,
                        isLocked = false,
                        renderAboveCanvas = true
                    ),
                    statusMessage = "Janela de referência aberta em todos os quadros (não sai na exportação)."
                )
            }
        }
        val isLocal = uriString.startsWith("/") || uriString.startsWith("file://")
        if (isLocal) {
            commitFloating(persistPickedImageToInternalStorage(context, uriString, "reference_images"))
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val savedPath = persistPickedImageToInternalStorage(context, uriString, "reference_images")
                withContext(Dispatchers.Main) {
                    commitFloating(savedPath)
                }
            }
        }
    }

    fun updateFloatingReferenceOffset(dx: Float, dy: Float) {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible || ref.isLocked) return@update state
            state.copy(
                referenceImage = ref.copy(
                    offsetX = (ref.offsetX + dx).coerceIn(-1200f, 1200f),
                    offsetY = (ref.offsetY + dy).coerceIn(-1200f, 1200f)
                )
            )
        }
    }

    fun updateFloatingReferenceScale(newScale: Float) {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible || ref.isLocked) return@update state
            state.copy(referenceImage = ref.copy(scale = newScale.coerceIn(0.2f, 5.0f)))
        }
    }

    fun rotateFloatingReference(deltaDegrees: Float) {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible || ref.isLocked) return@update state
            state.copy(
                referenceImage = ref.copy(
                    rotationDegrees = ((ref.rotationDegrees + deltaDegrees) % 360f)
                )
            )
        }
    }

    fun setFloatingReferenceOpacity(opacity: Float) {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible) return@update state
            state.copy(referenceImage = ref.copy(opacity = opacity.coerceIn(0.08f, 1.0f)))
        }
    }

    fun toggleFloatingReferenceLock() {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible) return@update state
            state.copy(referenceImage = ref.copy(isLocked = !ref.isLocked))
        }
    }

    fun toggleFloatingReferenceZOrder() {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible) return@update state
            val nextAbove = !ref.renderAboveCanvas
            state.copy(
                referenceImage = ref.copy(renderAboveCanvas = nextAbove),
                statusMessage = if (nextAbove) "Janela flutuante acima do canvas" else "Janela flutuante atrás do canvas"
            )
        }
    }

    fun resetFloatingReferenceTransform() {
        _uiState.update { state ->
            val ref = state.referenceImage
            if (!ref.isVisible) return@update state
            state.copy(
                referenceImage = ref.copy(
                    scale = 1.0f,
                    rotationDegrees = 0f,
                    offsetX = 0f,
                    offsetY = 0f,
                    opacity = 0.85f
                )
            )
        }
    }

    fun clearReferenceImage() {
        _uiState.update { it.copy(referenceImage = ReferenceImageState(), pendingReferenceImageUri = null) }
    }

    // --- Import / Demo ---

    fun loadDemoProject() {
        pausePlayback()
        perFrameUndoOps.clear()
        perFrameRedoOps.clear()
        perFrameLocalTarget.clear()
        val uniqueName = ensureUniqueProjectName("Novo Projeto")
        _uiState.update {
            it.copy(
                project = SampleProjects.createEmptyProject(uniqueName, ProjectResolution.PRESETS[0], 12),
                currentFrameIndex = 0,
                activeLayerIndex = 1,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false,
                currentScreen = AppScreen.EDITOR,
                activeDialog = EditorDialog.NONE,
                bezierNodes = emptyList(),
                isBezierClosed = false,
                activeSelection = SelectionRegion(isActive = false)
            )
        }
        updateUndoRedoAvailability()
    }

    fun importProjectFromJson(jsonString: String) {
        try {
            val parsedRaw = FileImportExportManager.projectFromJson(jsonString)
            val uniqueName = ensureUniqueProjectName(parsedRaw.name, excludeProjectId = parsedRaw.id)
            val parsed = parsedRaw.copy(name = uniqueName)
            pausePlayback()
            perFrameUndoOps.clear()
            perFrameRedoOps.clear()
            perFrameLocalTarget.clear()
            _uiState.update {
                it.copy(
                    project = parsed,
                    currentFrameIndex = 0,
                    activeLayerIndex = 0,
                    currentScreen = AppScreen.EDITOR,
                    activeDialog = EditorDialog.NONE,
                    bezierNodes = emptyList(),
                    isBezierClosed = false,
                    activeSelection = SelectionRegion(isActive = false)
                )
            }
            updateUndoRedoAvailability()
        } catch (e: Exception) {
            _uiState.update { it.copy(exportState = ExportState.Error("Erro ao importar projeto: ${e.localizedMessage}")) }
        }
    }

    // --- Exporting ---

    fun exportToGif(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Progress("Renderizando quadros com motor Vulkan/OpenGL...", 0.2f)) }
            try {
                val proj = _uiState.value.project
                val w = min(640, proj.resolution.width)
                val h = (w * proj.resolution.height) / proj.resolution.width

                val gifBytes = withContext(KodanimaDispatchers.backgroundRender) {
                    val framePairs = ArrayList<Pair<Bitmap, Int>>(proj.frames.size)
                    try {
                        val total = max(1, proj.frames.size)
                        for (i in proj.frames.indices) {
                            val frame = proj.frames[i]
                            val bmp = KodanimaGraphicsEngine.renderFrameToBitmap(frame, w, h, android.graphics.Color.WHITE)
                            framePairs.add(bmp to max(1, frame.holdFrames))
                            _uiState.update {
                                it.copy(exportState = ExportState.Progress("Renderizando quadros: ${i + 1}/$total", 0.2f + 0.4f * (i / total.toFloat())))
                            }
                        }
                        _uiState.update { it.copy(exportState = ExportState.Progress("Codificando arquivo GIF...", 0.7f)) }
                        GifEncoder().encodeFramesWithHolds(framePairs, proj.fps)
                    } finally {
                        for ((b, _) in framePairs) {
                            KodanimaBitmapPool.release(b)
                        }
                    }
                }

                val filename = "${proj.name.replace(" ", "_")}_kodanima.gif"
                withContext(Dispatchers.IO) {
                    FileImportExportManager.shareFile(context, filename, "image/gif", gifBytes)
                }

                _uiState.update { it.copy(exportState = ExportState.Success("GIF exportado com sucesso!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha na exportação GIF: ${e.localizedMessage}")) }
            }
        }
    }

    fun exportToSpriteSheet(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Progress("Gerando Sprite Sheet...", 0.3f)) }
            try {
                val proj = _uiState.value.project
                val cellW = min(320, proj.resolution.width)
                val cellH = (cellW * proj.resolution.height) / proj.resolution.width

                val pngBytes = withContext(KodanimaDispatchers.backgroundRender) {
                    var sheetBmp: Bitmap? = null
                    try {
                        sheetBmp = KodanimaGraphicsEngine.generateSpriteSheet(proj.frames, cellW, cellH, columns = 4)
                        val out = ByteArrayOutputStream()
                        sheetBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    } finally {
                        if (sheetBmp != null && !sheetBmp.isRecycled) sheetBmp.recycle()
                    }
                }

                val filename = "${proj.name.replace(" ", "_")}_spritesheet.png"
                withContext(Dispatchers.IO) {
                    FileImportExportManager.shareFile(context, filename, "image/png", pngBytes)
                }

                _uiState.update { it.copy(exportState = ExportState.Success("Sprite Sheet exportada com sucesso!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha ao exportar Sprite Sheet: ${e.localizedMessage}")) }
            }
        }
    }

    fun exportCurrentFramePng(context: Context) {
        viewModelScope.launch {
            try {
                val proj = _uiState.value.project
                val frameIdx = _uiState.value.currentFrameIndex
                val curr = proj.frames[frameIdx]
                val pngBytes = withContext(KodanimaDispatchers.backgroundRender) {
                    var bmp: Bitmap? = null
                    try {
                        bmp = KodanimaGraphicsEngine.renderFrameToBitmap(
                            curr,
                            proj.resolution.width,
                            proj.resolution.height,
                            android.graphics.Color.WHITE
                        )
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    } finally {
                        KodanimaBitmapPool.release(bmp)
                    }
                }

                val filename = "${proj.name.replace(" ", "_")}_frame_${frameIdx + 1}.png"
                withContext(Dispatchers.IO) {
                    FileImportExportManager.shareFile(context, filename, "image/png", pngBytes)
                }

                _uiState.update { it.copy(exportState = ExportState.Success("Quadro atual exportado em PNG!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha ao exportar PNG: ${e.localizedMessage}")) }
            }
        }
    }

    fun exportToMp4(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Progress("Iniciando codificador nativo H.264...", 0.05f)) }
            try {
                val proj = _uiState.value.project
                val file = com.example.engine.AndroidVideoRenderer.renderToMp4(context, proj) { progress, message ->
                    _uiState.update { it.copy(exportState = ExportState.Progress(message, progress)) }
                }
                val filename = file.name
                withContext(Dispatchers.IO) {
                    val bytes = file.readBytes()
                    FileImportExportManager.shareFile(context, filename, "video/mp4", bytes)
                }
                _uiState.update { it.copy(exportState = ExportState.Success("Vídeo MP4 exportado com sucesso via aceleração de hardware!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha na exportação MP4: ${e.localizedMessage}")) }
            }
        }
    }

    fun exportFramePackZip(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Progress("Gerando pacote de quadros PNG...", 0.05f)) }
            try {
                val proj = _uiState.value.project
                val file = com.example.engine.AndroidVideoRenderer.exportFramePackZip(context, proj) { progress, message ->
                    _uiState.update { it.copy(exportState = ExportState.Progress(message, progress)) }
                }
                val filename = file.name
                withContext(Dispatchers.IO) {
                    val bytes = file.readBytes()
                    FileImportExportManager.shareFile(context, filename, "application/zip", bytes)
                }
                _uiState.update { it.copy(exportState = ExportState.Success("Pacote de quadros PNG exportado em ZIP!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha ao exportar pacote ZIP: ${e.localizedMessage}")) }
            }
        }
    }

    // --- Special Media Tracks (Audio & Video/GIF) ---

    fun addAudioTrack(name: String, uriString: String, durationSec: Double = 0.0) {
        val calculatedDuration = if (durationSec > 0.0) durationSec else (_uiState.value.project.frames.size.toDouble() / _uiState.value.project.fps)
        val asset = com.example.model.SpecialMediaAsset(
            name = name,
            uriString = uriString,
            type = "audio",
            startSeconds = 0.0,
            durationSeconds = calculatedDuration,
            audioVolumePercent = 100,
            waveformPeaks = listOf(0.15f, 0.45f, 0.8f, 0.95f, 0.6f, 0.85f, 0.5f, 0.75f, 0.9f, 0.35f, 0.7f, 0.5f)
        )
        _uiState.update {
            it.copy(project = it.project.copy(mediaAssets = it.project.mediaAssets + asset))
        }
    }

    fun addVideoTrack(name: String, uriString: String, durationSec: Double = 0.0) {
        val calculatedDuration = if (durationSec > 0.0) durationSec else (_uiState.value.project.frames.size.toDouble() / _uiState.value.project.fps)
        val asset = com.example.model.SpecialMediaAsset(
            name = name,
            uriString = uriString,
            type = "video",
            startSeconds = 0.0,
            durationSeconds = calculatedDuration,
            opacityPercent = 50,
            scalePercent = 100.0
        )
        _uiState.update {
            it.copy(project = it.project.copy(mediaAssets = it.project.mediaAssets + asset))
        }
    }

    fun toggleAudioMute(assetId: String) {
        _uiState.update { state ->
            var targetAsset: com.example.model.SpecialMediaAsset? = null
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) {
                    val toggled = it.copy(audioMuted = !it.audioMuted)
                    targetAsset = toggled
                    toggled
                } else it
            }
            targetAsset?.let { MediaAssetManager.updateAudioVolume(it.audioVolumePercent, it.audioMuted) }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun setAudioVolume(assetId: String, volume: Int) {
        _uiState.update { state ->
            var targetAsset: com.example.model.SpecialMediaAsset? = null
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) {
                    val changed = it.copy(audioVolumePercent = volume.coerceIn(0, 100))
                    targetAsset = changed
                    changed
                } else it
            }
            targetAsset?.let { MediaAssetManager.updateAudioVolume(it.audioVolumePercent, it.audioMuted) }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun setVideoOpacity(assetId: String, opacity: Int) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) it.copy(opacityPercent = opacity.coerceIn(0, 100)) else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun toggleVideoTransformMode() {
        _uiState.update { state ->
            val hasVideo = state.project.mediaAssets.any { it.type == "video" || it.type == "gif" }
            if (!hasVideo && !state.isVideoTransformMode) {
                state.copy(statusMessage = "Importe um vídeo/GIF primeiro para transformar no canvas.")
            } else {
                state.copy(isVideoTransformMode = !state.isVideoTransformMode)
            }
        }
    }

    fun setVideoTransformMode(enabled: Boolean) {
        _uiState.update { it.copy(isVideoTransformMode = enabled) }
    }

    fun moveVideoAsset(assetId: String, dx: Double, dy: Double) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy) else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun scaleVideoAsset(assetId: String, factor: Double) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) it.copy(scalePercent = (it.scalePercent * factor).coerceIn(10.0, 500.0)) else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun rotateVideoAsset(assetId: String, degreesDelta: Double) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) it.copy(rotationDegrees = (it.rotationDegrees + degreesDelta) % 360.0) else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun updateVideoAssetTransform(assetId: String, dx: Double, dy: Double, zoomFactor: Double, degreesDelta: Double) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) {
                    it.copy(
                        offsetX = it.offsetX + dx,
                        offsetY = it.offsetY + dy,
                        scalePercent = (it.scalePercent * zoomFactor).coerceIn(10.0, 500.0),
                        rotationDegrees = (it.rotationDegrees + degreesDelta) % 360.0
                    )
                } else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun resetVideoTransform(assetId: String) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) {
                    it.copy(scalePercent = 100.0, rotationDegrees = 0.0, offsetX = 0.0, offsetY = 0.0)
                } else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun selectLayer(index: Int) = setActiveLayer(index)
    fun deleteLayer(index: Int) = deleteActiveLayer()

    fun moveLayerUp(layerIndex: Int) {
        val currLayers = _uiState.value.project.frames.firstOrNull()?.layers ?: emptyList()
        if (layerIndex <= 0 || layerIndex >= currLayers.size) return
        pushCurrentFrameToUndo()
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex in frame.layers.indices) {
                    val list = frame.layers.toMutableList()
                    val item = list.removeAt(layerIndex)
                    list.add(layerIndex - 1, item)
                    frame.copy(layers = list)
                } else frame
            }
            state.copy(
                project = state.project.copy(frames = updatedFrames),
                activeLayerIndex = layerIndex - 1
            )
        }
    }

    fun moveLayerDown(layerIndex: Int) {
        val currLayers = _uiState.value.project.frames.firstOrNull()?.layers ?: emptyList()
        if (layerIndex < 0 || layerIndex >= currLayers.size - 1) return
        pushCurrentFrameToUndo()
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { frame ->
                if (layerIndex in frame.layers.indices) {
                    val list = frame.layers.toMutableList()
                    val item = list.removeAt(layerIndex)
                    list.add(layerIndex + 1, item)
                    frame.copy(layers = list)
                } else frame
            }
            state.copy(
                project = state.project.copy(frames = updatedFrames),
                activeLayerIndex = layerIndex + 1
            )
        }
    }

    fun selectFrame(index: Int) = goToFrame(index)
    fun addNewFrame() = addFrame()

    fun insertFrameAtCurrentPosition() {
        pushCurrentFrameToUndo()
        _uiState.update { state ->
            saveCurrentFrameTargetPreference(state)
            val currFrame = state.project.frames[state.currentFrameIndex]
            val newFrame = Frame(layers = currFrame.layers.map { it.copy(id = UUID.randomUUID().toString(), strokes = emptyList()) })
            val updated = state.project.frames.toMutableList()
            val insertIdx = (state.currentFrameIndex + 1).coerceAtMost(updated.size)
            updated.add(insertIdx, newFrame)
            state.copy(
                project = state.project.copy(frames = updated, updatedAt = System.currentTimeMillis()),
                currentFrameIndex = insertIdx,
                activeLocalLayerIndex = 0,
                activeTargetIsFrameLocalLayer = false
            )
        }
        updateUndoRedoAvailability()
    }

    fun setProjectFps(fps: Int) {
        _uiState.update { it.copy(project = it.project.copy(fps = fps.coerceIn(1, 120))) }
    }

    fun setProjectResolution(resolution: ProjectResolution) {
        _uiState.update { it.copy(project = it.project.copy(resolution = resolution)) }
    }

    fun setOnionSkinOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.05f, 1.0f)
        _uiState.update {
            it.copy(project = it.project.copy(onionSkin = it.project.onionSkin.copy(opacity = clamped)))
        }
    }

    fun setOnionSkinSettings(settings: OnionSkinSettings) {
        _uiState.update {
            it.copy(project = it.project.copy(onionSkin = settings))
        }
    }

    fun importAudioFile(context: Context, uri: Uri) {
        _uiState.update { it.copy(statusMessage = "Processando forma de onda do áudio...") }
        viewModelScope.launch {
            val bundle = withContext(Dispatchers.IO) {
                MediaAssetManager.importMediaBundle(context, uri, "audio")
            }
            val asset = bundle?.primaryAsset
            if (asset != null) {
                MediaAssetManager.stopAudio()
                _uiState.update { state ->
                    val filtered = state.project.mediaAssets.filter { it.type != "audio" }
                    state.copy(
                        project = state.project.copy(mediaAssets = filtered + asset, updatedAt = System.currentTimeMillis()),
                        statusMessage = "Áudio '${asset.name}' importado (${asset.waveformPeaks.size} picos)!"
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Não foi possível importar o arquivo de áudio.") }
            }
        }
    }

    fun importVideoFile(context: Context, uri: Uri) {
        _uiState.update { it.copy(statusMessage = "Importando vídeo e extraindo áudio...") }
        viewModelScope.launch {
            val bundle = withContext(Dispatchers.IO) {
                MediaAssetManager.importMediaBundle(context, uri, "video")
            }
            val visualAsset = bundle?.primaryAsset
            if (visualAsset != null) {
                MediaAssetManager.releaseVideoDecoder()
                if (bundle.extractedAudioAsset != null) {
                    MediaAssetManager.stopAudio()
                }
                _uiState.update { state ->
                    var nextAssets = state.project.mediaAssets.filter { it.type != "video" && it.type != "gif" }
                    nextAssets = nextAssets + visualAsset
                    val extractedAudio = bundle.extractedAudioAsset
                    if (extractedAudio != null) {
                        nextAssets = nextAssets.filter { it.type != "audio" } + extractedAudio
                    }
                    state.copy(
                        project = state.project.copy(mediaAssets = nextAssets, updatedAt = System.currentTimeMillis()),
                        statusMessage = if (extractedAudio != null) {
                            "Vídeo '${visualAsset.name}' + áudio separado adicionados!"
                        } else {
                            "Vídeo '${visualAsset.name}' adicionado ao canvas!"
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Não foi possível importar o arquivo de vídeo.") }
            }
        }
    }

    fun importAudioUri(uriString: String) {
        val asset = com.example.model.SpecialMediaAsset(
            name = "Áudio da Timeline",
            uriString = uriString,
            type = "audio"
        )
        _uiState.update {
            val filtered = it.project.mediaAssets.filter { a -> a.type != "audio" }
            it.copy(project = it.project.copy(mediaAssets = filtered + asset))
        }
    }

    fun setAllFramesHoldDuration(holdCount: Int) {
        _uiState.update { state ->
            val updatedFrames = state.project.frames.map { it.copy(holdFrames = holdCount.coerceIn(1, 120)) }
            state.copy(project = state.project.copy(frames = updatedFrames))
        }
    }

    fun setVirtualCursorEnabled(enabled: Boolean) {
        updatePreferences { it.copy(tabletSettings = it.tabletSettings.copy(enableVirtualCursor = enabled)) }
    }

    fun setCustomCursorPath(path: String?) {
        updatePreferences { it.copy(tabletSettings = it.tabletSettings.copy(customCursorPath = path)) }
        viewModelScope.launch(Dispatchers.IO) {
            val cursorB64 = CursorHelper.encodeCursorFileToBase64(path)
            val updatedAcc = _uiState.value.userAccount.copy(customCursorBase64 = cursorB64)
            appContext?.let { PreferencesStorageManager.saveUserAccount(it, updatedAcc) }
            multiplayerEngine.updateLocalUser(updatedAcc)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(userAccount = updatedAcc) }
            }
            multiplayerEngine.sendCursorIconUpdate(updatedAcc)
        }
    }

    fun setInvertX(invert: Boolean) {
        updatePreferences { it.copy(tabletSettings = it.tabletSettings.copy(invertX = invert)) }
    }

    fun setInvertY(invert: Boolean) {
        updatePreferences { it.copy(tabletSettings = it.tabletSettings.copy(invertY = invert)) }
    }

    fun setPressureMode(mode: PressureMode, customLevels: Int = 8192) {
        updatePreferences {
            it.copy(
                tabletSettings = it.tabletSettings.copy(
                    pressureMode = mode,
                    customPressureLevels = customLevels
                )
            )
        }
    }

    fun setPressureCurve(curve: PressureCurve) {
        updatePreferences { it.copy(tabletSettings = it.tabletSettings.copy(pressureCurve = curve)) }
    }

    fun exportSingleFrame(context: Context, frameIndex: Int) {
        viewModelScope.launch {
            try {
                val proj = _uiState.value.project
                val clamped = frameIndex.coerceIn(0, proj.frames.size - 1)
                val curr = proj.frames[clamped]
                val pngBytes = withContext(KodanimaDispatchers.backgroundRender) {
                    var bmp: Bitmap? = null
                    try {
                        bmp = KodanimaGraphicsEngine.renderFrameToBitmap(
                            curr,
                            proj.resolution.width,
                            proj.resolution.height,
                            android.graphics.Color.WHITE
                        )
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    } finally {
                        KodanimaBitmapPool.release(bmp)
                    }
                }

                val filename = "${proj.name.replace(" ", "_")}_quadro_${clamped + 1}.png"
                withContext(Dispatchers.IO) {
                    FileImportExportManager.shareFile(context, filename, "image/png", pngBytes)
                }

                _uiState.update { it.copy(exportState = ExportState.Success("Quadro ${clamped + 1} exportado em PNG!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha ao exportar quadro: ${e.localizedMessage}")) }
            }
        }
    }

    fun exportProjectToJson(context: Context) = exportProjectFile(context)

    fun updateSpecialAssetScale(assetId: String, scalePercent: Double) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) it.copy(scalePercent = scalePercent) else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun updateSpecialAssetOpacity(assetId: String, opacity: Double) {
        _uiState.update { state ->
            val updated = state.project.mediaAssets.map {
                if (it.id == assetId) it.copy(opacityPercent = (opacity * 100).toInt()) else it
            }
            state.copy(project = state.project.copy(mediaAssets = updated))
        }
    }

    fun resetSpecialAssetTransform(assetId: String) {
        resetVideoTransform(assetId)
    }

    fun deleteMediaAsset(assetId: String) {
        val asset = _uiState.value.project.mediaAssets.firstOrNull { it.id == assetId }
        if (asset != null) {
            MediaAssetManager.deleteMediaFile(asset.uriString)
        }
        _uiState.update { state ->
            val updated = state.project.mediaAssets.filter { it.id != assetId }
            val stillHasVideo = updated.any { it.type == "video" || it.type == "gif" }
            state.copy(
                project = state.project.copy(mediaAssets = updated),
                isVideoTransformMode = if (!stillHasVideo) false else state.isVideoTransformMode
            )
        }
    }

    fun exportProjectFile(context: Context) {
        viewModelScope.launch {
            try {
                val proj = _uiState.value.project
                val filename = "${proj.name.replace(" ", "_")}.kodanima"
                withContext(Dispatchers.IO) {
                    val jsonString = FileImportExportManager.projectToJson(proj)
                    FileImportExportManager.shareFile(
                        context,
                        filename,
                        "application/json",
                        jsonString.toByteArray(Charsets.UTF_8)
                    )
                }

                _uiState.update { it.copy(exportState = ExportState.Success("Projeto Kodanima exportado!", filename)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportState = ExportState.Error("Falha ao exportar projeto: ${e.localizedMessage}")) }
            }
        }
    }

    fun dismissExportState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }
}
