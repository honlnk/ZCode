package com.example.zcode_mobile.remote

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.zcode_mobile.MainActivity
import com.example.zcode_mobile.R

class RemoteControlKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val remoteUrl = intent?.getStringExtra(EXTRA_REMOTE_URL).orEmpty()
        ensureNotificationChannel()

        // Bugfix: 纯浏览器后台后 WebView/JS/WebSocket 容易被系统暂停或回收。
        // 前台服务不能保证 WebView socket 永不断，但能给远控会话一个用户可见的保活边界，
        // 降低进程被杀概率；真正的状态恢复仍交给 web 端 replayable snapshot 协议完成。
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_remote_control)
            // vivo 等定制系统在通知中心显示的是应用图标缓存，App 换 Logo 后可能一直
            // 显示旧图标；显式给 largeIcon 才能保证通知里一定是当前 ZCode Logo。
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.zcode_logo))
            .setContentTitle(getString(R.string.remote_keep_alive_title))
            .setContentText(getString(R.string.remote_keep_alive_text))
            .setContentIntent(createContentIntent(remoteUrl))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Bugfix: 系统可能拒绝把服务转为前台：dataSync 前台服务的时长额度耗尽后，要等系统把本进程重新记为
        // 回到前台才重置；此外还有后台启动限制。拒绝以 ForegroundServiceStartNotAllowedException 从这里抛出
        // （不是从 startForegroundService() 抛出），不接住会终止整个进程、远控页随之丢失。
        // 此时系统已不再要求本服务进入前台，直接停止；用户回到前台后由 MainActivity 在服务未运行时补拉。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException 继承 IllegalStateException，API 31 起才有。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
            ) {
                Log.w(TAG, "keep-alive service foreground start not allowed", error)
                stopSelf()
                return START_NOT_STICKY
            }
            throw error
        }
        isRunning = true

        return START_NOT_STICKY
    }

    // Bugfix: targetSdk ≥ 35 的应用，dataSync 前台服务在后台每 24 小时累计只能运行 6 小时。到达上限后
    // 系统回调 onTimeout，服务必须自行停止；不停止会被系统以 RemoteServiceException 终止整个进程，
    // 远控页随之丢失。停止后保活通知随 onDestroy 移除，用户回到前台时由 MainActivity 在服务未运行时重新拉起。
    // 系统按版本回调不同重载（Android 15 回调单参版本，16 起回调带前台服务类型的版本），两个都要实现。
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.remote_keep_alive_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.remote_keep_alive_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createContentIntent(remoteUrl: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_REMOTE_URL, remoteUrl)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "zcode_remote_control"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_REMOTE_URL = "com.example.zcode_mobile.remote.REMOTE_URL"

        /**
         * 服务是否存活。只在主线程读写；MainActivity 回到前台时据此判断要不要补拉保活服务。
         */
        var isRunning = false
            private set

        fun start(context: Context, remoteUrl: String) {
            val intent = Intent(context, RemoteControlKeepAliveService::class.java)
                .putExtra(EXTRA_REMOTE_URL, remoteUrl)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteControlKeepAliveService::class.java))
        }
    }
}
