package com.rementia.walkingdetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageCaptureException

/**
 * デモ用 MainActivity:
 *  - カメラPermissionのチェック → 許可されたら撮影モジュールのstart/stopトグル。
 *  - 5秒おきに撮れた front/back 画像を ImageView に表示。
 */
class MainActivity : AppCompatActivity(), CameraIntervalModule.Callback {

    private lateinit var buttonToggle: Button
    private lateinit var imageViewFront: ImageView
    private lateinit var imageViewBack: ImageView

    private lateinit var cameraIntervalModule: CameraIntervalModule

    // カメラが動作中かどうか
    private var isCameraRunning = false

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // レイアウト: activity_main.xml
        setContentView(R.layout.activity_main)

        buttonToggle = findViewById(R.id.btnToggleCamera)
        imageViewFront = findViewById(R.id.imageViewFront)
        imageViewBack  = findViewById(R.id.imageViewBack)

        // CameraIntervalModule
        cameraIntervalModule = CameraIntervalModule.getInstance(this)
        cameraIntervalModule.setCallback(this)

        // ボタン押下で start/stop トグル
        buttonToggle.setOnClickListener {
            if (isCameraRunning) {
                stopCameraInterval()
            } else {
                checkCameraPermissionAndStart()
            }
        }
    }

    // --------------------------------------------------
    // カメラ パーミッション
    // --------------------------------------------------
    private fun checkCameraPermissionAndStart() {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (result == PackageManager.PERMISSION_GRANTED) {
            startCameraInterval()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startCameraInterval()
            } else {
                Toast.makeText(this, "CAMERA permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --------------------------------------------------
    // start / stop
    // --------------------------------------------------
    private fun startCameraInterval() {
        cameraIntervalModule.start()
        isCameraRunning = true
        buttonToggle.text = "Stop"
        Toast.makeText(this, "Camera Interval START", Toast.LENGTH_SHORT).show()
    }

    private fun stopCameraInterval() {
        cameraIntervalModule.stop()
        isCameraRunning = false
        buttonToggle.text = "Start"
        Toast.makeText(this, "Camera Interval STOP", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraIntervalModule.stop()
    }

    // --------------------------------------------------
    // CameraIntervalModule.Callback 実装
    // --------------------------------------------------
    /**
     * front/back の2枚が撮れた際に呼ばれるコールバック。
     */
    override fun onImagesReady(frontImage: ByteArray?, backImage: ByteArray?) {
        // UI スレッドで反映
        runOnUiThread {
            // front
            if (frontImage != null) {
                val bmpFront = BitmapFactory.decodeByteArray(frontImage, 0, frontImage.size)
                imageViewFront.setImageBitmap(bmpFront)
            } else {
                imageViewFront.setImageResource(android.R.color.transparent)
            }

            // back
            if (backImage != null) {
                val bmpBack = BitmapFactory.decodeByteArray(backImage, 0, backImage.size)
                imageViewBack.setImageBitmap(bmpBack)
            } else {
                imageViewBack.setImageResource(android.R.color.transparent)
            }
        }
    }

    /**
     * 撮影時にエラーが発生した際に呼ばれる。
     */
    override fun onError(message: String, exception: ImageCaptureException) {
        runOnUiThread {
            Toast.makeText(this, "Camera Error: $message\n$exception", Toast.LENGTH_SHORT).show()
        }
    }
}
