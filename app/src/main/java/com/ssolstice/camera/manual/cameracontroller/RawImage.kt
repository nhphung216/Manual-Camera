package com.ssolstice.camera.manual.cameracontroller

import android.hardware.camera2.DngCreator
import android.media.Image
import com.ssolstice.camera.manual.utils.ErrorLogger
import com.ssolstice.camera.manual.utils.Logger
import java.io.IOException
import java.io.OutputStream

/**
 * Wrapper class to store DngCreator and Image.
 */
class RawImage(private val dngCreator: DngCreator, private val image: Image) {
    /**
     * Writes the DNG file to the supplied output.
     */
    @Throws(IOException::class)
    fun writeImage(dngOutput: OutputStream) {
        Logger.d(TAG, "writeImage")
        try {
            dngCreator.writeImage(dngOutput, image)
        } catch (e: AssertionError) {
            // Một số thiết bị trả metadata thiếu -> IllegalArgumentException
            // Một số thiết bị OnePlus -> AssertionError
            // Một số thiết bị Samsung -> IllegalStateException
            Logger.e(TAG, "Failed to write DNG: " + e.message)
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "Failed to write DNG: " + e.message)
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "Failed to write DNG: " + e.message)
        } catch (e: Exception) {
            // Catch-all để tránh crash ngoài ý muốn
            Logger.e(TAG, "Unexpected error writing DNG: " + e.message)
        }
    }

    /**
     * Closes the image. Must be called to free up resources when no longer needed.
     * After calling this method, this object should not be used.
     */
    fun close() {
        Logger.d(TAG, "close")
        try {
            image.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error closing Image: " + e.message)
        }
        try {
            dngCreator.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error closing DngCreator: " + e.message)
        }
    }

    companion object {
        private const val TAG = "RawImage"
    }
}
