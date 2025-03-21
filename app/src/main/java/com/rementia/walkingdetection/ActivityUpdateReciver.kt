package com.rementia.walkingdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * ActivityUpdatesReceiver:
 * requestActivityUpdates() が発行するブロードキャストを受信し、
 * ActivityRecognitionResult を取り出してアプリ内に通知する。
 */
class ActivityUpdatesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive triggered!")
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            if (result != null) {
                Log.d(TAG, "ActivityRecognitionResult != null")

                // 全アクティビティ(可能性)一覧
                val probableActivities = result.probableActivities
                // 最も信頼度が高いもの
                val mostProbable = result.mostProbableActivity

                // 例: WAKLING(conf=80)
                val activityLabel = parseActivityType(mostProbable)

                // Manager へ通知
                val manager = ActivityUpdatesManager.getInstance(context)
                manager.notifyActivityUpdated("mostProbable: $activityLabel")

                // 全ての候補をログ出力
                val allListString = probableActivities.joinToString("\n") { a ->
                    parseActivityType(a)
                }
                Log.d(TAG, "All probableActivities:\n$allListString")
            } else {
                Log.d(TAG, "ActivityRecognitionResult is null")
            }
        } else {
            Log.d(TAG, "No ActivityRecognitionResult in intent.")
        }
    }

    /**
     * DetectedActivity を読みやすい文字列に変換
     */
    private fun parseActivityType(activity: DetectedActivity): String {
        val typeStr = when (activity.type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "UNIDENTIFIED"
        }
        return "$typeStr(conf=${activity.confidence})"
    }

    companion object {
        private const val TAG = "ActivityUpdatesReceiver"
    }
}
