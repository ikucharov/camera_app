package com.camera.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.*
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.camera.app.utils.Constants.Companion.REQUEST_CAMERA_PERMISSION
import com.camera.app.utils.gone
import com.camera.app.utils.show
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.lang.IllegalArgumentException

class MainActivity : AppCompatActivity() {

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private lateinit var cameraDevice: CameraDevice

    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private var isCaptured = false

    private val cameraManager: CameraManager by lazy {
        this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (camera != null) {
                cameraDevice = camera
                previewSession()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            this@MainActivity.finish()
        }

    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playVideo.setOnClickListener {
            if (isCaptured) {
                isCaptured = false
                stopChronometer()
            } else {
                isCaptured = true
                startChronometer()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    fun checkCameraPermission() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
            Log.d(TAG, "App has camera permission")
            connectCamera()
        } else {
            EasyPermissions.requestPermissions(
                this, getString(R.string.permission_request),
                REQUEST_CAMERA_PERMISSION, Manifest.permission.CAMERA
            )
        }
    }

    override fun onResume() {
        super.onResume()

        startBackgroundThread()

        if (previewTextureView.isAvailable) {
            openCamera()
        } else previewTextureView.surfaceTextureListener = surfaceListener
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun openCamera() {
        checkCameraPermission()
    }

    private fun closeCamera() {
        if (this::captureSession.isInitialized) {
            captureSession.close()
        }
        if (this::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
    }

    private fun previewSession() {
        val surfaceTexture = previewTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGH)

        val surface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "creating capture session failed!!")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    if (session != null)
                        captureSession = session
                    captureRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                }

            },
            null
        )
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera 2 api").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return when (key) {
            CameraCharacteristics.LENS_FACING -> characteristics.get(key)
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key)
            else -> throw IllegalArgumentException("Key not recognized")
        }
    }

    private fun cameraId(lens: Int): String {
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            deviceId = cameraIdList.filter {
                lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
        return deviceId[0]
    }

    @SuppressLint("MissingPermission")
    private fun connectCamera() {
        val deviceId = cameraId(CameraCharacteristics.LENS_FACING_BACK)
        Log.d(TAG, "deviceId: $deviceId")

        try {
            cameraManager.openCamera(deviceId, deviceStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            Log.e(TAG, "Open camera device interrupted opened")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun startChronometer() {
        playVideo.setImageResource(R.drawable.ic_pause)
        chronometer.show()
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
        chronometer.start()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun stopChronometer() {
        playVideo.setImageResource(R.drawable.ic_play_button)
        chronometer.setTextColor(resources.getColor(android.R.color.white, null))
        chronometer.stop()
        chronometer.gone()
    }

    companion object {
        const val TAG = "MainActivity"
        const val MAX_PREVIEW_WIDTH = 1024
        const val MAX_PREVIEW_HEIGH = 1024
    }
}