package com.ezbook.mobile.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.ezbook.mobile.data.model.UserBasicInfo
import kotlinx.serialization.json.Json

/**
 * 会话持久化：服务端地址、JWT、当前用户。
 */
class SessionStore(context: Context) {

    /** 用于读取系统设置（设备名称等） */
    val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ezbook_session", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)
        private set(value) {
            prefs.edit().putString(KEY_SERVER, value).apply()
        }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        private set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var user: UserBasicInfo?
        get() = prefs.getString(KEY_USER, null)?.let {
            runCatching { json.decodeFromString<UserBasicInfo>(it) }.getOrNull()
        }
        private set(value) {
            val text = value?.let { runCatching { json.encodeToString(UserBasicInfo.serializer(), it) }.getOrNull() }
            prefs.edit().putString(KEY_USER, text).apply()
        }

    /** 上一次记账使用的账户，用于下次记账默认选中 */
    var lastAccountId: String?
        get() = prefs.getString(KEY_LAST_ACCOUNT, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_ACCOUNT, value).apply()
        }

    /** 上次成功调用 tokens/refresh.json 的时间（秒），用于节流「主动续期」 */
    var tokenRefreshedAt: Long
        get() = prefs.getLong(KEY_REFRESHED_AT, 0L)
        private set(value) {
            prefs.edit().putLong(KEY_REFRESHED_AT, value).apply()
        }

    val hasServer: Boolean get() = !serverUrl.isNullOrBlank()
    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    /** 本地 token 是否已经过期（按 JWT 的 exp 判断，解析不出来时交给服务端判定） */
    fun isTokenExpired(token: String? = this.token): Boolean {
        val raw = token?.takeIf { it.isNotBlank() } ?: return true
        val expiresAt = tokenExpiresAt(raw) ?: return false
        return System.currentTimeMillis() / 1000 >= expiresAt
    }

    fun saveServerUrl(url: String?) {
        serverUrl = url?.let { normalizeUrl(it) }
    }

    fun saveSession(token: String, user: UserBasicInfo?) {
        this.token = token
        if (user != null) {
            this.user = user
        }
        markTokenRefreshed()
    }

    fun updateToken(token: String) {
        this.token = token
    }

    fun saveUser(user: UserBasicInfo) {
        this.user = user
    }

    /** 记录一次成功的续期（秒级时间戳） */
    fun markTokenRefreshed(at: Long = System.currentTimeMillis() / 1000) {
        tokenRefreshedAt = at
    }

    fun logout() {
        token = null
        user = null
        tokenRefreshedAt = 0L
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** 读取 JWT payload 中的 exp（秒级时间戳），解析失败返回 null */
    private fun tokenExpiresAt(token: String): Long? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val text = runCatching {
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        }.getOrNull() ?: return null
        return Regex("\"exp\"\\s*:\\s*(\\d+)")
            .find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_REFRESHED_AT = "token_refreshed_at"
        private const val KEY_LAST_ACCOUNT = "last_account_id"

        /**
         * 归一化服务器地址：
         * - 缺协议时补 https://
         * - 去掉末尾多余的 /
         * - 保留子路径（如 https://host/ezbookkeeping）
         */
        fun normalizeUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                url = "https://$url"
            }
            while (url.endsWith("/")) {
                url = url.dropLast(1)
            }
            return url
        }
    }
}
