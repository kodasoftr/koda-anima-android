package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.model.AnimationProject
import com.example.model.BrushPreset
import com.example.model.ConnectedFriendInfo
import com.example.model.DrawingStroke
import com.example.model.FriendEntry
import com.example.model.FriendRequest
import com.example.model.FriendStatus
import com.example.model.JoinKnockRequest
import com.example.model.Layer
import com.example.model.RemoteLiveStroke
import com.example.model.RemotePeerCursor
import com.example.model.RemoteRoomInfo
import com.example.model.RoomInviteNotification
import com.example.model.StrokePoint
import com.example.model.ToolType
import com.example.model.UserAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * High-performance local Wi-Fi/Hotspot collaborative engine using native Android networking APIs:
 * - `android.net.nsd.NsdManager` (`_kodanima._tcp.`) mDNS service advertisement & discovery
 * - `android.net.wifi.WifiManager.MulticastLock` + `WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY`)
 * - `android.net.ConnectivityManager` subnet-directed broadcast resolution across Wi-Fi & Hotspot
 * - Dual-channel architecture: Reliable TCP Control channel for Friend Requests, Room Invites &
 *   Avatar thumbnail sync + Dedicated low-latency TCP (`TCP_NODELAY`) Canvas Collaboration stream.
 */
class MultiplayerStudioEngine(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MultiplayerEngine"
        const val DEFAULT_TCP_PORT = 8765
        const val DISCOVERY_UDP_PORT = 8766
        const val DEFAULT_CONTROL_PORT = 8767
        const val NSD_SERVICE_TYPE = "_kodanima._tcp."
        private const val BEACON_MAGIC = "KODANIMA_ROOM_BEACON_V2"

        /**
         * Pure helper for generating candidate /24 host IPs for dual-band (2.4GHz <-> 5GHz) LAN discovery.
         */
        fun buildSubnetCandidateIps(localIp: String): List<String> {
            val parts = localIp.split(".")
            if (parts.size != 4) return emptyList()
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            val selfOctet = parts[3].toIntOrNull() ?: -1
            return (1..254).mapNotNull { i ->
                if (i == selfOctet) null else "$prefix.$i"
            }
        }

        /**
         * Pure helper for strict self-room filtering so the user never sees their own
         * hosted room inside "Salas Locais".
         */
        fun isOwnRoom(
            roomHostId: String,
            roomId: String,
            roomHostIp: String,
            localUserId: String,
            localRoomId: String?,
            localIps: Set<String>
        ): Boolean {
            if (localUserId.isNotBlank() && roomHostId.equals(localUserId, ignoreCase = true)) return true
            if (!localRoomId.isNullOrBlank() && roomId.equals(localRoomId, ignoreCase = true)) return true
            if (roomHostIp.isNotBlank() && roomHostIp in localIps) return true
            return false
        }
    }

    // Callbacks
    var onKnockReceived: ((JoinKnockRequest) -> Unit)? = null
    var onDiscoveredRoomsUpdated: ((List<RemoteRoomInfo>) -> Unit)? = null
    var onStrokeReceived: ((frameIndex: Int, layerIndex: Int, stroke: DrawingStroke, isLocalLayer: Boolean) -> Unit)? = null
    var onClearFrameReceived: ((frameIndex: Int) -> Unit)? = null
    var onAddFrameReceived: ((insertAt: Int) -> Unit)? = null
    var onAddLayerReceived: ((layerName: String) -> Unit)? = null
    var onFrameLocalLayersReceived: ((frameIndex: Int, localLayers: List<Layer>) -> Unit)? = null
    var onRemoteCursorUpdated: ((RemotePeerCursor) -> Unit)? = null
    var onProjectSynced: ((AnimationProject) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onFriendConnected: ((ConnectedFriendInfo) -> Unit)? = null
    var onConnectedPeersUpdated: ((List<ConnectedFriendInfo>) -> Unit)? = null
    var onHostShutdown: ((message: String) -> Unit)? = null
    var onPingUpdated: ((Int) -> Unit)? = null
    var onFriendRequestReceived: ((FriendRequest, targetUserId: String, targetName: String) -> Unit)? = null
    var onFriendAccepted: ((FriendEntry, targetUserId: String) -> Unit)? = null
    var onPeerInfoUpdated: ((FriendEntry) -> Unit)? = null
    var onRoomInviteReceived: ((RoomInviteNotification, targetUserId: String, targetName: String) -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null
    var onPeerCountChanged: ((Int) -> Unit)? = null
    var onPeerLeft: ((peerId: String) -> Unit)? = null
    var onRemoteLiveStrokeUpdated: ((peerId: String, frameIndex: Int, layerIndex: Int, stroke: DrawingStroke?, isLocalLayer: Boolean) -> Unit)? = null
    var onRemoteUndoRedoOpReceived: ((opType: String, authorId: String, frameIndex: Int, layerIndex: Int, strokeIds: List<String>, strokes: List<DrawingStroke>, isLocalLayer: Boolean) -> Unit)? = null
    var onPeerCursorIconUpdated: ((peerId: String, customCursorBase64: String?) -> Unit)? = null
    var onBrushDefinitionReceived: ((senderId: String, senderName: String, preset: BrushPreset) -> Unit)? = null
    var onMoveLayerReceived: ((fromIndex: Int, toIndex: Int) -> Unit)? = null

    private data class PendingClient(
        val reader: BufferedReader,
        val writer: BufferedWriter,
        val user: UserAccount,
        val clientIp: String,
        val closeHandle: () -> Unit
    )

    private data class KnownPeer(
        val userId: String,
        val userName: String,
        val ip: String,
        val controlPort: Int,
        val avatarBase64: String? = null,
        val avatarColor: Long = 0xFF00E5FF,
        val lastSeenMs: Long = System.currentTimeMillis()
    )

    // Android native networking handles
    private var appContext: Context? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegListener: NsdManager.RegistrationListener? = null
    private var nsdDiscListener: NsdManager.DiscoveryListener? = null
    private var registeredNsdServiceName: String? = null

    // Local identity & state
    @Volatile
    private var localUser: UserAccount = UserAccount()
    @Volatile
    private var controlPort: Int = 0

    // Sockets & jobs
    private var controlServerSocket: ServerSocket? = null
    private var controlServerJob: Job? = null
    private var presenceBeaconJob: Job? = null
    private var subnetScanJob: Job? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientCloseHandle: (() -> Unit)? = null
    private var clientWriter: BufferedWriter? = null
    private val hostClientWriters = ConcurrentHashMap<String, BufferedWriter>()
    private val hostClientCloseHandles = ConcurrentHashMap<String, () -> Unit>()
    private val hostConnectedPeers = ConcurrentHashMap<String, ConnectedFriendInfo>()
    private val pendingKnocks = ConcurrentHashMap<String, PendingClient>()
    @Volatile
    private var currentHostSelfInfo: ConnectedFriendInfo? = null

    private var serverJob: Job? = null
    private var broadcastBeaconJob: Job? = null
    private var discoveryJob: Job? = null
    private var clientListenerJob: Job? = null
    private var hostPingJob: Job? = null

    private val discoveredRoomsMap = ConcurrentHashMap<String, RemoteRoomInfo>()
    private val knownPeers = ConcurrentHashMap<String, KnownPeer>()
    private val seenEventIds = ConcurrentHashMap<String, Long>()
    private val lastPeerPreviewSeq = ConcurrentHashMap<String, Long>()
    private val committedStrokeIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val outgoingPreviewSeq = AtomicLong(0L)
    private val outboundQueueDepth = AtomicInteger(0)
    private val sessionSendChannel = Channel<Pair<String, String>>(capacity = 512)
    private var sessionSenderJob: Job? = null
    @Volatile
    private var activeRoomInfo: RemoteRoomInfo? = null
    @Volatile
    private var latestHostProject: AnimationProject? = null

    fun bindContext(context: Context) {
        appContext = context.applicationContext
    }

    fun updateLocalUser(user: UserAccount) {
        localUser = user
        currentHostSelfInfo = currentHostSelfInfo?.copy(
            name = user.name,
            avatarUri = user.avatarUri,
            avatarBase64 = user.avatarBase64,
            avatarColor = user.avatarColor,
            customCursorBase64 = user.customCursorBase64
        )
    }

    fun updateHostedProjectSnapshot(project: AnimationProject) {
        latestHostProject = project
    }

    /**
     * Collects all active non-loopback IPv4 addresses on Wi-Fi / Hotspot interfaces.
     */
    fun getAllLocalIpv4Addresses(): Set<String> {
        val result = linkedSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                for (ia in intf.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error listing local IPv4s: ${e.message}")
        }
        return result
    }

    /**
     * Resolves the preferred local IPv4 address (prioritizing wlan/ap interfaces).
     */
    fun getLocalIpAddress(): String {
        try {
            var fallback: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                val isWifiOrHotspot = name.contains("wlan") || name.contains("ap") || name.contains("swlan")
                for (ia in intf.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isWifiOrHotspot) return host
                        if (fallback == null) fallback = host
                    }
                }
            }
            if (fallback != null) return fallback
        } catch (e: Exception) {
            Log.w(TAG, "Error finding IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    /**
     * Collects subnet-directed broadcast addresses (via NetworkInterface + ConnectivityManager)
     * plus limited broadcast 255.255.255.255 so Tecno/MediaTek & Hotspot networks receive packets reliably.
     */
    private fun getBroadcastTargets(): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                for (ia in intf.interfaceAddresses) {
                    val bcast = ia.broadcast
                    if (bcast is Inet4Address) {
                        targets.add(bcast)
                    }
                }
            }
        } catch (_: Exception) {}

        // Also inspect active ConnectivityManager LinkProperties
        try {
            val ctx = appContext
            if (ctx != null) {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val active = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(active)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val lp = cm.getLinkProperties(active)
                    lp?.linkAddresses?.forEach { linkAddr ->
                        val addr = linkAddr.address
                        if (addr is Inet4Address && linkAddr.prefixLength in 1..30) {
                            val raw = addr.address
                            val ipInt = ((raw[0].toInt() and 0xFF) shl 24) or
                                ((raw[1].toInt() and 0xFF) shl 16) or
                                ((raw[2].toInt() and 0xFF) shl 8) or
                                (raw[3].toInt() and 0xFF)
                            val mask = (-1 shl (32 - linkAddr.prefixLength))
                            val bcastInt = ipInt or mask.inv()
                            val bcastBytes = byteArrayOf(
                                ((bcastInt ushr 24) and 0xFF).toByte(),
                                ((bcastInt ushr 16) and 0xFF).toByte(),
                                ((bcastInt ushr 8) and 0xFF).toByte(),
                                (bcastInt and 0xFF).toByte()
                            )
                            targets.add(InetAddress.getByAddress(bcastBytes))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {}
        return targets.toList()
    }

    private fun acquireWifiLocks(highPerf: Boolean) {
        val ctx = appContext ?: return
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            if (multicastLock == null) {
                multicastLock = wm.createMulticastLock("kodanima_mdns_lock").apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
            if (highPerf) {
                if (wifiLock == null) {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wm.createWifiLock(mode, "kodanima_coop_wifi_lock").apply {
                        setReferenceCounted(false)
                    }
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wifi lock acquire warning: ${e.message}")
        }
    }

    private fun releaseWifiLocks(releaseHighPerfOnly: Boolean = false) {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        if (!releaseHighPerfOnly) {
            try {
                if (multicastLock?.isHeld == true) multicastLock?.release()
            } catch (_: Exception) {}
        }
    }

    // --- Always-on Control Server + NsdManager + Discovery ---

    /**
     * Starts NSD service advertisement, NSD discovery, TCP control server, and multi-subnet UDP discovery.
     * Safe to call repeatedly; ensures Tecno BG6 & Moto G stay reachable for Friend Requests & Invites.
     */
    fun startDiscovery(user: UserAccount? = null, forceSubnetScan: Boolean = true) {
        if (user != null) localUser = user
        acquireWifiLocks(highPerf = activeRoomInfo != null)

        startControlServerIfNeeded()
        startNsdDiscoveryAndRegistration()
        startUdpDiscoveryListenerIfNeeded()
        startPresenceBeaconIfNeeded()
        if (forceSubnetScan) {
            triggerActiveSubnetPeerScan()
        }
    }

    /**
     * Actively probes /24 LAN peers on deterministic TCP control port 8767/8768
     * so devices split across 2.4 GHz & 5 GHz bands (where routers often filter UDP/mDNS)
     * still pair and discover rooms reliably!
     */
    fun triggerActiveSubnetPeerScan() {
        if (subnetScanJob?.isActive == true) return
        subnetScanJob = scope.launch(KodanimaDispatchers.networkIo) {
            val localIps = getAllLocalIpv4Addresses()
            val candidateIps = localIps.flatMap { buildSubnetCandidateIps(it) }.distinct()
            if (candidateIps.isEmpty()) return@launch

            candidateIps.chunked(32).forEach { batch ->
                if (!isActive) return@launch
                batch.map { targetIp ->
                    launch {
                        if (probeTcpPortFast(targetIp, DEFAULT_CONTROL_PORT, 320)) {
                            exchangePeerHelloOverTcp(targetIp, DEFAULT_CONTROL_PORT)
                        } else if (probeTcpPortFast(targetIp, DEFAULT_CONTROL_PORT + 1, 240)) {
                            exchangePeerHelloOverTcp(targetIp, DEFAULT_CONTROL_PORT + 1)
                        }
                    }
                }.forEach { it.join() }
            }
        }
    }

    private fun probeTcpPortFast(ip: String, port: Int, timeoutMs: Int): Boolean {
        var s: Socket? = null
        return try {
            s = Socket()
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { s?.close() } catch (_: Exception) {}
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        presenceBeaconJob?.cancel()
        presenceBeaconJob = null
        subnetScanJob?.cancel()
        subnetScanJob = null
        stopNsd()
        try { controlServerSocket?.close() } catch (_: Exception) {}
        controlServerSocket = null
        controlServerJob?.cancel()
        controlServerJob = null
        controlPort = 0
        if (activeRoomInfo == null && clientSocket == null && clientWriter == null) {
            releaseWifiLocks(releaseHighPerfOnly = false)
        }
    }

    private fun bindDeterministicControlServer(): ServerSocket {
        val candidatePorts = listOf(DEFAULT_CONTROL_PORT, DEFAULT_CONTROL_PORT + 1, DEFAULT_CONTROL_PORT + 2, 0)
        for (p in candidatePorts) {
            try {
                return ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(p))
                }
            } catch (_: Exception) {}
        }
        return ServerSocket(0)
    }

    private fun tuneLowLatencySocket(sock: Socket) {
        try {
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.sendBufferSize = 65536
            sock.receiveBufferSize = 65536
            sock.trafficClass = 0x10 // IPTOS_LOWDELAY hardware Wi-Fi/socket QoS hint
        } catch (_: Exception) {}
    }

    private fun startControlServerIfNeeded() {
        if (controlServerJob?.isActive == true && controlServerSocket != null && !controlServerSocket!!.isClosed) {
            return
        }
        controlServerJob = scope.launch(KodanimaDispatchers.networkIo) {
            try {
                val srv = bindDeterministicControlServer()
                controlServerSocket = srv
                controlPort = srv.localPort
                refreshNsdRegistration()
                while (isActive && !srv.isClosed) {
                    val sock = srv.accept() ?: break
                    tuneLowLatencySocket(sock)
                    launch(KodanimaDispatchers.networkIo) {
                        handleControlPacketSocket(sock)
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "Control server stopped: ${e.message}")
            }
        }
    }

    private suspend fun handleControlPacketSocket(sock: Socket) {
        var reader: BufferedReader? = null
        var writer: BufferedWriter? = null
        try {
            reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8), 16384)
            writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), 16384)
            val line = reader.readLine() ?: return
            val json = JSONObject(line)
            val remoteIp = sock.inetAddress?.hostAddress ?: ""
            processControlOrDiscoveryJson(json, remoteIp)

            // Respond with our own fresh presence + avatar so both phones immediately cache photos!
            val u = localUser
            val room = activeRoomInfo
            val ack = JSONObject().apply {
                put("magic", "KODANIMA_PEER_HELLO")
                put("userId", u.id)
                put("userName", u.name)
                put("avatarBase64", u.avatarBase64 ?: "")
                put("avatarColor", u.avatarColor)
                put("controlPort", controlPort)
                if (room != null) {
                    put("roomId", room.roomId)
                    put("roomName", room.roomName)
                    put("hostPort", room.hostPort)
                    put("frameCount", room.frameCount)
                    put("fps", room.fps)
                    put("peerCount", hostClientWriters.size + 1)
                }
            }.toString()
            writeLineThreadSafe(writer, ack)
        } catch (_: Exception) {
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { writer?.close() } catch (_: Exception) {}
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private fun startUdpDiscoveryListenerIfNeeded() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch(KodanimaDispatchers.networkIo) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                    soTimeout = 3500
                }
                val buffer = ByteArray(16384)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        packet.length = buffer.size
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        val senderIp = packet.address?.hostAddress ?: ""
                        processControlOrDiscoveryJson(json, senderIp)
                    } catch (_: java.net.SocketTimeoutException) {
                        purgeStaleRooms()
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "UDP discovery error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Discovery UDP socket bind warning: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun startPresenceBeaconIfNeeded() {
        if (presenceBeaconJob?.isActive == true) return
        presenceBeaconJob = scope.launch(KodanimaDispatchers.networkIo) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket().apply { broadcast = true }
                while (isActive) {
                    val u = localUser
                    if (controlPort > 0) {
                        val payload = JSONObject().apply {
                            put("magic", "KODANIMA_PEER_PRESENCE")
                            put("userId", u.id)
                            put("userName", u.name)
                            put("avatarColor", u.avatarColor)
                            put("controlPort", controlPort)
                        }.toString().toByteArray(Charsets.UTF_8)
                        sendUdpToAllTargets(udpSocket, payload)
                    }
                    purgeStaleRooms()
                    delay(2500)
                }
            } catch (_: Exception) {
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun purgeStaleRooms() {
        val now = System.currentTimeMillis()
        var changed = false
        val iter = discoveredRoomsMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value.lastSeenMs > 8000L) {
                iter.remove()
                changed = true
            }
        }
        if (changed) {
            withContext(Dispatchers.Main) {
                onDiscoveredRoomsUpdated?.invoke(discoveredRoomsMap.values.toList())
            }
        }
    }

    private fun startNsdDiscoveryAndRegistration() {
        val ctx = appContext ?: return
        try {
            if (nsdManager == null) {
                nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
            }
            val nm = nsdManager ?: return

            // Register own device control service if not already registered
            if (nsdRegListener == null && controlPort > 0) {
                val u = localUser
                val info = NsdServiceInfo().apply {
                    serviceName = "Kodanima-${u.id}"
                    serviceType = NSD_SERVICE_TYPE
                    port = controlPort
                    setAttribute("uid", u.id)
                    setAttribute("uname", u.name.take(30))
                    setAttribute("cport", controlPort.toString())
                    activeRoomInfo?.let { r ->
                        setAttribute("rid", r.roomId)
                        setAttribute("rname", r.roomName.take(30))
                        setAttribute("rport", r.hostPort.toString())
                    }
                }
                val regListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                        registeredNsdServiceName = serviceInfo.serviceName
                    }
                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "NSD reg failed: $errorCode")
                    }
                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                }
                nsdRegListener = regListener
                nm.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener)
            }

            if (nsdDiscListener == null) {
                val discListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {}
                    override fun onServiceFound(service: NsdServiceInfo) {
                        if (!service.serviceType.contains("_kodanima")) return
                        if (service.serviceName == registeredNsdServiceName) return
                        try {
                            @Suppress("DEPRECATION")
                            nm.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val hostAddr = resolved.host?.hostAddress ?: return
                                    val resolvedPort = resolved.port
                                    val attrs = resolved.attributes
                                    val uid = attrs["uid"]?.let { String(it, Charsets.UTF_8) } ?: ""
                                    val localIps = getAllLocalIpv4Addresses()
                                    if (uid.equals(localUser.id, ignoreCase = true) || hostAddr in localIps) return

                                    // Handshake via TCP Control Socket to pull rich avatar + active room metadata!
                                    scope.launch(KodanimaDispatchers.networkIo) {
                                        exchangePeerHelloOverTcp(hostAddr, resolvedPort)
                                    }
                                }
                            })
                        } catch (_: Exception) {}
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        try { nm.stopServiceDiscovery(this) } catch (_: Exception) {}
                        nsdDiscListener = null
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        nsdDiscListener = null
                    }
                }
                nsdDiscListener = discListener
                nm.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "NSD start warning: ${e.message}")
        }
    }

    private fun refreshNsdRegistration() {
        val nm = nsdManager ?: return
        try {
            nsdRegListener?.let { nm.unregisterService(it) }
        } catch (_: Exception) {}
        nsdRegListener = null
        registeredNsdServiceName = null
        startNsdDiscoveryAndRegistration()
    }

    private fun stopNsd() {
        val nm = nsdManager ?: return
        try { nsdRegListener?.let { nm.unregisterService(it) } } catch (_: Exception) {}
        try { nsdDiscListener?.let { nm.stopServiceDiscovery(it) } } catch (_: Exception) {}
        nsdRegListener = null
        nsdDiscListener = null
        registeredNsdServiceName = null
    }

    suspend fun exchangePeerHelloOverTcp(ip: String, port: Int) {
        if (port <= 0) return
        var sock: Socket? = null
        var writer: BufferedWriter? = null
        var reader: BufferedReader? = null
        try {
            sock = Socket()
            tuneLowLatencySocket(sock)
            sock.connect(InetSocketAddress(ip, port), 2500)
            writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), 16384)
            reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8), 16384)

            val u = localUser
            val room = activeRoomInfo
            val hello = JSONObject().apply {
                put("magic", "KODANIMA_PEER_HELLO")
                put("userId", u.id)
                put("userName", u.name)
                put("avatarBase64", u.avatarBase64 ?: "")
                put("avatarColor", u.avatarColor)
                put("controlPort", controlPort)
                if (room != null) {
                    put("roomId", room.roomId)
                    put("roomName", room.roomName)
                    put("hostPort", room.hostPort)
                    put("frameCount", room.frameCount)
                    put("fps", room.fps)
                    put("peerCount", hostClientWriters.size + 1)
                }
            }.toString()
            writeLineThreadSafe(writer, hello)

            val respLine = reader.readLine()
            if (!respLine.isNullOrBlank()) {
                processControlOrDiscoveryJson(JSONObject(respLine), ip)
            }
        } catch (_: Exception) {
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { writer?.close() } catch (_: Exception) {}
            try { sock?.close() } catch (_: Exception) {}
        }
    }

    private fun isDuplicateEvent(eventId: String): Boolean {
        if (eventId.isBlank()) return false
        val now = System.currentTimeMillis()
        val prev = seenEventIds.putIfAbsent(eventId, now)
        if (seenEventIds.size > 128) {
            seenEventIds.entries.removeIf { now - it.value > 20000L }
        }
        return prev != null && (now - prev) < 15000L
    }

    private suspend fun processControlOrDiscoveryJson(json: JSONObject, sourceIp: String) {
        val magic = json.optString("magic")
        val localIps = getAllLocalIpv4Addresses()
        val myId = localUser.id

        when (magic) {
            "KODANIMA_PEER_PRESENCE", "KODANIMA_PEER_HELLO" -> {
                val peerId = json.optString("userId", "")
                val peerName = json.optString("userName", "")
                val peerCtrlPort = json.optInt("controlPort", 0)
                val peerAvatarB64 = json.optString("avatarBase64", "").ifEmpty { null }
                val peerColor = json.optLong("avatarColor", 0xFF00E5FF)
                val resolvedIp = sourceIp.ifBlank { json.optString("ip", "") }

                if (peerId.isBlank() || peerId.equals(myId, ignoreCase = true) || resolvedIp in localIps) {
                    return
                }

                val existing = knownPeers[peerId]
                val mergedAvatar = peerAvatarB64 ?: existing?.avatarBase64
                knownPeers[peerId] = KnownPeer(
                    userId = peerId,
                    userName = peerName,
                    ip = resolvedIp,
                    controlPort = if (peerCtrlPort > 0) peerCtrlPort else (existing?.controlPort ?: 0),
                    avatarBase64 = mergedAvatar,
                    avatarColor = peerColor
                )

                // If we only saw a lightweight UDP presence packet and don't have their avatar yet, pull via TCP!
                if (magic == "KODANIMA_PEER_PRESENCE" && peerCtrlPort > 0 && mergedAvatar == null && existing == null) {
                    scope.launch(KodanimaDispatchers.networkIo) {
                        exchangePeerHelloOverTcp(resolvedIp, peerCtrlPort)
                    }
                }

                if (peerAvatarB64 != null) {
                    val savedUri = appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, peerId, peerAvatarB64) }
                    val updatedFriend = FriendEntry(
                        id = peerId,
                        name = peerName.ifBlank { peerId },
                        avatarUri = savedUri,
                        avatarBase64 = peerAvatarB64,
                        avatarColor = peerColor,
                        status = if (json.has("roomId")) FriendStatus.IN_ROOM else FriendStatus.ONLINE,
                        currentRoomId = json.optString("roomId", "").ifEmpty { null },
                        lastKnownIp = resolvedIp,
                        lastKnownControlPort = peerCtrlPort
                    )
                    withContext(Dispatchers.Main) {
                        onPeerInfoUpdated?.invoke(updatedFriend)
                    }
                }

                // If peer hello also announces a room, surface it cleanly
                if (json.has("roomId") && json.optInt("hostPort", 0) > 0) {
                    val rId = json.getString("roomId")
                    if (!isOwnRoom(peerId, rId, resolvedIp, myId, activeRoomInfo?.roomId, localIps)) {
                        val room = RemoteRoomInfo(
                            roomId = rId,
                            roomName = json.optString("roomName", "Projeto"),
                            hostId = peerId,
                            hostName = peerName,
                            hostAvatarBase64 = mergedAvatar,
                            hostAvatarColor = peerColor,
                            hostIp = resolvedIp,
                            hostPort = json.getInt("hostPort"),
                            frameCount = json.optInt("frameCount", 1),
                            fps = json.optInt("fps", 12),
                            connectedPeerCount = json.optInt("peerCount", 1),
                            lastSeenMs = System.currentTimeMillis()
                        )
                        discoveredRoomsMap[rId] = room
                        withContext(Dispatchers.Main) {
                            onDiscoveredRoomsUpdated?.invoke(discoveredRoomsMap.values.toList())
                        }
                    }
                }
            }

            BEACON_MAGIC, "KODANIMA_ROOM_BEACON_V1" -> {
                val hostId = json.optString("hostId", "")
                val rId = json.optString("roomId", "")
                val hostIp = sourceIp.ifBlank { json.optString("hostIp", "") }
                if (isOwnRoom(hostId, rId, hostIp, myId, activeRoomInfo?.roomId, localIps)) {
                    return
                }

                val peerCtrlPort = json.optInt("controlPort", 0)
                val cachedPeer = knownPeers[hostId]
                val hostAvatarB64 = json.optString("hostAvatarBase64", "").ifEmpty { null } ?: cachedPeer?.avatarBase64

                if (cachedPeer == null && hostId.isNotBlank() && peerCtrlPort > 0) {
                    scope.launch(KodanimaDispatchers.networkIo) {
                        exchangePeerHelloOverTcp(hostIp, peerCtrlPort)
                    }
                }

                val savedUri = if (hostAvatarB64 != null) {
                    appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, hostId, hostAvatarB64) }
                } else null

                val room = RemoteRoomInfo(
                    roomId = rId,
                    roomName = json.optString("roomName", "Projeto"),
                    hostId = hostId,
                    hostName = json.optString("hostName", "Amigo"),
                    hostAvatarUri = savedUri,
                    hostAvatarBase64 = hostAvatarB64,
                    hostAvatarColor = json.optLong("hostAvatarColor", 0xFF00E5FF),
                    hostIp = hostIp,
                    hostPort = json.optInt("hostPort", DEFAULT_TCP_PORT),
                    frameCount = json.optInt("frameCount", 1),
                    fps = json.optInt("fps", 12),
                    connectedPeerCount = json.optInt("peerCount", 1),
                    lastSeenMs = System.currentTimeMillis()
                )
                discoveredRoomsMap[rId] = room
                withContext(Dispatchers.Main) {
                    onDiscoveredRoomsUpdated?.invoke(discoveredRoomsMap.values.toList())
                }
            }

            "KODANIMA_FRIEND_REQUEST" -> {
                val senderId = json.optString("senderId", "")
                if (senderId.isBlank() || senderId.equals(myId, ignoreCase = true) || sourceIp in localIps) return
                val eventId = json.optString("id", "")
                if (isDuplicateEvent("FR-$eventId")) return

                val senderName = json.optString("senderName", "Animador")
                val senderCtrlPort = json.optInt("senderControlPort", 0)
                val senderAvatarB64 = json.optString("senderAvatarBase64", "").ifEmpty { null }
                    ?: knownPeers[senderId]?.avatarBase64
                val savedUri = if (senderAvatarB64 != null) {
                    appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, senderId, senderAvatarB64) }
                } else null

                knownPeers[senderId] = KnownPeer(
                    userId = senderId,
                    userName = senderName,
                    ip = sourceIp,
                    controlPort = senderCtrlPort,
                    avatarBase64 = senderAvatarB64,
                    avatarColor = json.optLong("senderAvatarColor", 0xFFBE699A)
                )

                val req = FriendRequest(
                    id = eventId.ifBlank { UUID.randomUUID().toString() },
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatarUri = savedUri,
                    senderAvatarBase64 = senderAvatarB64,
                    senderAvatarColor = json.optLong("senderAvatarColor", 0xFFBE699A),
                    senderIp = sourceIp.ifBlank { null },
                    senderControlPort = senderCtrlPort,
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                val targetUserId = json.optString("targetUserId", "")
                val targetName = json.optString("targetName", "")
                withContext(Dispatchers.Main) {
                    onFriendRequestReceived?.invoke(req, targetUserId, targetName)
                }
            }

            "KODANIMA_FRIEND_ACCEPT" -> {
                val senderId = json.optString("senderId", "")
                if (senderId.isBlank() || senderId.equals(myId, ignoreCase = true) || sourceIp in localIps) return
                val eventId = json.optString("eventId", "FA-$senderId-${json.optLong("timestamp", 0L)}")
                if (isDuplicateEvent(eventId)) return

                val senderName = json.optString("senderName", "Amigo")
                val senderAvatarB64 = json.optString("senderAvatarBase64", "").ifEmpty { null }
                    ?: knownPeers[senderId]?.avatarBase64
                val savedUri = if (senderAvatarB64 != null) {
                    appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, senderId, senderAvatarB64) }
                } else null
                val senderCtrlPort = json.optInt("senderControlPort", 0)

                val friend = FriendEntry(
                    id = senderId,
                    name = senderName,
                    avatarUri = savedUri,
                    avatarBase64 = senderAvatarB64,
                    avatarColor = json.optLong("senderAvatarColor", 0xFF00E5FF),
                    status = FriendStatus.ONLINE,
                    lastKnownIp = sourceIp.ifBlank { null },
                    lastKnownControlPort = senderCtrlPort
                )
                val targetUserId = json.optString("targetUserId", "")
                withContext(Dispatchers.Main) {
                    onFriendAccepted?.invoke(friend, targetUserId)
                }
            }

            "KODANIMA_ROOM_INVITE" -> {
                val hostId = json.optString("hostId", "")
                if (hostId.isBlank() || hostId.equals(myId, ignoreCase = true) || sourceIp in localIps) return
                val inviteId = json.optString("inviteId", "")
                if (isDuplicateEvent("INV-$inviteId")) return

                val hostName = json.optString("hostName", "Amigo")
                val hostIp = sourceIp.ifBlank { json.optString("hostIp", "") }
                val inviterAvatarB64 = json.optString("hostAvatarBase64", "").ifEmpty { null }
                    ?: knownPeers[hostId]?.avatarBase64
                val savedUri = if (inviterAvatarB64 != null) {
                    appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, hostId, inviterAvatarB64) }
                } else null

                val roomInfo = RemoteRoomInfo(
                    roomId = json.getString("roomId"),
                    roomName = json.getString("roomName"),
                    hostId = hostId,
                    hostName = hostName,
                    hostAvatarUri = savedUri,
                    hostAvatarBase64 = inviterAvatarB64,
                    hostAvatarColor = json.optLong("hostAvatarColor", 0xFF00E5FF),
                    hostIp = hostIp,
                    hostPort = json.optInt("hostPort", DEFAULT_TCP_PORT),
                    frameCount = json.optInt("frameCount", 1),
                    fps = json.optInt("fps", 12),
                    connectedPeerCount = 1
                )
                val targetUserId = json.optString("targetUserId", "")
                val targetName = json.optString("targetName", "")
                val invite = RoomInviteNotification(
                    id = inviteId.ifBlank { UUID.randomUUID().toString() },
                    roomInfo = roomInfo,
                    inviterAvatarBase64 = inviterAvatarB64
                )
                withContext(Dispatchers.Main) {
                    onRoomInviteReceived?.invoke(invite, targetUserId, targetName)
                }
            }
        }
    }

    // --- Sending Control Packets (Dual: Reliable TCP + Multi-Subnet UDP) ---

    private fun sendUdpToAllTargets(socket: DatagramSocket, bytes: ByteArray) {
        for (addr in getBroadcastTargets()) {
            try {
                val packet = DatagramPacket(bytes, bytes.size, addr, DISCOVERY_UDP_PORT)
                socket.send(packet)
            } catch (_: Exception) {}
        }
    }

    private fun sendControlPayloadOverTcpAndUdp(
        payloadJson: String,
        targetUserId: String = "",
        targetIp: String? = null,
        targetControlPort: Int = 0
    ) {
        scope.launch(KodanimaDispatchers.networkIo) {
            // 1. Direct reliable TCP to specific target or all discovered LAN peers
            val tcpTargets = linkedSetOf<Pair<String, Int>>()
            if (!targetIp.isNullOrBlank() && targetControlPort > 0) {
                tcpTargets.add(targetIp to targetControlPort)
            }
            knownPeers.values.forEach { peer ->
                if (peer.controlPort > 0 && peer.ip.isNotBlank()) {
                    if (targetUserId.isBlank() || peer.userId.equals(targetUserId, ignoreCase = true) || targetIp.isNullOrBlank()) {
                        tcpTargets.add(peer.ip to peer.controlPort)
                    }
                }
            }
            tcpTargets.forEach { (ip, port) ->
                launch {
                    sendSingleTcpLine(ip, port, payloadJson)
                }
            }

            // 2. Multi-subnet UDP broadcast fallback (skip huge payloads >12KB cleanly, keep normal avatar <8KB)
            val bytes = payloadJson.toByteArray(Charsets.UTF_8)
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket().apply { broadcast = true }
                repeat(2) {
                    sendUdpToAllTargets(udpSocket, bytes)
                    delay(120)
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP control send error: ${e.message}")
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun sendSingleTcpLine(ip: String, port: Int, line: String) {
        var sock: Socket? = null
        var writer: BufferedWriter? = null
        try {
            sock = Socket()
            tuneLowLatencySocket(sock)
            sock.connect(InetSocketAddress(ip, port), 2500)
            writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), 16384)
            writeLineThreadSafe(writer, line)
        } catch (_: Exception) {
        } finally {
            try { writer?.close() } catch (_: Exception) {}
            try { sock?.close() } catch (_: Exception) {}
        }
    }

    fun broadcastFriendRequest(
        sender: UserAccount,
        targetUserId: String,
        targetName: String
    ) {
        localUser = sender
        val payload = JSONObject().apply {
            put("magic", "KODANIMA_FRIEND_REQUEST")
            put("id", UUID.randomUUID().toString())
            put("senderId", sender.id)
            put("senderName", sender.name)
            put("senderAvatarBase64", sender.avatarBase64 ?: "")
            put("senderAvatarColor", sender.avatarColor)
            put("senderControlPort", controlPort)
            put("targetUserId", targetUserId)
            put("targetName", targetName)
            put("timestamp", System.currentTimeMillis())
        }.toString()
        sendControlPayloadOverTcpAndUdp(payload, targetUserId = targetUserId)
    }

    fun broadcastFriendAccept(
        acceptor: UserAccount,
        targetUserId: String,
        targetIp: String? = null,
        targetControlPort: Int = 0
    ) {
        localUser = acceptor
        val payload = JSONObject().apply {
            put("magic", "KODANIMA_FRIEND_ACCEPT")
            put("eventId", UUID.randomUUID().toString())
            put("senderId", acceptor.id)
            put("senderName", acceptor.name)
            put("senderAvatarBase64", acceptor.avatarBase64 ?: "")
            put("senderAvatarColor", acceptor.avatarColor)
            put("senderControlPort", controlPort)
            put("targetUserId", targetUserId)
            put("timestamp", System.currentTimeMillis())
        }.toString()
        sendControlPayloadOverTcpAndUdp(
            payloadJson = payload,
            targetUserId = targetUserId,
            targetIp = targetIp,
            targetControlPort = targetControlPort
        )
    }

    fun sendRoomInvite(
        hostUser: UserAccount,
        roomInfo: RemoteRoomInfo,
        targetFriend: FriendEntry
    ) {
        localUser = hostUser
        val payload = JSONObject().apply {
            put("magic", "KODANIMA_ROOM_INVITE")
            put("inviteId", UUID.randomUUID().toString())
            put("roomId", roomInfo.roomId)
            put("roomName", roomInfo.roomName)
            put("hostId", hostUser.id)
            put("hostName", hostUser.name)
            put("hostAvatarBase64", hostUser.avatarBase64 ?: "")
            put("hostAvatarColor", hostUser.avatarColor)
            put("hostIp", roomInfo.hostIp)
            put("hostPort", roomInfo.hostPort)
            put("frameCount", roomInfo.frameCount)
            put("fps", roomInfo.fps)
            put("targetUserId", targetFriend.id)
            put("targetName", targetFriend.name)
        }.toString()
        sendControlPayloadOverTcpAndUdp(
            payloadJson = payload,
            targetUserId = targetFriend.id,
            targetIp = targetFriend.lastKnownIp,
            targetControlPort = targetFriend.lastKnownControlPort
        )
    }

    // --- Hosting Local TCP Room Server ---

    fun startHosting(
        user: UserAccount,
        project: AnimationProject,
        port: Int = DEFAULT_TCP_PORT
    ): RemoteRoomInfo {
        stopSessionOnly()
        localUser = user
        latestHostProject = project
        acquireWifiLocks(highPerf = true)
        startDiscovery(user)

        val localIp = getLocalIpAddress()
        val roomId = "KD-" + user.id.takeLast(4) + "-" + (1000..9999).random()
        val roomInfo = RemoteRoomInfo(
            roomId = roomId,
            roomName = project.name,
            hostId = user.id,
            hostName = user.name,
            hostAvatarUri = user.avatarUri,
            hostAvatarBase64 = user.avatarBase64,
            hostAvatarColor = user.avatarColor,
            hostIp = localIp,
            hostPort = port,
            frameCount = project.frames.size,
            fps = project.fps,
            connectedPeerCount = 1
        )
        activeRoomInfo = roomInfo
        currentHostSelfInfo = ConnectedFriendInfo(
            id = user.id,
            name = user.name,
            avatarUri = user.avatarUri,
            avatarBase64 = user.avatarBase64,
            avatarColor = user.avatarColor,
            customCursorBase64 = user.customCursorBase64,
            isHost = true,
            pingMs = 0
        )
        refreshNsdRegistration()

        // Start Low-Latency TCP Server
        serverJob = scope.launch(KodanimaDispatchers.networkIo) {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                serverSocket = ss
                Log.d(TAG, "Coop TCP server started on port $port")
                while (isActive && !ss.isClosed) {
                    val socket = ss.accept() ?: break
                    tuneLowLatencySocket(socket)
                    handleIncomingConnection(socket, roomId)
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Server socket error: ${e.message}")
            }
        }

        // Start Host Heartbeat Ping every 2s so Host ALSO displays live RTT ping to connected peers!
        hostPingJob = scope.launch(KodanimaDispatchers.networkIo) {
            while (isActive) {
                delay(2000)
                if (hostClientWriters.isNotEmpty()) {
                    val ping = JSONObject().apply {
                        put("type", "PING")
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                    hostClientWriters.values.forEach { w ->
                        try { writeLineThreadSafe(w, ping) } catch (_: Exception) {}
                    }
                }
            }
        }

        // Start UDP Room Beacon Broadcaster across all subnets
        broadcastBeaconJob = scope.launch(KodanimaDispatchers.networkIo) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket().apply { broadcast = true }
                while (isActive) {
                    val currProj = latestHostProject ?: project
                    val beaconPayload = JSONObject().apply {
                        put("magic", BEACON_MAGIC)
                        put("roomId", roomInfo.roomId)
                        put("roomName", currProj.name)
                        put("hostId", localUser.id)
                        put("hostName", localUser.name)
                        put("hostAvatarColor", localUser.avatarColor)
                        put("hostIp", localIp)
                        put("hostPort", port)
                        put("controlPort", controlPort)
                        put("frameCount", currProj.frames.size)
                        put("fps", currProj.fps)
                        put("peerCount", hostClientWriters.size + 1)
                    }.toString()

                    sendUdpToAllTargets(udpSocket, beaconPayload.toByteArray(Charsets.UTF_8))
                    delay(1400)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Room beacon broadcaster terminated: ${e.message}")
            } finally {
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }

        return roomInfo
    }

    fun broadcastAccountPresence(user: UserAccount) {
        localUser = user
        if (controlPort <= 0) return
        val payload = JSONObject().apply {
            put("magic", "KODANIMA_PEER_HELLO")
            put("userId", user.id)
            put("userName", user.name)
            put("avatarBase64", user.avatarBase64 ?: "")
            put("avatarColor", user.avatarColor)
            put("controlPort", controlPort)
        }.toString()
        sendControlPayloadOverTcpAndUdp(payload)
    }

    private fun handleIncomingConnection(
        socket: Socket,
        roomId: String
    ) {
        val clientIp = socket.inetAddress?.hostAddress ?: "desconhecido"
        val clientPort = socket.port
        attachExternalHostClientStream(
            inputStream = socket.getInputStream(),
            outputStream = socket.getOutputStream(),
            remoteLabel = clientIp,
            remotePort = clientPort,
            roomId = roomId
        ) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Accepts any incoming host connection stream (TCP Socket OR Bluetooth RFCOMM BluetoothSocket).
     */
    fun attachExternalHostClientStream(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        remoteLabel: String,
        remotePort: Int = 0,
        roomId: String = activeRoomInfo?.roomId ?: "KD-ROOM",
        closeHandle: () -> Unit
    ) {
        scope.launch(KodanimaDispatchers.networkIo) {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 16384)
                val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), 16384)

                val firstLine = reader.readLine() ?: run {
                    closeHandle()
                    return@launch
                }

                val packet = JSONObject(firstLine)
                if (packet.optString("type") == "KNOCK") {
                    val uid = packet.optString("userId", UUID.randomUUID().toString())
                    val uName = packet.optString("userName", remoteLabel)
                    val avatarB64 = packet.optString("avatarBase64", "").ifEmpty { null }
                    val customCursorB64 = packet.optString("customCursorBase64", "").ifEmpty { null }
                    val savedUri = if (avatarB64 != null) {
                        appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, uid, avatarB64) }
                    } else null

                    val clientUser = UserAccount(
                        id = uid,
                        name = uName,
                        avatarUri = savedUri,
                        avatarBase64 = avatarB64,
                        avatarColor = packet.optLong("avatarColor", 0xFF987EDC),
                        customCursorBase64 = customCursorB64
                    )
                    val connId = packet.optString("connId", UUID.randomUUID().toString())

                    // Remove any stale pending knocks from the same user so double-taps never leave ghosts
                    pendingKnocks.entries.removeIf { (oldId, oldPending) ->
                        if (oldPending.user.id.equals(uid, ignoreCase = true) && oldId != connId) {
                            try { oldPending.closeHandle() } catch (_: Exception) {}
                            true
                        } else false
                    }

                    val knockReq = JoinKnockRequest(
                        connectionId = connId,
                        clientIp = remoteLabel,
                        clientPort = remotePort,
                        user = clientUser,
                        roomId = roomId
                    )

                    pendingKnocks[connId] = PendingClient(reader, writer, clientUser, remoteLabel, closeHandle)

                    withContext(Dispatchers.Main) {
                        onKnockReceived?.invoke(knockReq)
                    }
                } else {
                    closeHandle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming client: ${e.message}")
                closeHandle()
            }
        }
    }

    fun answerKnock(
        connId: String,
        allow: Boolean,
        currentProject: AnimationProject,
        hostUser: UserAccount
    ) {
        val client = pendingKnocks.remove(connId) ?: return
        scope.launch(KodanimaDispatchers.networkIo) {
            try {
                val writer = client.writer
                val reader = client.reader

                if (allow) {
                    val projectJson = FileImportExportManager.projectToJson(currentProject, compact = true)
                    val resp = JSONObject().apply {
                        put("type", "KNOCK_RESPONSE")
                        put("accepted", true)
                        put("hostId", hostUser.id)
                        put("hostName", hostUser.name)
                        put("hostAvatarBase64", hostUser.avatarBase64 ?: "")
                        put("hostAvatarColor", hostUser.avatarColor)
                        put("hostCustomCursorBase64", hostUser.customCursorBase64 ?: "")
                        put("projectJson", projectJson)
                    }.toString()
                    writeLineThreadSafe(writer, resp)

                    hostClientWriters[connId] = writer
                    hostClientCloseHandles[connId] = client.closeHandle
                    val connectedGuest = ConnectedFriendInfo(
                        id = client.user.id,
                        name = client.user.name,
                        avatarUri = client.user.avatarUri,
                        avatarBase64 = client.user.avatarBase64,
                        avatarColor = client.user.avatarColor,
                        customCursorBase64 = client.user.customCursorBase64,
                        isHost = false,
                        pingMs = 12
                    )
                    hostConnectedPeers[connId] = connectedGuest
                    broadcastHostPeersList()
                    withContext(Dispatchers.Main) {
                        onPeerCountChanged?.invoke(hostClientWriters.size + 1)
                        onFriendConnected?.invoke(connectedGuest)
                        onConnectedPeersUpdated?.invoke(hostConnectedPeers.values.toList())
                    }

                    readServerClientStream(connId, reader, writer, client.closeHandle)
                } else {
                    val resp = JSONObject().apply {
                        put("type", "KNOCK_RESPONSE")
                        put("accepted", false)
                        put("reason", "O anfitrião não permitiu a entrada na sala.")
                    }.toString()
                    writeLineThreadSafe(writer, resp)
                    delay(150)
                    client.closeHandle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error answering knock: ${e.message}")
                try { client.closeHandle() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun readServerClientStream(
        connId: String,
        reader: BufferedReader,
        writer: BufferedWriter,
        closeHandle: () -> Unit
    ) {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                val packet = JSONObject(line)
                when (packet.optString("type")) {
                    "STROKE" -> {
                        val frameIndex = packet.getInt("frameIndex")
                        val layerIndex = packet.getInt("layerIndex")
                        val isLocalLayer = packet.optBoolean("isLocalLayer", false)
                        val stroke = parseStrokeFromJson(packet.getJSONObject("stroke"))
                        committedStrokeIds.add(stroke.id)
                        if (committedStrokeIds.size > 4096) committedStrokeIds.clear()
                        withContext(Dispatchers.Main) {
                            onStrokeReceived?.invoke(frameIndex, layerIndex, stroke, isLocalLayer)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "CLEAR_FRAME" -> {
                        val frameIndex = packet.getInt("frameIndex")
                        withContext(Dispatchers.Main) {
                            onClearFrameReceived?.invoke(frameIndex)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "ADD_FRAME" -> {
                        val insertAt = packet.getInt("insertAt")
                        withContext(Dispatchers.Main) {
                            onAddFrameReceived?.invoke(insertAt)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "ADD_LAYER" -> {
                        val layerName = packet.optString("layerName", "Camada")
                        withContext(Dispatchers.Main) {
                            onAddLayerReceived?.invoke(layerName)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "MOVE_LAYER" -> {
                        val fromIndex = packet.optInt("fromIndex", -1)
                        val toIndex = packet.optInt("toIndex", -1)
                        if (fromIndex >= 0 && toIndex >= 0) {
                            withContext(Dispatchers.Main) {
                                onMoveLayerReceived?.invoke(fromIndex, toIndex)
                            }
                            broadcastToOtherPeers(connId, line)
                        }
                    }
                    "SYNC_FRAME_LOCAL_LAYERS" -> {
                        val frameIndex = packet.optInt("frameIndex", 0)
                        val arr = packet.optJSONArray("localLayers")
                        val list = if (arr != null) {
                            (0 until arr.length()).mapNotNull { i ->
                                arr.optJSONObject(i)?.let {
                                    FileImportExportManager.jsonToLayer(it, i, "Subcamada")
                                }
                            }
                        } else emptyList()
                        withContext(Dispatchers.Main) {
                            onFrameLocalLayersReceived?.invoke(frameIndex, list)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "CURSOR" -> {
                        val cursor = RemotePeerCursor(
                            peerId = packet.getString("peerId"),
                            peerName = packet.getString("peerName"),
                            color = packet.getLong("color"),
                            frameIndex = packet.getInt("frameIndex"),
                            x = packet.getDouble("x").toFloat(),
                            y = packet.getDouble("y").toFloat(),
                            isDrawing = packet.optBoolean("isDrawing", false)
                        )
                        withContext(Dispatchers.Main) {
                            onRemoteCursorUpdated?.invoke(cursor)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "STROKE_PREVIEW" -> {
                        val peerId = packet.optString("peerId", "")
                        val frameIndex = packet.optInt("frameIndex", 0)
                        val layerIndex = packet.optInt("layerIndex", 0)
                        val isLocalLayer = packet.optBoolean("isLocalLayer", false)
                        val seq = packet.optLong("seq", 0L)
                        val strokeObj = packet.optJSONObject("stroke")
                        val stroke = strokeObj?.let { parseStrokeFromJson(it) }
                        if (peerId.isNotBlank()) {
                            val prevSeq = lastPeerPreviewSeq[peerId] ?: 0L
                            val isOutOfOrder = seq > 0L && seq < prevSeq
                            val isAlreadyCommitted = stroke != null && stroke.id in committedStrokeIds
                            if (!isOutOfOrder && !isAlreadyCommitted) {
                                if (seq > 0L) lastPeerPreviewSeq[peerId] = seq
                                withContext(Dispatchers.Main) {
                                    onRemoteLiveStrokeUpdated?.invoke(peerId, frameIndex, layerIndex, stroke, isLocalLayer)
                                }
                            }
                            broadcastToOtherPeers(connId, line)
                        }
                    }
                    "BRUSH_DEF" -> {
                        val senderId = packet.optString("senderId", "")
                        val senderName = packet.optString("senderName", "Amigo")
                        val brushObj = packet.optJSONObject("brush")
                        val preset = brushObj?.let { KritaBrushImporter.jsonToBrushPreset(it) }
                        if (preset != null && senderId.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                onBrushDefinitionReceived?.invoke(senderId, senderName, preset)
                            }
                            broadcastToOtherPeers(connId, line)
                        }
                    }
                    "UNDO_STROKE", "REDO_STROKE" -> {
                        val opType = packet.optString("type")
                        val authorId = packet.optString("authorId", "")
                        val frameIndex = packet.optInt("frameIndex", 0)
                        val layerIndex = packet.optInt("layerIndex", 0)
                        val isLocalLayer = packet.optBoolean("isLocalLayer", false)
                        val strokeIds = mutableListOf<String>()
                        val idArr = packet.optJSONArray("strokeIds")
                        if (idArr != null) {
                            for (i in 0 until idArr.length()) {
                                strokeIds.add(idArr.getString(i))
                            }
                        }
                        val strokesList = mutableListOf<DrawingStroke>()
                        val stArr = packet.optJSONArray("strokes")
                        if (stArr != null) {
                            for (i in 0 until stArr.length()) {
                                strokesList.add(parseStrokeFromJson(stArr.getJSONObject(i)))
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onRemoteUndoRedoOpReceived?.invoke(opType, authorId, frameIndex, layerIndex, strokeIds, strokesList, isLocalLayer)
                        }
                        broadcastToOtherPeers(connId, line)
                    }
                    "CURSOR_ICON_UPDATE" -> {
                        val peerId = packet.optString("peerId", "")
                        val cursorB64 = packet.optString("customCursorBase64", "").ifEmpty { null }
                        if (peerId.isNotBlank()) {
                            hostConnectedPeers[connId]?.let { existing ->
                                if (existing.id == peerId) {
                                    hostConnectedPeers[connId] = existing.copy(customCursorBase64 = cursorB64)
                                }
                            }
                            val snapshot = hostConnectedPeers.values.toList()
                            withContext(Dispatchers.Main) {
                                onPeerCursorIconUpdated?.invoke(peerId, cursorB64)
                                onConnectedPeersUpdated?.invoke(snapshot)
                            }
                            broadcastToOtherPeers(connId, line)
                        }
                    }
                    "CLIENT_LEAVE" -> {
                        break
                    }
                    "PING" -> {
                        val ts = packet.getLong("timestamp")
                        val pong = JSONObject().apply {
                            put("type", "PONG")
                            put("timestamp", ts)
                        }.toString()
                        try { writeLineThreadSafe(writer, pong) } catch (_: Exception) {}
                    }
                    "PONG" -> {
                        val ts = packet.getLong("timestamp")
                        val rtt = (System.currentTimeMillis() - ts).toInt().coerceAtLeast(1)
                        hostConnectedPeers[connId]?.let { existing ->
                            hostConnectedPeers[connId] = existing.copy(pingMs = rtt)
                        }
                        val snapshot = hostConnectedPeers.values.toList()
                        withContext(Dispatchers.Main) {
                            onPingUpdated?.invoke(rtt)
                            onConnectedPeersUpdated?.invoke(snapshot)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            hostClientWriters.remove(connId)
            hostClientCloseHandles.remove(connId)
            val leftPeer = hostConnectedPeers.remove(connId)
            try { reader.close() } catch (_: Exception) {}
            try { writer.close() } catch (_: Exception) {}
            try { closeHandle() } catch (_: Exception) {}
            if (leftPeer != null) {
                val peerLeftMsg = JSONObject().apply {
                    put("type", "PEER_LEFT")
                    put("peerId", leftPeer.id)
                }.toString()
                hostClientWriters.values.forEach { w ->
                    try { writeLineThreadSafe(w, peerLeftMsg) } catch (_: Exception) {}
                }
            }
            broadcastHostPeersList()
            val remainingPeers = hostConnectedPeers.values.toList()
            scope.launch(Dispatchers.Main) {
                leftPeer?.let { onPeerLeft?.invoke(it.id) }
                onPeerCountChanged?.invoke(hostClientWriters.size + 1)
                onConnectedPeersUpdated?.invoke(remainingPeers)
            }
        }
    }

    private fun broadcastHostPeersList() {
        if (hostClientWriters.isEmpty()) return
        try {
            val arr = JSONArray()
            currentHostSelfInfo?.let { h ->
                arr.put(JSONObject().apply {
                    put("id", h.id)
                    put("name", h.name)
                    put("avatarBase64", h.avatarBase64 ?: "")
                    put("avatarColor", h.avatarColor)
                    put("customCursorBase64", h.customCursorBase64 ?: "")
                    put("isHost", true)
                    put("pingMs", h.pingMs)
                })
            }
            hostConnectedPeers.values.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("avatarBase64", p.avatarBase64 ?: "")
                    put("avatarColor", p.avatarColor)
                    put("customCursorBase64", p.customCursorBase64 ?: "")
                    put("isHost", false)
                    put("pingMs", p.pingMs)
                })
            }
            val msg = JSONObject().apply {
                put("type", "PEERS_LIST")
                put("peers", arr)
            }.toString()
            hostClientWriters.values.forEach { w ->
                try { writeLineThreadSafe(w, msg) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun writeLineThreadSafe(writer: BufferedWriter, line: String) {
        synchronized(writer) {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    private fun broadcastToOtherPeers(excludeConnId: String, line: String) {
        hostClientWriters.forEach { (id, writer) ->
            if (id != excludeConnId) {
                try {
                    writeLineThreadSafe(writer, line)
                } catch (_: Exception) {}
            }
        }
    }

    // --- Client Connecting to Host Room (TCP or Bluetooth RFCOMM) ---

    fun connectToRoom(
        hostIp: String,
        hostPort: Int,
        user: UserAccount,
        roomId: String
    ) {
        localUser = user
        acquireWifiLocks(highPerf = true)
        clientListenerJob?.cancel()
        clientListenerJob = scope.launch(KodanimaDispatchers.networkIo) {
            var socket: Socket? = null
            try {
                socket = Socket()
                tuneLowLatencySocket(socket)
                socket.connect(InetSocketAddress(hostIp, hostPort), 6000)
                clientSocket = socket
                val closeAction: () -> Unit = { try { socket.close() } catch (_: Exception) {} }
                clientCloseHandle = closeAction
                runClientSessionStreams(
                    inputStream = socket.getInputStream(),
                    outputStream = socket.getOutputStream(),
                    user = user,
                    roomId = roomId,
                    closeHandle = closeAction
                )
            } catch (e: Exception) {
                try { socket?.close() } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    onDisconnected?.invoke("Erro ao conectar: ${e.localizedMessage ?: "Tempo esgotado"}")
                }
            }
        }
    }

    fun connectViaExternalStream(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        user: UserAccount,
        roomId: String,
        closeHandle: () -> Unit
    ) {
        localUser = user
        clientListenerJob?.cancel()
        clientListenerJob = scope.launch(KodanimaDispatchers.networkIo) {
            clientCloseHandle = closeHandle
            runClientSessionStreams(inputStream, outputStream, user, roomId, closeHandle)
        }
    }

    private suspend fun runClientSessionStreams(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        user: UserAccount,
        roomId: String,
        closeHandle: () -> Unit
    ) {
        var writer: BufferedWriter? = null
        var reader: BufferedReader? = null
        try {
            writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), 16384)
            reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 16384)
            clientWriter = writer

            val connId = UUID.randomUUID().toString()
            val knockPacket = JSONObject().apply {
                put("type", "KNOCK")
                put("connId", connId)
                put("userId", user.id)
                put("userName", user.name)
                put("avatarBase64", user.avatarBase64 ?: "")
                put("avatarColor", user.avatarColor)
                put("customCursorBase64", user.customCursorBase64 ?: "")
                put("roomId", roomId)
            }.toString()
            writeLineThreadSafe(writer, knockPacket)

            val respLine = reader.readLine() ?: throw IllegalStateException("Conexão fechada pelo anfitrião")
            val resp = JSONObject(respLine)

            if (resp.optBoolean("accepted", false)) {
                val projectJson = resp.optString("projectJson").ifEmpty {
                    resp.optJSONObject("project")?.toString() ?: ""
                }
                val project = FileImportExportManager.projectFromJson(projectJson)

                val hostId = resp.optString("hostId", "")
                val hostName = resp.optString("hostName", "Anfitrião")
                val hostAvatarB64 = resp.optString("hostAvatarBase64", "").ifEmpty { null }
                val hostCustomCursorB64 = resp.optString("hostCustomCursorBase64", "").ifEmpty { null }
                val savedUri = if (hostAvatarB64 != null) {
                    appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, hostId, hostAvatarB64) }
                } else null

                val hostFriend = ConnectedFriendInfo(
                    id = hostId,
                    name = hostName,
                    avatarUri = savedUri,
                    avatarBase64 = hostAvatarB64,
                    avatarColor = resp.optLong("hostAvatarColor", 0xFF00E5FF),
                    customCursorBase64 = hostCustomCursorB64,
                    isHost = true
                )

                withContext(Dispatchers.Main) {
                    onFriendConnected?.invoke(hostFriend)
                    onConnectedPeersUpdated?.invoke(listOf(hostFriend))
                    onConnected?.invoke()
                    onProjectSynced?.invoke(project)
                }

                val pingJob = scope.launch(KodanimaDispatchers.networkIo) {
                    while (isActive && clientWriter != null) {
                        delay(2000)
                        try {
                            val pingPacket = JSONObject().apply {
                                put("type", "PING")
                                put("timestamp", System.currentTimeMillis())
                            }.toString()
                            writeLineThreadSafe(writer, pingPacket)
                        } catch (_: Exception) {
                            break
                        }
                    }
                }

                var shutdownHandled = false
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        val packet = JSONObject(line)
                        when (packet.optString("type")) {
                            "STROKE" -> {
                                val frameIndex = packet.getInt("frameIndex")
                                val layerIndex = packet.getInt("layerIndex")
                                val isLocalLayer = packet.optBoolean("isLocalLayer", false)
                                val stroke = parseStrokeFromJson(packet.getJSONObject("stroke"))
                                committedStrokeIds.add(stroke.id)
                                if (committedStrokeIds.size > 4096) committedStrokeIds.clear()
                                withContext(Dispatchers.Main) {
                                    onStrokeReceived?.invoke(frameIndex, layerIndex, stroke, isLocalLayer)
                                }
                            }
                            "CLEAR_FRAME" -> {
                                val frameIndex = packet.getInt("frameIndex")
                                withContext(Dispatchers.Main) {
                                    onClearFrameReceived?.invoke(frameIndex)
                                }
                            }
                            "ADD_FRAME" -> {
                                val insertAt = packet.getInt("insertAt")
                                withContext(Dispatchers.Main) {
                                    onAddFrameReceived?.invoke(insertAt)
                                }
                            }
                            "ADD_LAYER" -> {
                                val layerName = packet.optString("layerName", "Camada")
                                withContext(Dispatchers.Main) {
                                    onAddLayerReceived?.invoke(layerName)
                                }
                            }
                            "MOVE_LAYER" -> {
                                val fromIndex = packet.optInt("fromIndex", -1)
                                val toIndex = packet.optInt("toIndex", -1)
                                if (fromIndex >= 0 && toIndex >= 0) {
                                    withContext(Dispatchers.Main) {
                                        onMoveLayerReceived?.invoke(fromIndex, toIndex)
                                    }
                                }
                            }
                            "SYNC_FRAME_LOCAL_LAYERS" -> {
                                val frameIndex = packet.optInt("frameIndex", 0)
                                val arr = packet.optJSONArray("localLayers")
                                val list = if (arr != null) {
                                    (0 until arr.length()).mapNotNull { i ->
                                        arr.optJSONObject(i)?.let {
                                            FileImportExportManager.jsonToLayer(it, i, "Subcamada")
                                        }
                                    }
                                } else emptyList()
                                withContext(Dispatchers.Main) {
                                    onFrameLocalLayersReceived?.invoke(frameIndex, list)
                                }
                            }
                            "CURSOR" -> {
                                val cursor = RemotePeerCursor(
                                    peerId = packet.getString("peerId"),
                                    peerName = packet.getString("peerName"),
                                    color = packet.getLong("color"),
                                    frameIndex = packet.getInt("frameIndex"),
                                    x = packet.getDouble("x").toFloat(),
                                    y = packet.getDouble("y").toFloat(),
                                    isDrawing = packet.optBoolean("isDrawing", false)
                                )
                                withContext(Dispatchers.Main) {
                                    onRemoteCursorUpdated?.invoke(cursor)
                                }
                            }
                            "STROKE_PREVIEW" -> {
                                val peerId = packet.optString("peerId", "")
                                val frameIndex = packet.optInt("frameIndex", 0)
                                val layerIndex = packet.optInt("layerIndex", 0)
                                val isLocalLayer = packet.optBoolean("isLocalLayer", false)
                                val seq = packet.optLong("seq", 0L)
                                val strokeObj = packet.optJSONObject("stroke")
                                val stroke = strokeObj?.let { parseStrokeFromJson(it) }
                                if (peerId.isNotBlank()) {
                                    val prevSeq = lastPeerPreviewSeq[peerId] ?: 0L
                                    val isOutOfOrder = seq > 0L && seq < prevSeq
                                    val isAlreadyCommitted = stroke != null && stroke.id in committedStrokeIds
                                    if (!isOutOfOrder && !isAlreadyCommitted) {
                                        if (seq > 0L) lastPeerPreviewSeq[peerId] = seq
                                        withContext(Dispatchers.Main) {
                                            onRemoteLiveStrokeUpdated?.invoke(peerId, frameIndex, layerIndex, stroke, isLocalLayer)
                                        }
                                    }
                                }
                            }
                            "BRUSH_DEF" -> {
                                val senderId = packet.optString("senderId", "")
                                val senderName = packet.optString("senderName", "Amigo")
                                val brushObj = packet.optJSONObject("brush")
                                val preset = brushObj?.let { KritaBrushImporter.jsonToBrushPreset(it) }
                                if (preset != null && senderId.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        onBrushDefinitionReceived?.invoke(senderId, senderName, preset)
                                    }
                                }
                            }
                            "UNDO_STROKE", "REDO_STROKE" -> {
                                val opType = packet.optString("type")
                                val authorId = packet.optString("authorId", "")
                                val frameIndex = packet.optInt("frameIndex", 0)
                                val layerIndex = packet.optInt("layerIndex", 0)
                                val isLocalLayer = packet.optBoolean("isLocalLayer", false)
                                val strokeIds = mutableListOf<String>()
                                val idArr = packet.optJSONArray("strokeIds")
                                if (idArr != null) {
                                    for (i in 0 until idArr.length()) {
                                        strokeIds.add(idArr.getString(i))
                                    }
                                }
                                val strokesList = mutableListOf<DrawingStroke>()
                                val stArr = packet.optJSONArray("strokes")
                                if (stArr != null) {
                                    for (i in 0 until stArr.length()) {
                                        strokesList.add(parseStrokeFromJson(stArr.getJSONObject(i)))
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    onRemoteUndoRedoOpReceived?.invoke(opType, authorId, frameIndex, layerIndex, strokeIds, strokesList, isLocalLayer)
                                }
                            }
                            "CURSOR_ICON_UPDATE" -> {
                                val peerId = packet.optString("peerId", "")
                                val cursorB64 = packet.optString("customCursorBase64", "").ifEmpty { null }
                                if (peerId.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        onPeerCursorIconUpdated?.invoke(peerId, cursorB64)
                                    }
                                }
                            }
                            "PEER_LEFT" -> {
                                val leftPeerId = packet.optString("peerId", "")
                                if (leftPeerId.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        onPeerLeft?.invoke(leftPeerId)
                                    }
                                }
                            }
                            "PEERS_LIST" -> {
                                val arr = packet.optJSONArray("peers")
                                if (arr != null) {
                                    val list = mutableListOf<ConnectedFriendInfo>()
                                    for (i in 0 until arr.length()) {
                                        val pObj = arr.getJSONObject(i)
                                        val pid = pObj.optString("id", "")
                                        val b64 = pObj.optString("avatarBase64", "").ifEmpty { null }
                                        val cursorB64 = pObj.optString("customCursorBase64", "").ifEmpty { null }
                                        val savedAvatarUri = if (b64 != null) {
                                            appContext?.let { AvatarTransferHelper.savePeerAvatarFromBase64(it, pid, b64) }
                                        } else null
                                        list.add(
                                            ConnectedFriendInfo(
                                                id = pid,
                                                name = pObj.optString("name", "Amigo"),
                                                avatarUri = savedAvatarUri,
                                                avatarBase64 = b64,
                                                avatarColor = pObj.optLong("avatarColor", 0xFF00E5FF),
                                                customCursorBase64 = cursorB64,
                                                isHost = pObj.optBoolean("isHost", false),
                                                pingMs = pObj.optInt("pingMs", 12)
                                            )
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        onConnectedPeersUpdated?.invoke(list)
                                    }
                                }
                            }
                            "HOST_SHUTDOWN" -> {
                                val reasonMsg = packet.optString("message", "o dono da casa saiu :(")
                                shutdownHandled = true
                                withContext(Dispatchers.Main) {
                                    onHostShutdown?.invoke(reasonMsg)
                                }
                                break
                            }
                            "PING" -> {
                                val ts = packet.getLong("timestamp")
                                val pong = JSONObject().apply {
                                    put("type", "PONG")
                                    put("timestamp", ts)
                                }.toString()
                                try { writeLineThreadSafe(writer, pong) } catch (_: Exception) {}
                            }
                            "PONG" -> {
                                val ts = packet.getLong("timestamp")
                                val rtt = (System.currentTimeMillis() - ts).toInt().coerceAtLeast(1)
                                withContext(Dispatchers.Main) {
                                    onPingUpdated?.invoke(rtt)
                                }
                            }
                        }
                    }
                    if (!shutdownHandled) {
                        withContext(Dispatchers.Main) {
                            onHostShutdown?.invoke("o dono da casa saiu :(")
                        }
                    }
                } finally {
                    pingJob.cancel()
                }
            } else {
                val reason = resp.optString("reason", "Entrada recusada pelo anfitrião.")
                withContext(Dispatchers.Main) {
                    onDisconnected?.invoke(reason)
                }
                closeHandle()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onDisconnected?.invoke("Erro ao conectar: ${e.localizedMessage ?: "Tempo esgotado"}")
            }
        } finally {
            clientWriter = null
            clientCloseHandle = null
            try { reader?.close() } catch (_: Exception) {}
            try { writer?.close() } catch (_: Exception) {}
            try { closeHandle() } catch (_: Exception) {}
            if (activeRoomInfo == null) releaseWifiLocks(releaseHighPerfOnly = true)
        }
    }

    // --- Real-Time Collaborative Delta Senders ---

    private fun ensureSessionSenderStarted() {
        if (sessionSenderJob?.isActive == true) return
        sessionSenderJob = scope.launch(KodanimaDispatchers.networkIo) {
            for ((type, packet) in sessionSendChannel) {
                val remaining = outboundQueueDepth.decrementAndGet().coerceAtLeast(0)
                // Coalesce stale cursor updates under congested Wi-Fi while preserving all stroke/undo/brush packets in strict FIFO order
                if (type == "CURSOR" && remaining > 12) continue
                clientWriter?.let { w ->
                    try { writeLineThreadSafe(w, packet) } catch (_: Exception) {}
                }
                hostClientWriters.values.forEach { w ->
                    try { writeLineThreadSafe(w, packet) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun broadcastSessionPacket(packet: String, type: String = "DATA") {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        ensureSessionSenderStarted()
        if (type == "CURSOR" && outboundQueueDepth.get() > 16) return
        if (sessionSendChannel.trySend(type to packet).isSuccess) {
            outboundQueueDepth.incrementAndGet()
        }
    }

    fun sendStroke(frameIndex: Int, layerIndex: Int, stroke: DrawingStroke, isLocalLayer: Boolean = false) {
        val packet = JSONObject().apply {
            put("type", "STROKE")
            put("frameIndex", frameIndex)
            put("layerIndex", layerIndex)
            if (isLocalLayer) put("isLocalLayer", true)
            put("stroke", strokeToJson(stroke))
        }.toString()
        broadcastSessionPacket(packet, "STROKE")
    }

    fun sendClearFrame(frameIndex: Int) {
        val packet = JSONObject().apply {
            put("type", "CLEAR_FRAME")
            put("frameIndex", frameIndex)
        }.toString()
        broadcastSessionPacket(packet, "CLEAR_FRAME")
    }

    fun sendAddFrame(insertAt: Int) {
        val packet = JSONObject().apply {
            put("type", "ADD_FRAME")
            put("insertAt", insertAt)
        }.toString()
        broadcastSessionPacket(packet, "ADD_FRAME")
    }

    fun sendAddLayer(layerName: String) {
        val packet = JSONObject().apply {
            put("type", "ADD_LAYER")
            put("layerName", layerName)
        }.toString()
        broadcastSessionPacket(packet, "ADD_LAYER")
    }

    fun sendMoveLayer(fromIndex: Int, toIndex: Int) {
        val packet = JSONObject().apply {
            put("type", "MOVE_LAYER")
            put("fromIndex", fromIndex)
            put("toIndex", toIndex)
        }.toString()
        broadcastSessionPacket(packet, "MOVE_LAYER")
    }

    fun sendSyncFrameLocalLayers(frameIndex: Int, localLayers: List<Layer>) {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val arr = JSONArray()
        localLayers.forEach { layer ->
            arr.put(FileImportExportManager.layerToJson(layer))
        }
        val packet = JSONObject().apply {
            put("type", "SYNC_FRAME_LOCAL_LAYERS")
            put("frameIndex", frameIndex)
            put("localLayers", arr)
        }.toString()
        broadcastSessionPacket(packet, "SYNC_FRAME_LOCAL_LAYERS")
    }

    fun sendCursor(
        user: UserAccount,
        frameIndex: Int,
        x: Float,
        y: Float,
        isDrawing: Boolean
    ) {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val packet = JSONObject().apply {
            put("type", "CURSOR")
            put("peerId", user.id)
            put("peerName", user.name)
            put("color", user.avatarColor)
            put("frameIndex", frameIndex)
            put("x", x)
            put("y", y)
            put("isDrawing", isDrawing)
        }.toString()
        broadcastSessionPacket(packet, "CURSOR")
    }

    fun sendLiveStrokePreview(
        peerId: String,
        frameIndex: Int,
        layerIndex: Int,
        stroke: DrawingStroke?,
        isLocalLayer: Boolean = false
    ) {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val packet = JSONObject().apply {
            put("type", "STROKE_PREVIEW")
            put("peerId", peerId)
            put("frameIndex", frameIndex)
            put("layerIndex", layerIndex)
            if (isLocalLayer) put("isLocalLayer", true)
            put("seq", outgoingPreviewSeq.incrementAndGet())
            if (stroke != null) {
                put("stroke", strokeToJson(stroke))
            }
        }.toString()
        broadcastSessionPacket(packet, "STROKE_PREVIEW")
    }

    fun sendBrushDefinition(
        senderId: String,
        senderName: String,
        preset: BrushPreset
    ) {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val packet = JSONObject().apply {
            put("type", "BRUSH_DEF")
            put("senderId", senderId)
            put("senderName", senderName)
            put("brush", KritaBrushImporter.brushPresetToJson(preset))
        }.toString()
        broadcastSessionPacket(packet, "BRUSH_DEF")
    }

    fun sendUndoRemoveStrokes(
        authorId: String,
        frameIndex: Int,
        layerIndex: Int,
        strokeIds: List<String>,
        isLocalLayer: Boolean = false
    ) {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val packet = JSONObject().apply {
            put("type", "UNDO_STROKE")
            put("authorId", authorId)
            put("frameIndex", frameIndex)
            put("layerIndex", layerIndex)
            if (isLocalLayer) put("isLocalLayer", true)
            put("strokeIds", JSONArray(strokeIds))
        }.toString()
        broadcastSessionPacket(packet)
    }

    fun sendRedoRestoreStrokes(
        authorId: String,
        frameIndex: Int,
        layerIndex: Int,
        strokes: List<DrawingStroke>,
        isLocalLayer: Boolean = false
    ) {
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val arr = JSONArray()
        strokes.forEach { arr.put(strokeToJson(it)) }
        val packet = JSONObject().apply {
            put("type", "REDO_STROKE")
            put("authorId", authorId)
            put("frameIndex", frameIndex)
            put("layerIndex", layerIndex)
            if (isLocalLayer) put("isLocalLayer", true)
            put("strokes", arr)
        }.toString()
        broadcastSessionPacket(packet)
    }

    fun sendCursorIconUpdate(user: UserAccount) {
        localUser = user
        currentHostSelfInfo = currentHostSelfInfo?.copy(customCursorBase64 = user.customCursorBase64)
        if (clientWriter == null && hostClientWriters.isEmpty()) return
        val packet = JSONObject().apply {
            put("type", "CURSOR_ICON_UPDATE")
            put("peerId", user.id)
            put("customCursorBase64", user.customCursorBase64 ?: "")
        }.toString()
        broadcastSessionPacket(packet)
    }

    fun sendClientLeave(userId: String) {
        val w = clientWriter ?: return
        val packet = JSONObject().apply {
            put("type", "CLIENT_LEAVE")
            put("peerId", userId)
        }.toString()
        try {
            writeLineThreadSafe(w, packet)
        } catch (_: Exception) {}
    }

    /**
     * Broadcasts explicit host shutdown alert to every connected visitor before tearing down sockets.
     */
    fun shutdownHostAndNotify(message: String = "o dono da casa saiu :(") {
        val writersSnapshot = hostClientWriters.values.toList()
        if (writersSnapshot.isEmpty()) {
            stopSessionOnly()
            return
        }
        val shutdownLine = JSONObject().apply {
            put("type", "HOST_SHUTDOWN")
            put("message", message)
        }.toString()
        scope.launch(KodanimaDispatchers.networkIo) {
            writersSnapshot.forEach { w ->
                try { writeLineThreadSafe(w, shutdownLine) } catch (_: Exception) {}
            }
            delay(120)
            stopSessionOnly()
        }
    }

    /**
     * Stops only the active room/client session while keeping discovery/friend requests alive.
     */
    fun stopSessionOnly() {
        serverJob?.cancel()
        serverJob = null
        broadcastBeaconJob?.cancel()
        broadcastBeaconJob = null
        clientListenerJob?.cancel()
        clientListenerJob = null
        hostPingJob?.cancel()
        hostPingJob = null

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        try { clientSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        try { clientCloseHandle?.invoke() } catch (_: Exception) {}
        clientCloseHandle = null
        clientWriter = null

        hostClientWriters.values.forEach { w -> try { w.close() } catch (_: Exception) {} }
        hostClientWriters.clear()
        hostClientCloseHandles.values.forEach { closeFn -> try { closeFn() } catch (_: Exception) {} }
        hostClientCloseHandles.clear()
        hostConnectedPeers.clear()
        currentHostSelfInfo = null

        pendingKnocks.values.forEach { c -> try { c.closeHandle() } catch (_: Exception) {} }
        pendingKnocks.clear()

        activeRoomInfo = null
        latestHostProject = null
        lastPeerPreviewSeq.clear()
        committedStrokeIds.clear()
        releaseWifiLocks(releaseHighPerfOnly = true)
    }

    fun stopAll() {
        stopSessionOnly()
        stopDiscovery()
        releaseWifiLocks(releaseHighPerfOnly = false)
    }

    // --- Stroke JSON Serialization with Recomputed Bounds ---

    fun strokeToJson(stroke: DrawingStroke): JSONObject {
        return JSONObject().apply {
            put("id", stroke.id)
            if (stroke.authorId.isNotBlank()) {
                put("authorId", stroke.authorId)
            }
            put("tool", stroke.tool.name)
            put("color", stroke.color)
            put("brushSize", stroke.brushSize.coerceIn(0.5f, 200f).toDouble())
            put("opacity", stroke.opacity.coerceIn(0.01f, 1.0f).toDouble())
            put("brushPresetId", stroke.brushPresetId)
            if (!stroke.customBrushParams.isNullOrBlank()) {
                put("customBrushParams", stroke.customBrushParams)
            }
            put("isFilled", stroke.isFilled)
            val flatPts = JSONArray()
            stroke.points.forEach { pt ->
                flatPts.put(((pt.x * 1000f).roundToInt()) / 1000.0)
                flatPts.put(((pt.y * 1000f).roundToInt()) / 1000.0)
                flatPts.put((((pt.pressure.coerceIn(0.05f, 2.0f)) * 1000f).roundToInt()) / 1000.0)
            }
            put("pts", flatPts)
            if (!stroke.clipPolygon.isNullOrEmpty() && stroke.clipPolygon.size >= 3) {
                val clipArr = JSONArray()
                stroke.clipPolygon.forEach { cp ->
                    clipArr.put(((cp.x * 1000f).roundToInt()) / 1000.0)
                    clipArr.put(((cp.y * 1000f).roundToInt()) / 1000.0)
                }
                put("clip", clipArr)
                if (stroke.clipInverted) {
                    put("clipInverted", true)
                    put("clipBoundsWidth", stroke.clipBoundsWidth.toDouble())
                    put("clipBoundsHeight", stroke.clipBoundsHeight.toDouble())
                }
            }
        }
    }

    fun parseStrokeFromJson(obj: JSONObject): DrawingStroke {
        val toolName = obj.optString("tool", "PEN")
        val tool = try { ToolType.valueOf(toolName) } catch (_: Exception) { ToolType.PEN }
        val points = mutableListOf<StrokePoint>()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        val compactArr = obj.optJSONArray("pts")
        if (compactArr != null) {
            var i = 0
            val len = compactArr.length()
            while (i + 2 < len) {
                val x = compactArr.getDouble(i).toFloat()
                val y = compactArr.getDouble(i + 1).toFloat()
                val p = compactArr.getDouble(i + 2).toFloat().coerceIn(0.05f, 2.0f)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                points.add(StrokePoint(x = x, y = y, pressure = p))
                i += 3
            }
        } else {
            val arr = obj.optJSONArray("points")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val ptObj = arr.getJSONObject(i)
                    val x = ptObj.getDouble("x").toFloat()
                    val y = ptObj.getDouble("y").toFloat()
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    points.add(
                        StrokePoint(
                            x = x,
                            y = y,
                            pressure = ptObj.optDouble("pressure", 1.0).toFloat().coerceIn(0.05f, 2.0f)
                        )
                    )
                }
            }
        }
        val customParams = if (obj.has("customBrushParams") && !obj.isNull("customBrushParams")) {
            obj.optString("customBrushParams", "").ifBlank { null }
        } else null
        val clipArr = obj.optJSONArray("clip")
        val clipPoly = if (clipArr != null && clipArr.length() >= 6) {
            val polyList = ArrayList<StrokePoint>(clipArr.length() / 2)
            var ci = 0
            while (ci + 1 < clipArr.length()) {
                polyList.add(StrokePoint(x = clipArr.getDouble(ci).toFloat(), y = clipArr.getDouble(ci + 1).toFloat()))
                ci += 2
            }
            polyList
        } else null
        return DrawingStroke(
            id = obj.optString("id", UUID.randomUUID().toString()),
            authorId = obj.optString("authorId", ""),
            tool = tool,
            color = obj.optLong("color", 0xFFFFFFFF),
            brushSize = obj.optDouble("brushSize", 6.0).toFloat().coerceIn(0.5f, 200f),
            opacity = obj.optDouble("opacity", 1.0).toFloat().coerceIn(0.01f, 1.0f),
            brushPresetId = obj.optInt("brushPresetId", 1),
            customBrushParams = customParams,
            clipPolygon = clipPoly,
            clipInverted = obj.optBoolean("clipInverted", false),
            clipBoundsWidth = obj.optDouble("clipBoundsWidth", 0.0).toFloat(),
            clipBoundsHeight = obj.optDouble("clipBoundsHeight", 0.0).toFloat(),
            isFilled = obj.optBoolean("isFilled", false),
            points = points,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
    }
}
