package com.gesturehome.gesture

/**
 * Every gesture the on-device models can emit.
 * Hand gestures come from MediaPipe GestureRecognizer, head/eye gestures from FaceLandmarker.
 */
enum class GestureEvent(val label: String) {
    OPEN_PALM("Open palm"),
    CLOSED_FIST("Closed fist"),
    THUMB_UP("Thumb up"),
    THUMB_DOWN("Thumb down"),
    VICTORY("Victory"),
    POINTING_UP("Pointing up"),
    HEAD_TILT_LEFT("Head tilt left"),
    HEAD_TILT_RIGHT("Head tilt right"),
    LONG_BLINK("Long blink");

    companion object {
        /** Maps MediaPipe gesture category names to our enum. */
        fun fromMediaPipe(name: String): GestureEvent? = when (name) {
            "Open_Palm" -> OPEN_PALM
            "Closed_Fist" -> CLOSED_FIST
            "Thumb_Up" -> THUMB_UP
            "Thumb_Down" -> THUMB_DOWN
            "Victory" -> VICTORY
            "Pointing_Up" -> POINTING_UP
            else -> null
        }
    }
}
