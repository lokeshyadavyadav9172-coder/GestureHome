package com.gesturehome.devices

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager


/**
 * A single controllable thing in the home.
 * The demo ships with phone-local devices (torch, screen, volume, silent mode) so it runs
 * with zero extra hardware; swap in a Google Home / smart-plug HTTP call inside [apply].
 */
data class Device(
    val id: String,
    val name: String,
    val room: String,
    var isOn: Boolean = false,
    var level: Int = 50, // 0..100 for dimmable devices
    val dimmable: Boolean = false,
)

interface DeviceBackend {
    fun apply(device: Device)
}

/**
 * Controls the phone itself — the "my device" backend.
 * Torch and volume work out of the box; screen brightness needs WRITE_SETTINGS,
 * so it falls back to per-window brightness handled in the Activity.
 */
class PhoneDeviceBackend(
    private val context: Context,
    private val onWindowBrightness: (Float) -> Unit = {},
) : DeviceBackend {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun apply(device: Device) {
        when (device.id) {
            "torch" -> setTorch(device.isOn)
            "screen" -> onWindowBrightness(if (device.isOn) device.level / 100f else 0.05f)
            "volume" -> setVolume(if (device.isOn) device.level else 0)
            "silent" -> audioManager.ringerMode =
                if (device.isOn) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        }
    }

    private fun setTorch(on: Boolean) {
        runCatching {
            val id = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.setTorchMode(id, on)
        }
    }

    private fun setVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (max * percent / 100).coerceIn(0, max),
            0
        )
    }
}
