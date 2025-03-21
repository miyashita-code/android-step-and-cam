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
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER
import com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener

/**
 * 歩行検出用 Manager
 */
class ActivityRecognitionManager private constructor(context: Context) {

    interface ActivityRecognitionCallback {
        /**
         * 検知されたアクティビティ情報を返す
         */
        fun onActivityChanged(activityLabel: String?)
    }

    private val mContext: Context = context.applicationContext
    private val mClient: ActivityRecognitionClient = ActivityRecognition.getClient(mContext)
    private val mPendingIntent: PendingIntent
    private var mCallback: ActivityRecognitionCallback? = null

    init {
        // BroadcastReceiver を呼び出す PendingIntent
        val intent = Intent(mContext, ActivityDetectionReceiver::class.java)
        mPendingIntent = PendingIntent.getBroadcast(
            mContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * コールバックをセット
     */
    fun setCallback(callback: ActivityRecognitionCallback?) {
        mCallback = callback
    }

    /**
     * ActivityTransition の登録
     * → 事前に ACTIVITY_RECOGNITION パーミッションが許可されているかチェックする
     */
    fun startUpdates() {
        // パーミッションが許可されているか確認
        val permissionState = ContextCompat.checkSelfPermission(
            mContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            // パーミッションが未許可の場合
            return
        }

        // 監視対象のアクティビティ (Enter/Exit) を定義
        val transitions = mutableListOf<ActivityTransition>()

        // IN_VEHICLE
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ACTIVITY_TRANSITION_EXIT)
                .build()
        )

        // WALKING
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ACTIVITY_TRANSITION_EXIT)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)

        // リクエスト登録
        mClient.requestActivityTransitionUpdates(request, mPendingIntent)
            .addOnSuccessListener(object : OnSuccessListener<Void> {
                override fun onSuccess(unused: Void?) {
                    // 成功時（ログやToastなどは必要に応じて）
                    Log.d("ActivityTranslation", "onSuccess: request")
                }
            })
            .addOnFailureListener(object : OnFailureListener {
                override fun onFailure(e: Exception) {
                    // 失敗時
                    Log.e("ActivityTranslation", "onFailure: request", e)
                }
            })
    }

    /**
     * ActivityTransition の登録解除
     */
    fun stopUpdates() {
        // 同様にパーミッションチェック
        val permissionState = ContextCompat.checkSelfPermission(
            mContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mClient.removeActivityTransitionUpdates(mPendingIntent)
            .addOnSuccessListener(object : OnSuccessListener<Void> {
                override fun onSuccess(unused: Void?) {
                    mPendingIntent.cancel()
                }
            })
            .addOnFailureListener(object : OnFailureListener {
                override fun onFailure(e: Exception) {
                    // エラー処理
                }
            })
    }

    /**
     * BroadcastReceiver から呼ばれる想定:
     * (Enter or Exit) & (ActivityType) が通知されるので、
     * コールバックを介してメイン画面などに伝える。
     */
    fun notifyActivityChanged(activityLabel: String?) {
        mCallback?.onActivityChanged(activityLabel)
    }

    companion object {
        private var sInstance: ActivityRecognitionManager? = null

        /**
         * シングルトン生成 / 取得
         */
        fun getInstance(context: Context): ActivityRecognitionManager {
            if (sInstance == null) {
                sInstance = ActivityRecognitionManager(context)
            }
            return sInstance!!
        }
    }
}
// Compare this snippet from app/src/main/java/com/rementia/walkingdetection/ActivityDetectionReceiver.kt: