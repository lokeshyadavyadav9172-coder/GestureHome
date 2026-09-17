package com.gesturehome.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI

/**
 * Runs both on-device models on every camera frame and emits debounced gestures.
 *
 * Nothing leaves the phone: frames are converted in memory, inferred locally with
 * MediaPipe Tasks (TFLite under the hood) and dropped immediately after.
 */
class GestureAnalyzer(
    context: Context,
    private val minConfidence: Float = 0.6f,
    private val cooldownMs: Long = 1_200L,
    private val onGesture: (GestureEvent) -> Unit,
    private val onStatus: (String) -> Unit = {},
) : ImageAnalysis.Analyzer {

    private val recognizer: GestureRecognizer = GestureRecognizer.createFromOptions(
        context,
        GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("gesture_recognizer.task")
                    .setDelegate(Delegate.CPU) // switch to GPU on devices that support it
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .build()
    )

    private val faceLandmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .build()
    )

    private var lastEmitAt = 0L
    private var blinkFrames = 0

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toUprightBitmap() ?: return
            val mpImage = BitmapImageBuilder(bitmap).build()

            handGesture(recognizer.recognize(mpImage))?.let { emit(it); return }
            headOrEyeGesture(faceLandmarker.detect(mpImage))?.let { emit(it) }
        } catch (t: Throwable) {
            onStatus("Inference error: ${t.message}")
        } finally {
            image.close()
        }
    }

    private fun handGesture(result: GestureRecognizerResult): GestureEvent? {
        val category = result.gestures().firstOrNull()?.firstOrNull() ?: return null
        if (category.score() < minConfidence) return null
        return GestureEvent.fromMediaPipe(category.categoryName())
    }

    private fun headOrEyeGesture(result: FaceLandmarkerResult): GestureEvent? {
        // Blink: use blendshapes when available, they are far more stable than raw landmarks.
        val shapes = result.faceBlendshapes().orElse(null)?.firstOrNull()
        if (shapes != null) {
            val left = shapes.firstOrNull { it.categoryName() == "eyeBlinkLeft" }?.score() ?: 0f
            val right = shapes.firstOrNull { it.categoryName() == "eyeBlinkRight" }?.score() ?: 0f
            blinkFrames = if (left > 0.55f && right > 0.55f) blinkFrames + 1 else 0
            // ~0.5s of closed eyes at 15fps => deliberate long blink, not a natural one.
            if (blinkFrames >= 7) {
                blinkFrames = 0
                return GestureEvent.LONG_BLINK
            }
        }

        // Head tilt: roll angle between the two eye centres.
        val landmarks = result.faceLandmarks().firstOrNull() ?: return null
        if (landmarks.size < 264) return null
        val leftEye = landmarks[33]
        val rightEye = landmarks[263]
        val dy = (rightEye.y() - leftEye.y()).toDouble()
        val dx = (rightEye.x() - leftEye.x()).toDouble()
        if (hypot(dx, dy) < 1e-4) return null
        val roll = atan2(dy, dx) * 180.0 / PI
        return when {
            roll > 12 -> GestureEvent.HEAD_TILT_RIGHT
            roll < -12 -> GestureEvent.HEAD_TILT_LEFT
            else -> null
        }.also { if (it != null && abs(roll) > 45) return null } // ignore absurd angles
    }

    private fun emit(event: GestureEvent) {
        val now = System.currentTimeMillis()
        if (now - lastEmitAt < cooldownMs) return
        lastEmitAt = now
        onGesture(event)
    }

    fun close() {
        recognizer.close()
        faceLandmarker.close()
    }
}

/** CameraX gives rotated YUV/RGBA frames; MediaPipe wants an upright bitmap. */
private fun ImageProxy.toUprightBitmap(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    plane.buffer.rewind()
    bitmap.copyPixelsFromBuffer(plane.buffer)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
