package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Play-Policy-compliant runtime permission helper for Kodanima 2D.
 * Requests ONLY strictly needed permissions:
 * - POST_NOTIFICATIONS (API 33+) to keep the Local Server notification alive & provide "Encerrar Servidor"
 * - NEARBY_WIFI_DEVICES (API 33+) for Wi-Fi Direct proximity pairing without location
 * - BLUETOOTH_SCAN / BLUETOOTH_CONNECT / BLUETOOTH_ADVERTISE (API 31+) for offline Bluetooth Coop
 * - ACCESS_FINE_LOCATION (API <= 32 only) where Android requires it for Wi-Fi Direct / BT discovery
 * Media/files use Android Photo Picker & SAF (zero storage permissions needed).
 */
object PermissionHelper {

    fun getInitialRuntimePermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        return perms.toTypedArray()
    }

    fun getWifiDirectPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun hasWifiDirectPermission(context: Context): Boolean {
        val required = getWifiDirectPermissions()
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        val required = getBluetoothPermissions()
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasConnectedDeviceFgsPrerequisite(context: Context): Boolean {
        return hasWifiDirectPermission(context) || hasBluetoothPermission(context)
    }

    fun hasMissingInitialPermissions(context: Context): Boolean {
        return getInitialRuntimePermissions().any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
}
