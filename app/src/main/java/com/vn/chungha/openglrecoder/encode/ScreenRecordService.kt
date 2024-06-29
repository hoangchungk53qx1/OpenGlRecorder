package com.vn.chungha.openglrecoder.encode

import com.vn.chungha.openglrecoder.R
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * This is a service class for screen recording.
 * It handles the start and stop commands for screen recording.
 */
class ScreenRecordService : Service() {
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val scope = MainScope()

    private var screenRecorder: ScreenRecorder? = null

    /**
     * This method is called when the service is binded.
     * It returns null as the service does not allow binding.
     */
    override fun onBind(intent: Intent): IBinder? = null

    /**
     * This method is called when the service is started.
     * It handles the start and stop commands for screen recording.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notify the foreground service notification
        notifyForegroundServiceNotification()

        // Extract values from the intent
        if (intent?.getBooleanExtra(INTENT_KEY_START_OR_STOP, false) == true) {
            // Start command
            screenRecorder = ScreenRecorder(
                context = this,
                resultCode = intent.getIntExtra(INTENT_KEY_MEDIA_PROJECTION_RESULT_CODE, -1),
                resultData = intent.getParcelableExtra(INTENT_KEY_MEDIA_PROJECTION_RESULT_DATA)!!
            )
            // Start recording
            screenRecorder?.startRecord()
        } else {
            // Stop command
            scope.launch {
                // Wait for the stop command and then stop the service
                screenRecorder?.stopRecord()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }


    /**
     * This method is called when the service is destroyed.
     * It cancels any ongoing operations.
     */
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun notifyForegroundServiceNotification() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW).apply {
                setName("Screen Recorder Service")
            }.build()
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setContentTitle("screen_recorder_service_chungha")
            setContentText("Screen Recorder Service is running...")
            setSmallIcon(R.drawable.ic_launcher_foreground)
        }.build()

        // ForegroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    companion object {

        private const val CHANNEL_ID = "screen_recorder_service"
        private const val NOTIFICATION_ID = 12313313

        private const val INTENT_KEY_MEDIA_PROJECTION_RESULT_CODE = "result_code"
        private const val INTENT_KEY_MEDIA_PROJECTION_RESULT_DATA = "result_code"

        /**
         * This method starts the screen recording service.
         * It creates an intent with the necessary extras and starts the foreground service.
         */
        private const val INTENT_KEY_START_OR_STOP = "start_or_stop"

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                putExtra(INTENT_KEY_MEDIA_PROJECTION_RESULT_CODE, resultCode)
                putExtra(INTENT_KEY_MEDIA_PROJECTION_RESULT_DATA, data)
                putExtra(INTENT_KEY_START_OR_STOP, true)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * This method stops the screen recording service.
         * It creates an intent with the necessary extras and starts the foreground service.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                putExtra(INTENT_KEY_START_OR_STOP, false)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}