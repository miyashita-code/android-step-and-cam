package com.rementia.walkingdetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresPermission
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FakeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.STARTED
    }
    override val lifecycle: Lifecycle
        get() = registry
}

class CameraIntervalModule private constructor(
    private val context: Context
) {
    interface Callback {
        fun onImagesReady(frontImage: ByteArray?, backImage: ByteArray?)
        fun onError(message: String, exception: ImageCaptureException)
    }

    companion object {
        private var sInstance: CameraIntervalModule? = null
        fun getInstance(context: Context): CameraIntervalModule {
            if (sInstance == null) {
                sInstance = CameraIntervalModule(context.applicationContext)
            }
            return sInstance!!
        }

        private const val TAG = "CameraIntervalModule"

        private const val INTERVAL_MS = 500L
    }

    private var callback: Callback? = null

    // 撮影ループ用 Thread/Handler
    private val handlerThread = HandlerThread("CamIntervalThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var isRunning = false

    // 前回撮影バッファ
    private var prevFrontData: ByteArray? = null
    private var prevBackData: ByteArray? = null

    // 今回撮影バッファ
    private var currentFrontData: ByteArray? = null
    private var currentBackData: ByteArray? = null

    // ダミーLifeCycleOwner
    private val fakeLifecycleOwner = FakeLifecycleOwner()

    fun setCallback(cb: Callback?) {
        callback = cb
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.CAMERA)
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Already started.")
            return
        }
        isRunning = true
        Log.d(TAG, "start capturing loop")

        prevFrontData = null
        prevBackData = null
        currentFrontData = null
        currentBackData = null

        scheduleNext()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "stop capturing loop.")
    }

    private fun scheduleNext() {
        if (!isRunning) return
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            onIntervalTick()
        }, INTERVAL_MS)
    }

    private fun onIntervalTick() {
        // 1) 前回分あれば callback
        if (prevFrontData != null || prevBackData != null) {
            // ★ここは CamIntervalThread, callback先でUI操作しないために runOnUiThread が必要
            //   → ただし callback先でUI操作するなら callback内で runOnUiThread すればOK
            callback?.onImagesReady(prevFrontData, prevBackData)

            prevFrontData = null
            prevBackData = null
        }

        // 2) front -> back
        captureOneShot(CameraSelector.LENS_FACING_FRONT) { frontData ->
            currentFrontData = frontData

            captureOneShot(CameraSelector.LENS_FACING_BACK) { backData ->
                currentBackData = backData

                // 3) current => prev
                prevFrontData = currentFrontData
                prevBackData = currentBackData
                currentFrontData = null
                currentBackData = null

                // 4) next
                scheduleNext()
            }
        }
    }

    private fun captureOneShot(lensFacing: Int, done: (ByteArray?) -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1024, 768)) // 例: これくらいに制限
                .build()


            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.bindToLifecycle(fakeLifecycleOwner, selector, imageCapture)

            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = imageProxyToByteArray(image)
                        image.close()

                        // unbindAll() は UIスレッドで
                        Handler(Looper.getMainLooper()).post {
                            provider.unbindAll()
                        }
                        done(bytes)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "captureOneShot error lens=$lensFacing: $exc")

                        Handler(Looper.getMainLooper()).post {
                            provider.unbindAll()
                        }
                        callback?.onError("lens=$lensFacing", exc)
                        done(null)
                    }
                }
            )
        }, ContextCompat.getMainExecutor(context))
    }

    private fun imageProxyToByteArray(imageProxy: ImageProxy): ByteArray {
        val plane = imageProxy.planes[0].buffer
        val bytes = ByteArray(plane.remaining())
        plane.get(bytes)
        return bytes
    }
}


