package com.camera.app.utils

import android.Manifest

/**
 * Utility class for constants
 */
class Constants {

    companion object {

        val REQUEST_VIDEO_PERMISSIONS = 1
        val VIDEO_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
