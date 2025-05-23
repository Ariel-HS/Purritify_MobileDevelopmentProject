package com.example.purritify.audio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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

        if (hasBluetoothPermission && btEnabled) {
            try {
                val pairedDevices = btAdapter!!.bondedDevices ?: emptySet()
                Log.d(TAG, "pairedDevices number: ${pairedDevices.size}")

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
            // Add a dummy Bluetooth device entry if we know Bluetooth is enabled but we don't have permission
            devices.add(AudioDevice(
                id = "bluetooth_permission_needed",
                name = context.getString(R.string.bluetooth_permission_needed),
                type = AudioDeviceType.OTHER,
                isConnected = false,
                isActive = false
            ))
        }

        // Only set default active device if none is currently active
        if (_activeDevice.value == null && devices.isNotEmpty()) {
            // Set the first device as active by default
            devices[0].isActive = true
            _activeDevice.postValue(devices[0])
        }

        _audioDevices.postValue(devices)
    }

    private fun isBluetoothDeviceConnected(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermission) return false

        try {
            // For simplicity, assume paired devices are connected.
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
            when (device.type) {
                AudioDeviceType.INTERNAL_SPEAKER -> {
                    audioManager.isSpeakerphoneOn = true

                    if (audioManager.isBluetoothScoOn) {
                        audioManager.isBluetoothScoOn = false
                        audioManager.stopBluetoothSco()
                    }

                    audioManager.mode = AudioManager.MODE_NORMAL

                    Log.d(TAG, "Audio routed to internal speaker")
                }

                AudioDeviceType.WIRED_HEADSET -> {
                    audioManager.isSpeakerphoneOn = false

                    if (audioManager.isBluetoothScoOn) {
                        audioManager.isBluetoothScoOn = false
                        audioManager.stopBluetoothSco()
                    }

                    audioManager.mode = AudioManager.MODE_NORMAL

                    audioManager.setWiredHeadsetOn(true)

                    requestAudioFocus()

                    Log.d(TAG, "Audio routed to wired headset")
                }

                AudioDeviceType.BLUETOOTH_HEADSET -> {
                    if (!audioManager.isBluetoothScoOn) {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }

                    audioManager.isSpeakerphoneOn = false

                    Log.d(TAG, "Audio routed to Bluetooth headset (SCO)")
                }

                AudioDeviceType.BLUETOOTH_A2DP -> {
                    audioManager.isSpeakerphoneOn = false

                    if (audioManager.isBluetoothScoOn) {
                        audioManager.isBluetoothScoOn = false
                        audioManager.stopBluetoothSco()
                    }

                    audioManager.mode = AudioManager.MODE_NORMAL

                    requestAudioFocus()

                    Log.d(TAG, "Audio routed to Bluetooth A2DP device")
                }

                else -> {
                    audioManager.isSpeakerphoneOn = true
                    audioManager.mode = AudioManager.MODE_NORMAL
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
            .setOnAudioFocusChangeListener {
                // Handle audio focus changes
            }
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
            audioManager.isSpeakerphoneOn = false

            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }

            audioManager.mode = AudioManager.MODE_NORMAL
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up audio routing", e)
        }
    }

}
