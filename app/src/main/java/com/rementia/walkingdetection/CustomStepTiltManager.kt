package com.rementia.walkingdetection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * CustomStepTiltManager:
 *  - Step Detector (TYPE_STEP_DETECTOR)
 *  - Tilt (type=22)
 *
 * Tilt 検出後は 2 秒間だけ `tiltActive = true` としてステップを無視し、
 * 2 秒経過後に自動で `tiltActive = false` に戻す。
 */
class CustomStepTiltManager private constructor(context: Context) {

    interface Callback {
        /**
         * Step イベントが来るたびに呼ばれる (Tilt が ON の場合は無視されるが、イベント自体は通知)
         * @param stepEventIndex   純粋に到着した step イベント回数
         * @param tiltActive       現在 tilt が ON かどうか
         * @param accepted         この step をカウントしたか (tiltActive=false の時だけカウント)
         * @param acceptedCount    実際にカウントされたステップ数の累計
         */
        fun onStepEvent(stepEventIndex: Int, tiltActive: Boolean, accepted: Boolean, acceptedCount: Int)

        /**
         * Tilt 検出時 (毎回呼ばれる)
         */
        fun onTiltDetected()
    }

    companion object {
        private var sInstance: CustomStepTiltManager? = null

        fun getInstance(context: Context): CustomStepTiltManager {
            if (sInstance == null) {
                sInstance = CustomStepTiltManager(context.applicationContext)
            }
            return sInstance!!
        }

        private const val TAG = "CustomStepTiltManager"

        // Tilt Detector の定数 (22)
        private const val TILT_TYPE_CONST = 22

        // Tilt を ON にしてから OFF に戻すまでの時間 (ms)
        private const val TILT_ACTIVE_DURATION_MS = 2000L
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Step Detector
    private val stepDetectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    // Tilt (type=22)
    private val tiltSensor: Sensor? by lazy {
        sensorManager.getSensorList(Sensor.TYPE_ALL).find { it.type == TILT_TYPE_CONST }
    }

    private var callback: Callback? = null

    // 状態フラグ
    private var isStepActive = false
    private var isTiltActive = false  // 現在 tilt が ON かどうか

    // ステップイベントの総数 (tiltActive の有無に関わらず)
    private var totalStepEvents = 0

    // 実際にカウントされたステップ (tiltActive=false 時だけカウント)
    private var acceptedStepCount = 0

    // Tilt を OFF に戻すための Handler / Runnable
    private val tiltHandler = Handler(Looper.getMainLooper())
    private val tiltResetRunnable = Runnable {
        isTiltActive = false
        Log.i(TAG, "Tilt auto-reset to false after $TILT_ACTIVE_DURATION_MS ms")
    }

    init {
        Log.d(TAG, "CustomStepTiltManager init.")
    }

    fun setCallback(cb: Callback?) {
        callback = cb
    }

    /**
     * Custom Step Detector を開始
     */
    fun startCustomStep() {
        if (isStepActive) {
            Log.d(TAG, "CustomStep already active.")
            return
        }
        if (stepDetectorSensor == null) {
            Log.w(TAG, "No stepDetector sensor found.")
            return
        }
        // カウント類リセット
        totalStepEvents = 0
        acceptedStepCount = 0

        sensorManager.registerListener(sensorListener, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isStepActive = true
        Log.d(TAG, "startCustomStepDetector")
    }

    fun stopCustomStep() {
        if (!isStepActive) return
        stepDetectorSensor?.also {
            sensorManager.unregisterListener(sensorListener, it)
        }
        isStepActive = false
        Log.d(TAG, "stopCustomStepDetector")
    }

    /**
     * Tilt 検出開始
     */
    fun startTilt() {
        if (isTiltActive) {
            Log.d(TAG, "Tilt already active. (But we can keep re-trigger if sensor events come)")
        }
        if (tiltSensor == null) {
            Log.w(TAG, "No tiltSensor(22) found.")
            return
        }
        sensorManager.registerListener(sensorListener, tiltSensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "startTilt")
    }

    fun stopTilt() {
        tiltSensor?.also {
            sensorManager.unregisterListener(sensorListener, it)
        }
        // OFF
        isTiltActive = false
        Log.d(TAG, "stopTilt => tiltActive=false forcibly")
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> {
                    if (event.values.isNotEmpty() && event.values[0] == 1.0f) {
                        totalStepEvents++
                        val nowTilt = isTiltActive
                        val accepted = !nowTilt
                        if (accepted) {
                            acceptedStepCount++
                        }
                        Log.i(TAG, "Step event#$totalStepEvents, tilt=$nowTilt => accepted=$accepted => acceptedStepCount=$acceptedStepCount")
                        callback?.onStepEvent(totalStepEvents, nowTilt, accepted, acceptedStepCount)
                    }
                }
                TILT_TYPE_CONST -> {
                    if (event.values.isNotEmpty() && event.values[0] == 1.0f) {
                        Log.i(TAG, "Tilt DETECTED => tiltActive for $TILT_ACTIVE_DURATION_MS ms")
                        callback?.onTiltDetected()

                        // TiltをON => 2秒後にOFF
                        isTiltActive = true
                        // 既存のリセットを削除 (連続で tilt が来る場合に対応)
                        tiltHandler.removeCallbacks(tiltResetRunnable)
                        tiltHandler.postDelayed(tiltResetRunnable, TILT_ACTIVE_DURATION_MS)
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
