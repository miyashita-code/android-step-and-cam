package com.rementia.walkingdetection

import android.content.Context
import android.hardware.*
import android.util.Log

class MultiMotionManager private constructor(context: Context) {

    interface SignificantMotionCallback {
        fun onSignificantMotion()  // 1回だけ (ワンショット)
    }
    interface StepDetectorCallback {
        fun onStepDetected(stepCountIncrement: Int)
    }
    interface StepCounterCallback {
        fun onStepCounterUpdated(totalSteps: Long)
    }
    interface TiltDetectorCallback {
        fun onTiltDetected()
    }



    companion object {
        private var sInstance: MultiMotionManager? = null
        fun getInstance(context: Context): MultiMotionManager {
            if (sInstance == null) {
                sInstance = MultiMotionManager(context.applicationContext)
            }
            return sInstance!!
        }

        private const val TAG = "MultiMotionManager"
    }

    private val sensorManager: SensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val TYPE_TILT_DETECTOR_CONST = 22


    // 各センサー取得
    private val significantMotionSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val tiltDetectorSensor: Sensor? = sensorManager.getDefaultSensor(TYPE_TILT_DETECTOR_CONST)

    // コールバック
    private var significantMotionCallback: SignificantMotionCallback? = null
    private var stepDetectorCallback: StepDetectorCallback? = null
    private var stepCounterCallback: StepCounterCallback? = null
    private var tiltDetectorCallback: TiltDetectorCallback? = null

    // Step Detector 用カウンタ
    private var stepDetectorCount = 0
    // Step Counter の初期値 (最初に呼ばれたときに取得し、差分で報告する場合もあり)
    private var stepCounterBase: Long = 0
    private var isStepCounterFirst = true

    // フラグ
    private var isSignificantMotionActive = false
    private var isStepDetectorActive = false
    private var isStepCounterActive = false
    private var isTiltDetectorActive = false


    /**
     * コールバック登録
     */
    fun setSignificantMotionCallback(cb: SignificantMotionCallback?) {
        significantMotionCallback = cb
    }
    fun setStepDetectorCallback(cb: StepDetectorCallback?) {
        stepDetectorCallback = cb
    }
    fun setStepCounterCallback(cb: StepCounterCallback?) {
        stepCounterCallback = cb
    }
    fun setTiltDetectorCallback(cb: TiltDetectorCallback?) {
        tiltDetectorCallback = cb
    }

    /**
     * 大きなモーション (Significant Motion) 開始 (one-shot)
     * イベント発生後、自動的にセンサーが無効化される端末が多い
     */
    fun startSignificantMotion() {
        if (significantMotionSensor == null) {
            Log.w(TAG, "SignificantMotion not supported.")
            return
        }
        if (isSignificantMotionActive) {
            Log.d(TAG, "SignificantMotion already active.")
            return
        }
        // one-shot のため TriggerEventListener を使う例もある
        // ここでは SensorEventListener でも可能だが通常は TriggerEventListener が推奨
        // ただしAndroid端末によっては TriggerEventListener でないと受け取れない場合あり
        // => ここでは TriggerEventListener の例を示す
        sensorManager.requestTriggerSensor(significantMotionTriggerListener, significantMotionSensor)
        isSignificantMotionActive = true
        Log.d(TAG, "startSignificantMotion: requestTriggerSensor")
    }
    fun stopSignificantMotion() {
        if (!isSignificantMotionActive) return
        if (significantMotionSensor != null) {
            sensorManager.cancelTriggerSensor(significantMotionTriggerListener, significantMotionSensor)
        }
        isSignificantMotionActive = false
        Log.d(TAG, "stopSignificantMotion")
    }

    private val significantMotionTriggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            // 一度呼ばれると自動的にセンサーが無効化 (one-shot)
            Log.i(TAG, "SignificantMotion onTrigger!")
            isSignificantMotionActive = false
            significantMotionCallback?.onSignificantMotion()
        }
    }

    /**
     * Step Detector: 1歩ごとに +1
     */
    fun startStepDetection() {
        if (stepDetectorSensor == null) {
            Log.w(TAG, "StepDetector not supported.")
            return
        }
        if (isStepDetectorActive) {
            Log.d(TAG, "StepDetector already active.")
            return
        }
        stepDetectorCount = 0
        sensorManager.registerListener(stepDetectorListener, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isStepDetectorActive = true
        Log.d(TAG, "startStepDetection")
    }
    fun stopStepDetection() {
        if (!isStepDetectorActive) return
        sensorManager.unregisterListener(stepDetectorListener, stepDetectorSensor)
        isStepDetectorActive = false
        Log.d(TAG, "stopStepDetection")
    }
    private val stepDetectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                if (event.values.isNotEmpty() && event.values[0] == 1.0f) {
                    stepDetectorCount++
                    Log.i(TAG, "StepDetector count=$stepDetectorCount")
                    stepDetectorCallback?.onStepDetected(stepDetectorCount)
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Step Counter: 再起動以降の累計歩数を返す
     */
    fun startStepCounter() {
        if (stepCounterSensor == null) {
            Log.w(TAG, "StepCounter not supported.")
            return
        }
        if (isStepCounterActive) {
            Log.d(TAG, "StepCounter already active.")
            return
        }
        // 初回呼び出しで base をセットするためフラグリセット
        isStepCounterFirst = true

        sensorManager.registerListener(stepCounterListener, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isStepCounterActive = true
        Log.d(TAG, "startStepCounter")
    }
    fun stopStepCounter() {
        if (!isStepCounterActive) return
        sensorManager.unregisterListener(stepCounterListener, stepCounterSensor)
        isStepCounterActive = false
        Log.d(TAG, "stopStepCounter")
    }
    private val stepCounterListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val total = event.values[0].toLong()
                if (isStepCounterFirst) {
                    // 最初に呼ばれたときの値を base にする
                    stepCounterBase = total
                    isStepCounterFirst = false
                }
                val diff = total - stepCounterBase
                Log.i(TAG, "StepCounter raw=$total, base=$stepCounterBase, diff=$diff")
                stepCounterCallback?.onStepCounterUpdated(diff)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Tilt Detector
     */
    fun startTiltDetector() {
        if (tiltDetectorSensor == null) {
            Log.w(TAG, "TiltDetector not supported.")
            return
        }
        if (isTiltDetectorActive) {
            Log.d(TAG, "TiltDetector already active.")
            return
        }
        sensorManager.registerListener(tiltListener, tiltDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isTiltDetectorActive = true
        Log.d(TAG, "startTiltDetector")
    }
    fun stopTiltDetector() {
        if (!isTiltDetectorActive) return
        sensorManager.unregisterListener(tiltListener, tiltDetectorSensor)
        isTiltDetectorActive = false
        Log.d(TAG, "stopTiltDetector")
    }
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type == TYPE_TILT_DETECTOR_CONST) {
                // one-shot 的に "1.0" が来る
                if (event.values.isNotEmpty() && event.values[0] == 1.0f) {
                    Log.i(TAG, "TiltDetector triggered!")
                    tiltDetectorCallback?.onTiltDetected()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

}
