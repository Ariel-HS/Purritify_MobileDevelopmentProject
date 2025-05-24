package com.example.purritify.audio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purritify.R

class AudioDeviceManager(private val context: Context) {

    private val TAG = "AudioDeviceManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val bluetoothAdapter: BluetoothAdapter? = run {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        bluetoothManager?.adapter
    }

    private val _audioDevices = MutableLiveData<List<AudioDevice>>(emptyList())
    val audioDevices: LiveData<List<AudioDevice>> = _audioDevices

    private val _activeDevice = MutableLiveData<AudioDevice?>(null)
    val activeDevice: LiveData<AudioDevice?> = _activeDevice

    private val _deviceConnectionError = MutableLiveData<String?>(null)
    val deviceConnectionError: LiveData<String?> = _deviceConnectionError

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var isRegistered = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val hasBluetoothPermission: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                AudioManager.ACTION_HEADSET_PLUG -> {
                    refreshAudioDevices()
                    checkActiveDeviceConnection()
                }
            }
        }
    }

    init {
        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                refreshAudioDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                refreshAudioDevices()
                checkActiveDeviceConnection()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(bluetoothReceiver, bluetoothFilter)
        isRegistered = true

        refreshAudioDevices()
    }

    fun refreshAudioDevices() {
        val devices = mutableListOf<AudioDevice>()

        val currentActiveId = _activeDevice.value?.id
        Log.d(TAG, "Current active device ID: $currentActiveId")

        devices.add(AudioDevice(
            id = "internal_speaker",
            name = context.getString(R.string.phone_speaker),
            type = AudioDeviceType.INTERNAL_SPEAKER,
            isConnected = true,
            isActive = currentActiveId == "internal_speaker" || currentActiveId == null
        ))

        val hasWiredHeadset =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }

        if (hasWiredHeadset) {
            devices.add(AudioDevice(
                id = "wired_headset",
                name = context.getString(R.string.wired_headset),
                type = AudioDeviceType.WIRED_HEADSET,
                isConnected = true,
                isActive = currentActiveId == "wired_headset"
            ))
        }

        val btAdapter = bluetoothAdapter
        val btEnabled = btAdapter != null && btAdapter.isEnabled
        Log.d(TAG, "is bluetooth enabled: $btEnabled")

        if (hasBluetoothPermission && btEnabled) {
            try {
                val pairedDevices = btAdapter!!.bondedDevices ?: emptySet()
                Log.d(TAG, "number of pairedDevices: ${pairedDevices.size}")

                for (device in pairedDevices) {
                    try {
                        val deviceName = try {
                            device.name ?: context.getString(R.string.bluetooth_headset)
                        } catch (e: SecurityException) {
                            context.getString(R.string.bluetooth_headset)
                        }

                        val majorDeviceClass = device.bluetoothClass.majorDeviceClass
                        val deviceClass = device.bluetoothClass.deviceClass

                        Log.d(TAG, "Bluetooth device: $deviceName")
                        Log.d(TAG, "Bluetooth major device class: $majorDeviceClass")
                        Log.d(TAG, "Bluetooth device class: $deviceClass")

                        val isAudioDevice =
                            (majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO) ||
                            (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) ||
                            (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) ||
                            (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO) ||
                            (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER)

                        if (isAudioDevice) {
                            val isConnected = isBluetoothDeviceConnected(device)
                            if (isConnected) {
                                val type = when {
                                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> AudioDeviceType.BLUETOOTH_HEADSET
                                    else -> AudioDeviceType.BLUETOOTH_A2DP
                                }

                                devices.add(AudioDevice(
                                    id = device.address,
                                    name = deviceName,
                                    type = type,
                                    isConnected = true,
                                    isActive = _activeDevice.value?.id == device.address
                                ))
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception accessing Bluetooth device: ${e.message}")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception accessing Bluetooth devices: ${e.message}")
            }
        } else if (!hasBluetoothPermission && btEnabled) {
            devices.add(AudioDevice(
                id = "bluetooth_permission_needed",
                name = context.getString(R.string.bluetooth_permission_needed),
                type = AudioDeviceType.OTHER,
                isConnected = false,
                isActive = false
            ))
        }

        if (_activeDevice.value == null && devices.isNotEmpty()) {
            devices[0].isActive = true
            _activeDevice.postValue(devices[0])
        }

        _audioDevices.postValue(devices)
    }

    private fun isBluetoothDeviceConnected(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermission) return false

        try {
            return device.bondState == BluetoothDevice.BOND_BONDED
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception checking Bluetooth connection: ${e.message}")
            return false
        }
    }

    fun selectDevice(device: AudioDevice) {
        val currentDevices = _audioDevices.value ?: return

        if (!device.isConnected) {
            if (device.id == "bluetooth_permission_needed") {
                _deviceConnectionError.postValue(context.getString(R.string.bluetooth_permissions_required))
            } else {
                _deviceConnectionError.postValue(context.getString(R.string.device_connection_failed))
            }
            return
        }

        _activeDevice.postValue(device)

        val updatedDevices = currentDevices.map {
            it.copy(isActive = it.id == device.id)
        }
        _audioDevices.postValue(updatedDevices)

        routeAudioToDevice(device)
    }

    private fun routeAudioToDevice(device: AudioDevice) {
        Log.d(TAG, "Attempting to route audio to: ${device.name} (${device.type})")

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    Log.d(TAG, "Stopped existing Bluetooth SCO connection")
                }
            }

            audioManager.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            } else {
                audioManager.clearCommunicationDevice()
            }

            when (device.type) {
                AudioDeviceType.INTERNAL_SPEAKER -> {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val speakerDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

                        speakerDevice?.let {
                            val success = audioManager.setCommunicationDevice(it)
                            Log.d(TAG, "Setting speaker as communication device: $success")
                        } ?: Log.e(TAG, "No built-in speaker found")
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }

                    requestAudioFocus()
                    Log.d(TAG, "Audio forcefully routed to internal speaker")
                }

                AudioDeviceType.WIRED_HEADSET -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        audioManager.clearCommunicationDevice()

                        val wiredHeadsetDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull {
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            }

                        wiredHeadsetDevice?.let {
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            val success = audioManager.setCommunicationDevice(it)
                            Log.d(TAG, "Setting wired headset as communication device: $success")
                        } ?: Log.e(TAG, "No wired headset found")
                    } else {
                        @Suppress("DEPRECATION")
                        if (audioManager.isBluetoothScoOn) {
                            @Suppress("DEPRECATION")
                            audioManager.stopBluetoothSco()
                            audioManager.isBluetoothScoOn = false
                        }

                        audioManager.mode = AudioManager.MODE_NORMAL
                    }

                    requestAudioFocus()
                    Log.d(TAG, "Audio routed to wired headset")
                }

                AudioDeviceType.BLUETOOTH_HEADSET -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val bluetoothScoDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

                        if (bluetoothScoDevice != null) {
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            val success = audioManager.setCommunicationDevice(bluetoothScoDevice)
                            Log.d(TAG, "Setting Bluetooth SCO as communication device: $success")
                        } else {
                            Log.e(TAG, "No Bluetooth SCO device found")
                            val a2dpDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

                            a2dpDevice?.let {
                                val success = audioManager.setCommunicationDevice(it)
                                Log.d(TAG, "Setting Bluetooth A2DP as fallback: $success")
                            }
                        }
                    } else {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        @Suppress("DEPRECATION")
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                        Log.d(TAG, "Started Bluetooth SCO connection (legacy)")
                    }

                    requestAudioFocus()
                    Log.d(TAG, "Audio routed to Bluetooth headset")
                }

                AudioDeviceType.BLUETOOTH_A2DP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val bluetoothA2dpDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

                        if (bluetoothA2dpDevice != null) {
                            val success = audioManager.setCommunicationDevice(bluetoothA2dpDevice)
                            Log.d(TAG, "Setting Bluetooth A2DP as communication device: $success")
                        } else {
                            Log.e(TAG, "No Bluetooth A2DP device found")
                        }
                    }

                    requestAudioFocus()
                    Log.d(TAG, "Audio routed to Bluetooth A2DP device")
                }

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val speakerDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

                        speakerDevice?.let {
                            audioManager.setCommunicationDevice(it)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                    Log.d(TAG, "Audio defaulting to speaker (unknown device type)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio: ${e.message}", e)
            _deviceConnectionError.postValue("Failed to route audio: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener {}
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus granted")
        } else {
            Log.d(TAG, "Audio focus request failed")
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
    }

    private fun checkActiveDeviceConnection() {
        val activeDevice = _activeDevice.value ?: return
        val devices = _audioDevices.value ?: return

        val deviceStillConnected = devices.any { it.id == activeDevice.id && it.isConnected }

        if (!deviceStillConnected) {
            val internalSpeaker = devices.firstOrNull { it.type == AudioDeviceType.INTERNAL_SPEAKER }
            if (internalSpeaker != null) {
                _deviceConnectionError.postValue(context.getString(R.string.device_connection_lost))
                selectDevice(internalSpeaker)
            }
        }
    }

    fun clearConnectionError() {
        _deviceConnectionError.postValue(null)
    }

    fun cleanup() {
        audioDeviceCallback?.let {
            audioManager.unregisterAudioDeviceCallback(it)
        }

        if (isRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
                isRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }

            audioManager.mode = AudioManager.MODE_NORMAL
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up audio routing", e)
        }
    }

}
