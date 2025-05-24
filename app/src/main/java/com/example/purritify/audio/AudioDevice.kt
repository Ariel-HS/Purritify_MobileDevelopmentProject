package com.example.purritify.audio

data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    var isConnected: Boolean = true,
    var isActive: Boolean = false
)

enum class AudioDeviceType {
    INTERNAL_SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH_A2DP,
    BLUETOOTH_HEADSET,
    OTHER
}
