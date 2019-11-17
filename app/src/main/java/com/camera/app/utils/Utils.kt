package com.camera.app.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Utils {

    // https://github.com/permissions-dispatcher/PermissionsDispatcher/issues/107
    fun hasPermission(context: Context, permission: String): Boolean {
        return try {
            PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                context,
                permission
            )
        } catch (e: Throwable) {
            false
        }
    }
}
