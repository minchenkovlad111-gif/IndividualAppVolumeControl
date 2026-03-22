package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.media.AudioDeviceInfo

object OutputLabelUtils {
    fun shortType(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "SCO"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wire"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wire"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Spk"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Ear"
            else -> "Out"
        }
    }
}
