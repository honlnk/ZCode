package com.example.zcode_mobile.remote

import android.net.Uri
import com.example.zcode_mobile.BuildConfig

private const val ZCODE_REMOTE_SCHEME = "zcode"
private const val ZCODE_REMOTE_HOST = "remote"

// 手机远控 v4 登录由官方 OAuth 提供方完成；这些域名族的主框架跳转必须留在 WebView 内，
// 否则登录态会落在系统浏览器、App 永远等不到回调（表现为扫码后去浏览器登录且回不来）。
private val IN_APP_AUTH_DOMAIN_SUFFIXES = listOf("z.ai", "bigmodel.cn", "zhipuai.cn")

object RemoteControlUrlPolicy {
    fun resolve(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        return resolve(uri)
    }

    fun resolve(uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        if (uri.scheme == ZCODE_REMOTE_SCHEME && uri.host == ZCODE_REMOTE_HOST) {
            return buildDefaultRemoteUrl(uri)
        }

        if (isAllowedRemoteUrl(uri)) {
            return uri.toString()
        }

        return null
    }

    fun isAllowedRemoteUrl(raw: String?): Boolean {
        val uri = runCatching { Uri.parse(raw?.trim().orEmpty()) }.getOrNull() ?: return false
        return isAllowedRemoteUrl(uri)
    }

    /** 远控入口本身：https://zcode.z.ai/remote*（生产包唯一入口）。 */
    fun isRemoteEntryUrl(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.scheme?.lowercase() == "https" &&
            uri.host?.lowercase() == "zcode.z.ai" &&
            uri.path.orEmpty().startsWith("/remote")
    }

    /**
     * 允许在 WebView 内继续主框架导航的地址。
     * - 远控入口 /remote* 始终放行；
     * - OAuth 域族（z.ai / bigmodel.cn / zhipuai.cn 及其子域）放行，保证登录不跳出 App；
     * - zcode.z.ai 的非 /remote 路径只在登录弹窗内放行（allowZcodeNonRemote），主页面仍严格限制。
     */
    fun isInAppNavigationAllowed(uri: Uri?, allowZcodeNonRemote: Boolean = false): Boolean {
        if (uri == null) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()

        if (isRemoteEntryUrl(uri)) {
            return true
        }

        if (scheme == "https") {
            if (host == "zcode.z.ai") {
                return allowZcodeNonRemote
            }
            if (IN_APP_AUTH_DOMAIN_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
                return true
            }
        }

        if (!BuildConfig.DEBUG) {
            return false
        }

        // Bugfix: 开发机真机调试时二维码会指向局域网 Vite 地址。
        // 生产包仍只允许 zcode.z.ai，debug 包才放开测试域、本机和局域网 http/https，避免远控壳变成任意浏览器。
        if (!uri.path.orEmpty().startsWith("/remote")) {
            return false
        }
        if (host == "zcode.z.ai") {
            return scheme == "https"
        }
        return (scheme == "http" || scheme == "https") && isDebugHost(host)
    }

    private fun buildDefaultRemoteUrl(uri: Uri): String {
        val base = Uri.parse(BuildConfig.REMOTE_CONTROL_DEFAULT_URL).buildUpon()
        uri.encodedQuery?.let { base.encodedQuery(it) }
        uri.encodedFragment?.let { base.encodedFragment(it) }
        return base.build().toString()
    }

    private fun isAllowedRemoteUrl(uri: Uri): Boolean {
        if (isRemoteEntryUrl(uri)) {
            return true
        }

        if (!BuildConfig.DEBUG) {
            return false
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        if (!uri.path.orEmpty().startsWith("/remote")) {
            return false
        }
        if (host == "zcode.z.ai") {
            return scheme == "https"
        }
        return (scheme == "http" || scheme == "https") && isDebugHost(host)
    }

    private fun isDebugHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") {
            return true
        }
        if (host.endsWith(".local")) {
            return true
        }
        if (host.startsWith("192.168.") || host.startsWith("10.")) {
            return true
        }
        return Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.").containsMatchIn(host)
    }
}
