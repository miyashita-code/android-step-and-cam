package com.rementia.walkingdetection

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener

/**
 * ActivityUpdatesManager:
 * 従来の requestActivityUpdates(intervalMillis, pendingIntent) で
 * ユーザーアクティビティを定期的に取得するためのクラス。
 */
class ActivityUpdatesManager private constructor(context: Context) {

    interface ActivityUpdatesCallback {
        fun onActivityUpdated(activityInfo: String)
    }

    private val mContext = context.applicationContext
    private val mClient: ActivityRecognitionClient = ActivityRecognition.getClient(mContext)
    private var mCallback: ActivityUpdatesCallback? = null

    // ここに放り込まれたBroadcastReceiverがアクティビティ結果を受け取る
    private val mPendingIntent: PendingIntent

    init {
        Log.d(TAG, "ActivityUpdatesManager init.")
        val intent = Intent(mContext, ActivityUpdatesReceiver::class.java)
        mPendingIntent = PendingIntent.getBroadcast(
            mContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private var sInstance: ActivityUpdatesManager? = null
        private const val TAG = "ActivityUpdatesManager"

        fun getInstance(context: Context): ActivityUpdatesManager {
            if (sInstance == null) {
                sInstance = ActivityUpdatesManager(context)
            }
            return sInstance!!
        }
    }

    fun setCallback(callback: ActivityUpdatesCallback?) {
        this.mCallback = callback
    }

    /**
     * BroadcastReceiver から呼び出される想定:
     * アクティビティの文字列をUIに知らせる。
     */
    fun notifyActivityUpdated(activityLabel: String) {
        Log.d(TAG, "notifyActivityUpdated: $activityLabel")
        mCallback?.onActivityUpdated(activityLabel)
    }

    /**
     * intervalMillis (ms) ごとにアクティビティ更新を受け取るリクエスト
     */
    fun startActivityUpdates(intervalMillis: Long) {
        Log.d(TAG, "startActivityUpdates called with interval=$intervalMillis")

        // パーミッションチェック
        val permissionState = ContextCompat.checkSelfPermission(
            mContext, Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission NOT GRANTED, returning.")
            return
        }

        // リクエスト
        mClient.requestActivityUpdates(intervalMillis, mPendingIntent)
            .addOnSuccessListener(OnSuccessListener<Void> {
                Log.d(TAG, "requestActivityUpdates SUCCESS!")
            })
            .addOnFailureListener(OnFailureListener { e ->
                Log.e(TAG, "requestActivityUpdates FAILURE: ${e.message}")
            })
    }

    /**
     * 登録解除
     */
    fun stopActivityUpdates() {
        Log.d(TAG, "stopActivityUpdates called.")
        val permissionState = ContextCompat.checkSelfPermission(
            mContext, Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission NOT GRANTED, cannot remove.")
            return
        }

        mClient.removeActivityUpdates(mPendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "removeActivityUpdates SUCCESS!")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "removeActivityUpdates FAILURE: ${e.message}")
            }
    }
}
