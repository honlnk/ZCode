package com.example.zcode_mobile

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.example.zcode_mobile.remote.RemoteControlKeepAliveService
import com.example.zcode_mobile.remote.RemoteControlRecentLink
import com.example.zcode_mobile.remote.RemoteControlRecentLinksStore
import com.example.zcode_mobile.remote.RemoteControlUrlPolicy
import com.example.zcode_mobile.ui.RemoteControlApp
import com.example.zcode_mobile.ui.theme.ZcodemobileTheme

class MainActivity : ComponentActivity() {
    private var remoteUrl by mutableStateOf<String?>(null)
    private var recentLinks by mutableStateOf<List<RemoteControlRecentLink>>(emptyList())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 状态栏/导航栏图标明暗跟随系统主题：深色背景用浅色图标，浅色背景用深色图标。
        val isNightMode = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
        if (BuildConfig.DEBUG) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        recentLinks = RemoteControlRecentLinksStore.read(this)

        val restoredRemoteUrl = savedInstanceState?.getString(KEY_REMOTE_URL)
        val launchRemoteUrl = restoredRemoteUrl ?: resolveRemoteUrlFromIntent(intent)
        launchRemoteUrl?.let { openRemoteUrl(it) }

        setContent {
            ZcodemobileTheme {
                RemoteControlApp(
                    remoteUrl = remoteUrl,
                    recentLinks = recentLinks,
                    onOpenUrl = ::openRemoteUrl,
                    onCloseRemote = ::closeRemote,
                    onClearRecentLinks = ::clearRecentLinks,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveRemoteUrlFromIntent(intent)?.let { openRemoteUrl(it) }
    }

    override fun onResume() {
        super.onResume()
        restartKeepAliveIfNeeded()
    }

    override fun onTopResumedActivityChanged(isTopResumed: Boolean) {
        super.onTopResumedActivityChanged(isTopResumed)
        if (isTopResumed) restartKeepAliveIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        remoteUrl?.let { outState.putString(KEY_REMOTE_URL, it) }
    }

    private fun openRemoteUrl(rawUrl: String): Boolean {
        val resolvedUrl = RemoteControlUrlPolicy.resolve(rawUrl) ?: return false
        recentLinks = RemoteControlRecentLinksStore.record(this, resolvedUrl)
        remoteUrl = resolvedUrl
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestNotificationPermissionIfNeeded()
        startKeepAliveService(resolvedUrl)
        return true
    }

    /**
     * 保活服务可能已被系统按时长上限停掉（见 RemoteControlKeepAliveService.onTimeout），或上次补拉被系统拒绝。
     * 回到前台后补拉，保证下次切后台仍然保活；服务在运行时不做任何事。
     *
     * Bugfix: 时长额度要等系统把本进程记为回到前台才重置，在此之前启动会被系统拒绝。onResume 早于系统完成
     * 这笔记账，所以延后到当前消息处理完再启动，并在系统确认进入回到前台状态（onTopResumedActivityChanged）
     * 时再补一次。仍被拒绝时服务自行停止，下次回到前台再试。
     */
    private fun restartKeepAliveIfNeeded() {
        val url = remoteUrl ?: return
        if (RemoteControlKeepAliveService.isRunning) return
        mainHandler.post {
            if (remoteUrl == url && !RemoteControlKeepAliveService.isRunning) {
                startKeepAliveService(url)
            }
        }
    }

    /**
     * 启动保活服务。被系统拒绝时保留现状、下次回到前台再试，不让保活失败影响远控页本身。
     */
    private fun startKeepAliveService(url: String) {
        try {
            RemoteControlKeepAliveService.start(this, url)
        } catch (error: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException 继承 IllegalStateException，API 31 起才有。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
            ) {
                Log.w(TAG, "keep-alive service start not allowed", error)
                return
            }
            throw error
        }
    }

    private fun closeRemote() {
        remoteUrl = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        RemoteControlKeepAliveService.stop(this)
    }

    private fun clearRecentLinks() {
        recentLinks = RemoteControlRecentLinksStore.clear(this)
    }

    private fun resolveRemoteUrlFromIntent(intent: Intent?): String? {
        if (intent == null) {
            return null
        }

        intent.getStringExtra(RemoteControlKeepAliveService.EXTRA_REMOTE_URL)?.let { raw ->
            RemoteControlUrlPolicy.resolve(raw)?.let { return it }
        }
        intent.data?.let { uri ->
            RemoteControlUrlPolicy.resolve(uri)?.let { return it }
        }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { raw ->
            RemoteControlUrlPolicy.resolve(raw)?.let { return it }
        }

        return null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_REMOTE_URL = "remoteUrl"
        private const val REQUEST_NOTIFICATIONS = 4001
    }
}
