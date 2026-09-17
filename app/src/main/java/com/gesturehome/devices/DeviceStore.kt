package com.gesturehome.devices

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.gesturehome.gesture.GestureEvent

/** Action a gesture is bound to. */
sealed interface GestureAction {
    data class Toggle(val deviceId: String) : GestureAction
    data class Step(val deviceId: String, val delta: Int) : GestureAction
    data object AllOff : GestureAction
}

/**
 * In-memory state for the prototype: device list + the user's gesture bindings.
 * Bindings are per-user on purpose — motor ability varies, so nothing is hard-coded.
 * Persist with DataStore/Room when you move past the hackathon demo.
 */
class DeviceStore(private val backend: DeviceBackend) {

    val devices = mutableStateListOf(
        Device("torch", "Torch light", "This device", dimmable = false),
        Device("screen", "Screen brightness", "This device", level = 60, dimmable = true),
        Device("volume", "Media volume", "This device", level = 40, dimmable = true),
        Device("silent", "Silent mode", "This device"),
    )

    val bindings = mutableStateOf(
        mapOf<GestureEvent, GestureAction>(
            GestureEvent.OPEN_PALM to GestureAction.Toggle("torch"),
            GestureEvent.CLOSED_FIST to GestureAction.AllOff,
            GestureEvent.THUMB_UP to GestureAction.Step("volume", +15),
            GestureEvent.THUMB_DOWN to GestureAction.Step("volume", -15),
            GestureEvent.HEAD_TILT_LEFT to GestureAction.Step("screen", -15),
            GestureEvent.HEAD_TILT_RIGHT to GestureAction.Step("screen", +15),
            GestureEvent.LONG_BLINK to GestureAction.Toggle("silent"),
        )
    )

    val log = mutableStateListOf<String>()

    fun bind(event: GestureEvent, action: GestureAction) {
        bindings.value = bindings.value + (event to action)
    }

    /** Runs the action bound to a recognized gesture. Returns a human-readable result. */
    fun handle(event: GestureEvent): String {
        val action = bindings.value[event] ?: return "${event.label} — not assigned"
        val message = when (action) {
            is GestureAction.Toggle -> device(action.deviceId)?.let {
                update(it.copy(isOn = !it.isOn))
                "${event.label} → ${it.name} ${if (!it.isOn) "on" else "off"}"
            } ?: "Unknown device"

            is GestureAction.Step -> device(action.deviceId)?.let {
                val level = (it.level + action.delta).coerceIn(0, 100)
                update(it.copy(isOn = level > 0, level = level))
                "${event.label} → ${it.name} $level%"
            } ?: "Unknown device"

            GestureAction.AllOff -> {
                devices.toList().forEach { update(it.copy(isOn = false)) }
                "${event.label} → everything off"
            }
        }
        log.add(0, message)
        if (log.size > 20) log.removeAt(log.lastIndex)
        return message
    }

    private fun device(id: String) = devices.firstOrNull { it.id == id }

    private fun update(updated: Device) {
        val index = devices.indexOfFirst { it.id == updated.id }
        if (index >= 0) devices[index] = updated
        backend.apply(updated)
    }
}
