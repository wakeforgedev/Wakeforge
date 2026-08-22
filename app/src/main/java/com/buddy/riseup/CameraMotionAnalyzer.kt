package com.buddy.riseup

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * A deliberately lightweight motion detector: no ML model, no pose
 * estimation, no dependency beyond CameraX itself. It compares the average
 * luminance (brightness) of the camera's Y-plane between consecutive
 * frames — a big enough frame-to-frame brightness swing means something in
 * frame moved. That's a real, working signal for "is a person actually
 * moving in front of the camera," which is exactly what the mission needs
 * to confirm — it's just not trying to verify *what* the movement is
 * (a push-up specifically), which is the honest tradeoff for keeping this
 * buildable and testable without a heavier ML pipeline. Upgrading to real
 * pose-based rep counting (like Early's push-up check) is a drop-in
 * replacement for this one class later — nothing else in the mission flow
 * needs to change.
 *
 * Every frame is discarded immediately after its brightness is measured —
 * nothing is stored, copied, or written anywhere.
 */
class CameraMotionAnalyzer(
    private val onMotionSample: (isActive: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private var previousLuma: Double? = null

    override fun analyze(image: ImageProxy) {
        try {
            val luma = averageLuma(image)
            val previous = previousLuma
            previousLuma = luma
            if (previous != null) {
                val delta = abs(luma - previous)
                onMotionSample(delta > MOTION_THRESHOLD)
            }
        } finally {
            // Always close — CameraX stalls the analyzer pipeline otherwise.
            image.close()
        }
    }

    private fun averageLuma(image: ImageProxy): Double {
        // Plane 0 of YUV_420_888 (CameraX's default ImageAnalysis format) is the
        // luminance plane — sampling it is enough for brightness-change motion
        // detection without needing the color planes at all.
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        var sum = 0L
        var count = 0
        var i = 0
        while (i < data.size) {
            sum += (data[i].toInt() and 0xFF)
            count++
            i += SAMPLE_STRIDE // sample sparsely — plenty accurate for this, much cheaper than every byte
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    companion object {
        private const val MOTION_THRESHOLD = 18.0
        private const val SAMPLE_STRIDE = 8
    }
}
