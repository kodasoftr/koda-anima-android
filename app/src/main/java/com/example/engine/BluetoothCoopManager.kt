package com.example.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class BluetoothPeerItem(
    val name: String,
    val address: String,
    val isBonded: Boolean
)

/**
 * Native Android Bluetooth Classic RFCOMM transport for Kodanima 2D Coop.
 * Enables full real-time Canvas collaboration when there is zero Wi-Fi router or internet.
 */
class BluetoothCoopManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDevicesUpdated: (List<BluetoothPeerItem>) -> Unit,
    private val onIncomingClientConnected: (InputStream, OutputStream, remoteName: String, closeHandle: () -> Unit) -> Unit,
    private val onClientConnectedToHost: (InputStream, OutputStream, remoteName: String, closeHandle: () -> Unit) -> Unit,
    private val onStatusMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "BluetoothCoopManager"
        private const val SERVICE_NAME = "Kodanima2DCoop"
        val KODANIMA_BT_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val discoveredMap = java.util.concurrent.ConcurrentHashMap<String, BluetoothPeerItem>()
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                @Suppress("DEPRECATION")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                } ?: return
                val addr = device.address ?: return
                val devName = try { device.name ?: addr } catch (_: SecurityException) { addr }
                val bonded = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
                discoveredMap[addr] = BluetoothPeerItem(devName, addr, bonded)
                onDevicesUpdated(discoveredMap.values.toList())
            }
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun refreshPairedAndScan() {
        val bt = adapter ?: run {
            onStatusMessage("Bluetooth não disponível neste dispositivo.")
            return
        }
        if (!PermissionHelper.hasBluetoothPermission(context)) {
            onStatusMessage("Permissão de Bluetooth necessária para parear sem roteador.")
            return
        }
        if (!bt.isEnabled) {
            onStatusMessage("Ative o Bluetooth do celular para conectar sem Wi-Fi.")
            return
        }
        try {
            bt.bondedDevices?.forEach { dev ->
                discoveredMap[dev.address] = BluetoothPeerItem(
                    name = dev.name ?: dev.address,
                    address = dev.address,
                    isBonded = true
                )
            }
            onDevicesUpdated(discoveredMap.values.toList())

            if (!receiverRegistered) {
                ContextCompat.registerReceiver(
                    context,
                    discoveryReceiver,
                    IntentFilter(BluetoothDevice.ACTION_FOUND),
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            }
            if (bt.isDiscovering) bt.cancelDiscovery()
            bt.startDiscovery()
            onStatusMessage("Procurando aparelhos Bluetooth próximos...")
        } catch (e: SecurityException) {
            onStatusMessage("Permissão Bluetooth negada.")
        }
    }

    @SuppressLint("MissingPermission")
    fun startRfcommServer() {
        stopServerOnly()
        val bt = adapter ?: return
        if (!PermissionHelper.hasBluetoothPermission(context) || !bt.isEnabled) return

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = bt.listenUsingRfcommWithServiceRecord(SERVICE_NAME, KODANIMA_BT_UUID)
                serverSocket = ss
                onStatusMessage("Servidor Bluetooth pronto! Aguardando conexão RFCOMM...")
                while (isActive) {
                    val socket = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    } ?: break
                    val remoteName = try { socket.remoteDevice?.name ?: "Amigo BT" } catch (_: SecurityException) { "Amigo BT" }
                    onIncomingClientConnected(
                        socket.inputStream,
                        socket.outputStream,
                        remoteName
                    ) {
                        try { socket.close() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "BT RFCOMM server warning: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToHostDevice(address: String) {
        connectJob?.cancel()
        val bt = adapter ?: run {
            onStatusMessage("Bluetooth não disponível.")
            return
        }
        if (!PermissionHelper.hasBluetoothPermission(context) || !bt.isEnabled) {
            onStatusMessage("Ative o Bluetooth e conceda a permissão.")
            return
        }
        connectJob = scope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                try { if (bt.isDiscovering) bt.cancelDiscovery() } catch (_: Exception) {}
                val device = bt.getRemoteDevice(address)
                socket = try {
                    device.createRfcommSocketToServiceRecord(KODANIMA_BT_UUID).apply { connect() }
                } catch (primaryErr: Exception) {
                    try { socket?.close() } catch (_: Exception) {}
                    // Fallback via RFCOMM channel 1 for Android OEM stacks with strict SDP caching
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val fallbackSock = m.invoke(device, 1) as BluetoothSocket
                    fallbackSock.connect()
                    fallbackSock
                }
                val remoteName = try { device.name ?: address } catch (_: SecurityException) { address }
                onClientConnectedToHost(
                    socket.inputStream,
                    socket.outputStream,
                    remoteName
                ) {
                    try { socket.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                try { socket?.close() } catch (_: Exception) {}
                onStatusMessage("Falha ao conectar Bluetooth: confira se a sala Bluetooth está aberta no outro celular.")
            }
        }
    }

    fun stopServerOnly() {
        acceptJob?.cancel()
        acceptJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    fun stopAll() {
        stopServerOnly()
        connectJob?.cancel()
        connectJob = null
        if (receiverRegistered) {
            try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }
}
