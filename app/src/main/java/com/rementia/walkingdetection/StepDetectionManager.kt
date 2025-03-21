package com.rementia.walkingdetection


import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * StepDetectorManager:
 * TYPE_STEP_DETECTOR を使い、歩数（ステップ）を1歩ずつ検知。
 * コールバックで歩数変化を通知。
 */
class StepDetectorManager private constructor(context: Context) {

    interface OnStepListener {
        fun onStepDetected(stepCount: Int)
    }

    private val appContext: Context = context.applicationContext
    private val sensorManager: SensorManager
    private var stepDetectorSensor: Sensor? = null

    private var isDetecting = false
    private var stepCount = 0  // 1歩ごとに++するカウンタ

    private var onStepListener: OnStepListener? = null

    companion object {
        private var sInstance: StepDetectorManager? = null
        private const val TAG = "StepDetectorManager"

        fun getInstance(context: Context): StepDetectorManager {
            if (sInstance == null) {
                sInstance = StepDetectorManager(context)
            }
            return sInstance!!
        }
    }

    init {
        Log.d(TAG, "init StepDetectorManager")
        sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // センサー取得 (null の場合は未サポート)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetectorSensor == null) {
            Log.w(TAG, "TYPE_STEP_DETECTOR not supported on this device.")
        }
    }

    /**
     * コールバックをセット
     */
    fun setOnStepListener(listener: OnStepListener?) {
        onStepListener = listener
    }

    /**
     * センサー検知を開始
     */
    fun startStepDetection() {
        if (isDetecting) {
            Log.d(TAG, "Already detecting steps.")
            return
        }
        // リセット or 継続は仕様次第
        stepCount = 0

        if (stepDetectorSensor == null) {
            // 未サポート
            Log.w(TAG, "Can't start detection: no stepDetectorSensor.")
            return
        }
        // リスナー登録
        sensorManager.registerListener(sensorEventListener, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isDetecting = true
        Log.d(TAG, "startStepDetection: registered listener.")
    }

    /**
     * センサー検知を停止
     */
    fun stopStepDetection() {
        if (!isDetecting) {
            Log.d(TAG, "No step detection to stop.")
            return
        }
        sensorManager.unregisterListener(sensorEventListener, stepDetectorSensor)
        isDetecting = false
        Log.d(TAG, "stopStepDetection: unregistered listener.")
    }

    /**
     * 内部的なセンサーリスナー
     */
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                // 常に event.values[0] == 1.0f が入る想定
                val stepValue = event.values[0]
                if (stepValue == 1.0f) {
                    stepCount++
                    Log.i(TAG, "onSensorChanged: stepCount=$stepCount")
                    // コールバック呼び出し
                    onStepListener?.onStepDetected(stepCount)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // not used
        }
    }
}
