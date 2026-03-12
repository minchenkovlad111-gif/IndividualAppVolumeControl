package com.duda.appvolumecontrol.core

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object BluetoothInfoUtils {
    fun fetchA2dpCodecLabel(
        context: Context,
        address: String,
        callback: (String) -> Unit
    ) {
        if (address.isBlank()) {
            callback("Codec: unknown (no address)")
            return
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callback("Codec: unknown (Bluetooth permission denied)")
            return
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: run {
            callback("Codec: unknown (Bluetooth adapter unavailable)")
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: run {
            callback("Codec: unknown (device not found)")
            return
        }
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val a2dp = proxy as? BluetoothA2dp
                val label = if (a2dp == null) {
                    "Codec: unknown (A2DP unavailable)"
                } else {
                    resolveCodecLabel(a2dp, device)
                }
                callback(label)
                adapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {
                callback("Codec: unknown (A2DP disconnected)")
            }
        }
        val ok = adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
        if (!ok) {
            callback("Codec: unknown (A2DP proxy failed)")
        }
    }

    private fun resolveCodecLabel(a2dp: BluetoothA2dp, device: android.bluetooth.BluetoothDevice): String {
        val status = runCatching {
            val method = a2dp.javaClass.getDeclaredMethod("getCodecStatus", device.javaClass)
            method.isAccessible = true
            method.invoke(a2dp, device)
        }.getOrElse {
            runCatching {
                val method = a2dp.javaClass.getDeclaredMethod("getCodecStatus")
                method.isAccessible = true
                method.invoke(a2dp)
            }.getOrNull()
        }
        val config = runCatching {
            val method = status?.javaClass?.getDeclaredMethod("getCodecConfig")
            method?.isAccessible = true
            method?.invoke(status)
        }.getOrNull()
        val codecType = runCatching {
            val method = config?.javaClass?.getDeclaredMethod("getCodecType")
            method?.isAccessible = true
            method?.invoke(config) as? Int
        }.getOrNull()
        val label = codecType?.let { codecName(it) } ?: "Unknown"
        return "Codec: $label"
    }

    private fun codecName(type: Int): String {
        val mapping = listOf(
            "SOURCE_CODEC_TYPE_SBC" to "SBC",
            "SOURCE_CODEC_TYPE_AAC" to "AAC",
            "SOURCE_CODEC_TYPE_APTX" to "aptX",
            "SOURCE_CODEC_TYPE_APTX_HD" to "aptX HD",
            "SOURCE_CODEC_TYPE_LDAC" to "LDAC",
            "SOURCE_CODEC_TYPE_LC3" to "LC3",
            "SOURCE_CODEC_TYPE_OPUS" to "Opus",
            "SOURCE_CODEC_TYPE_APTX_ADAPTIVE" to "aptX Adaptive",
            "SOURCE_CODEC_TYPE_APTX_TWSP" to "aptX TWS+",
        )
        for ((fieldName, label) in mapping) {
            val value = runCatching {
                val field = Class.forName("android.bluetooth.BluetoothCodecConfig")
                    .getDeclaredField(fieldName)
                field.isAccessible = true
                field.getInt(null)
            }.getOrNull()
            if (value != null && value == type) {
                return label
            }
        }
        return "Type $type"
    }
}
