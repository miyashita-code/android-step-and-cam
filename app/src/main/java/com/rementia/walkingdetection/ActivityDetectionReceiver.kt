package com.rementia.walkingdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult

class ActivityDetectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ActivityDetectionReceiver", "onReceive")
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            if (result != null) {
                val manager = ActivityRecognitionManager.getInstance(context)

                // 1) 生( raw )のイベント一覧を出力
                val rawEvents = result.transitionEvents
                val rawString = rawEvents.joinToString("\n") { e ->
                    // e.activityType, e.transitionType, e.elapsedRealTimeNanos
                    "RawEvent: activityType=${e.activityType}, transitionType=${e.transitionType}, time=${e.elapsedRealTimeNanos}"
                }
                manager.notifyActivityChanged("Raw transitions:\n$rawString")

                Log.d("ActivityDetectionReceiver", "Raw transitions:\n$rawString")

                // 2) イベントを個別に判定 → 「IN_VEHICLE 開始」などのメッセージ生成
                for (event in rawEvents) {
                    val activityType = event.activityType
                    val transitionType = event.transitionType

                    val activityLabel = getActivityLabel(activityType)
                    val enterOrExit = if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        "開始"
                    } else {
                        "終了"
                    }
                    val displayText = "$activityLabel $enterOrExit"

                    // ActivityRecognitionManager を通じて MainActivity 等に通知
                    manager.notifyActivityChanged(displayText)
                }
            }
        }
    }

    /**
     * Activity Typeを文字列に変換
     */
    private fun getActivityLabel(activityType: Int): String {
        return when (activityType) {
            0 -> "車両乗車 (IN_VEHICLE)"
            1 -> "自転車 (ON_BICYCLE)"
            2 -> "徒歩 (ON_FOOT)"
            3 -> "静止 (STILL)"
            4 -> "不明 (UNKNOWN)"
            5 -> "端末傾斜 (TILTING)"
            7 -> "歩行 (WALKING)"
            8 -> "走行 (RUNNING)"
            else -> "未知の動作"
        }
    }
}
