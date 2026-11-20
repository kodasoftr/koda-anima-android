package com.example.engine

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

data class WifiDirectPeerItem(
    val deviceName: String,
    val deviceAddress: String,
    val statusLabel: String,
    val isConnected: Boolean = false
)

/**
 * Native Android Wi-Fi Direct (`WifiP2pManager`) helper for direct peer-to-peer pairing
 * across devices without requiring a router or internet access.
 */
class WifiDirectP2pHelper(
    private val context: Context,
    private val onPeersChanged: (List<WifiDirectPeerItem>) -> Unit,
    private val onP2pConnected: (isGroupOwner: Boolean, groupOwnerIp: String) -> Unit,
    private val onStatusMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "WifiDirectP2pHelper"

        private fun statusToLabel(status: Int): String = when (status) {
            WifiP2pDevice.CONNECTED -> "Conectado"
            WifiP2pDevice.INVITED -> "Convidado"
            WifiP2pDevice.FAILED -> "Falhou"
            WifiP2pDevice.AVAILABLE -> "Disponível"
            WifiP2pDevice.UNAVAILABLE -> "Indisponível"
            else -> "Desconhecido"
        }
    }

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val mgr = manager ?: return
            val ch = channel ?: return
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!PermissionHelper.hasWifiDirectPermission(ctx)) return
                    try {
                        mgr.requestPeers(ch) { peerList ->
                            val items = peerList.deviceList.map { d ->
                                WifiDirectPeerItem(
                                    deviceName = d.deviceName.ifBlank { d.deviceAddress },
                                    deviceAddress = d.deviceAddress,
                                    statusLabel = statusToLabel(d.status),
                                    isConnected = d.status == WifiP2pDevice.CONNECTED
                                )
                            }
                            onPeersChanged(items)
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Missing permission for requestPeers: ${e.message}")
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val netInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as? NetworkInfo
                    }
                    if (netInfo?.isConnected == true) {
                        mgr.requestConnectionInfo(ch) { info ->
                            val ownerIp = info.groupOwnerAddress?.hostAddress
                            if (info.groupFormed && !ownerIp.isNullOrBlank()) {
                                onP2pConnected(info.isGroupOwner, ownerIp)
                            }
                        }
                    }
                }
            }
        }
    }

    fun start() {
        if (manager == null) {
            manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        }
        val mgr = manager ?: run {
            onStatusMessage("Este dispositivo não suporta Wi-Fi Direct.")
            return
        }
        if (channel == null) {
            channel = mgr.initialize(context, Looper.getMainLooper(), null)
        }
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            isRegistered = true
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        start()
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!PermissionHelper.hasWifiDirectPermission(context)) {
            onStatusMessage("Permissão de pareamento por proximidade (Wi-Fi Direct) necessária.")
            return
        }
        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onStatusMessage("Buscando dispositivos próximos via Wi-Fi Direct...")
                }

                override fun onFailure(reason: Int) {
                    onStatusMessage("Wi-Fi Direct: ative o Wi-Fi para descobrir celulares próximos (cód. $reason).")
                }
            })
        } catch (e: SecurityException) {
            onStatusMessage("Permissão Wi-Fi Direct negada.")
        }
    }

    @SuppressLint("MissingPermission")
    fun createGroupForHosting() {
        start()
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!PermissionHelper.hasWifiDirectPermission(context)) return
        try {
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onStatusMessage("Grupo Wi-Fi Direct criado! Amigos podem parear diretamente.")
                }

                override fun onFailure(reason: Int) {
                    // Already in a group (BUSY=2) is harmless
                    if (reason != WifiP2pManager.BUSY) {
                        Log.w(TAG, "createGroup reason=$reason")
                    }
                }
            })
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String) {
        start()
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!PermissionHelper.hasWifiDirectPermission(context)) {
            onStatusMessage("Permissão Wi-Fi Direct necessária para parear.")
            return
        }
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }
        try {
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onStatusMessage("Pareando via Wi-Fi Direct... Aceite no outro celular se solicitado.")
                }

                override fun onFailure(reason: Int) {
                    onStatusMessage("Falha no pareamento Wi-Fi Direct (cód. $reason).")
                }
            })
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (isRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            isRegistered = false
        }
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            try { mgr.removeGroup(ch, null) } catch (_: Exception) {}
        }
    }
}
