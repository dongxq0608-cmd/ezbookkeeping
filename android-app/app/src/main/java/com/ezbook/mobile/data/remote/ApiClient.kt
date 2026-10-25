package com.ezbook.mobile.data.remote

import android.os.Build
import android.provider.Settings
import com.ezbook.mobile.BuildConfig
import com.ezbook.mobile.data.SessionStore
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.data.model.AssetTrendDay
import com.ezbook.mobile.data.model.ApiResponse
import com.ezbook.mobile.data.model.AuthResponse
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.data.model.IdRequest
import com.ezbook.mobile.data.model.LoginRequest
import com.ezbook.mobile.data.model.RevokeTokenRequest
import com.ezbook.mobile.data.model.ServerVersion
import com.ezbook.mobile.data.model.StatisticItem
import com.ezbook.mobile.data.model.StatisticResponse
import com.ezbook.mobile.data.model.TrendMonth
import com.ezbook.mobile.data.model.Tag
import com.ezbook.mobile.data.model.TokenRefreshResponse
import com.ezbook.mobile.data.model.TransactionItem
import com.ezbook.mobile.data.model.TransactionPage
import com.ezbook.mobile.data.model.TransactionSaveRequest
import com.ezbook.mobile.data.model.TwoFactorRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 业务异常，code 为 ezBookkeeping 的 errorCode（服务端未返回时为 HTTP 状态码的负值）
 */
class ApiException(
    val code: Int,
    override val message: String
) : Exception(message) {

    /** 与前端一致的“需要重新登录”错误码集合 */
    val isAuthError: Boolean
        get() = code in 202001..202006 || code == 202012
}

/** 会话彻底失效（刷新也救不回来） */
class SessionExpiredException : Exception("登录状态已失效，请重新登录")

class ApiClient(private val store: SessionStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 续期这类「不能让用户干等」的短请求用更短的超时 */
    private val quickClient = client.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /** 会话失效回调（由 App 层注册，用于跳转登录页） */
    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    private val refreshMutex = Mutex()

    /** 会话失效只上报一次，避免并发请求重复弹提示 / 重复跳转登录页 */
    private val expiredReported = java.util.concurrent.atomic.AtomicBoolean(false)

    private companion object {
        /** 主动续期的最小间隔（秒）：服务端 token_min_refresh_interval 默认 86400（1 天） */
        const val RENEW_INTERVAL_SECONDS = 20L * 3600

        /** 各厂商存放「市场名 / 宣传机型」的系统属性 */
        val MARKET_NAME_PROPS = listOf(
            "ro.product.marketname",        // 小米 / vivo 等
            "ro.product.odm.marketname",
            "ro.product.vendor.marketname",
            "ro.vendor.product.market.name",
            "ro.vendor.oplus.market.name",  // OPPO / 一加 / realme
            "ro.config.marketing_name",     // 华为 / 荣耀
            "ro.product.model.marketname"
        )
    }

    /* ------------------------- 基础请求 ------------------------- */

    private fun buildUrl(path: String, query: Map<String, String?>?): HttpUrl {
        val base = store.serverUrl?.takeIf { it.isNotBlank() }
            ?: throw ApiException(-100, "尚未配置服务器地址")
        val full = if (path.startsWith("http://") || path.startsWith("https://")) path else base + path
        val parsed: HttpUrl = full.toHttpUrlOrNull() ?: throw ApiException(-101, "服务器地址格式不正确")
        val builder = parsed.newBuilder()
        query?.forEach { (key, value) ->
            if (value != null) {
                builder.addQueryParameter(key, value)
            }
        }
        return builder.build()
    }

    private fun timezoneOffsetMinutes(): String {
        val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        return offset.toString()
    }

    /**
     * 上报 User-Agent。
     *
     * 服务端会把登录/续期请求的 UA 原样存进 token 记录，网页端「设备与会话」再用 ua-parser-js
     * 解析这段 UA 得出设备类型与设备信息。OkHttp 默认的 `okhttp/4.x` 解析不出任何信息，
     * 于是显示为 web + Unknown Device。这里按 Android 手机浏览器的格式构造，
     * 让服务端能识别出「手机 + 具体机型 + Android 版本」。
     */
    private val userAgent: String by lazy { buildUserAgent() }

    private fun buildUserAgent(): String {
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
        val buildId = Build.ID?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"

        val deviceName = resolveDeviceName()
            .replace(Regex("[;()\"\\\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Android" }

        // 不带 Chrome 标识：ua-parser-js 会按 Android WebView 的经典格式识别成
        // 「Android Browser」，网页端显示为「机型 (Android Browser 4.0)」，
        // 一眼能看出不是普通 Chrome 网页会话
        val raw = "Mozilla/5.0 (Linux; Android $release; $deviceName Build/$buildId) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Mobile Safari/537.36 EzBook/${BuildConfig.VERSION_NAME}"

        // OkHttp 只接受 ASCII 可打印字符，个别机型的型号 / build id 可能带非 ASCII 字符
        return raw.replace(Regex("[^\\x20-\\x7E]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 解析对人友好的机型名。
     *
     * 国产 ROM 的 `Build.MODEL` 往往是入网认证型号（如 `23113RKC6C`），不是宣传名（Redmi K70）。
     * 依次尝试：厂商系统属性里的市场名 → 系统设置里的「设备名称」 → `厂商 + 型号` 兜底。
     */
    private fun resolveDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val fallback = when {
            model.isBlank() -> manufacturer
            manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }

        val marketName = MARKET_NAME_PROPS.firstNotNullOfOrNull { key ->
            readSystemProperty(key)?.takeIf { it.length in 2..48 }
        }
        val settingsName = runCatching {
            Settings.Global.getString(store.appContext.contentResolver, "device_name")
        }.getOrNull()?.trim()?.takeIf { it.length in 2..48 }

        // 设置里的名字可能与型号相同（用户没改过），这种情况没有额外信息，直接兜底
        val candidate = marketName
            ?: settingsName?.takeIf { !it.equals(model, ignoreCase = true) }

        return candidate?.takeIf { it.isNotBlank() } ?: fallback
    }

    /** 读取 Android 系统属性（`getprop`），失败时返回 null */
    private fun readSystemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun doCall(
        method: String,
        path: String,
        query: Map<String, String?>? = null,
        body: String? = null,
        auth: Boolean = false,
        tokenOverride: String? = null,
        quick: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val url = buildUrl(path, query)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("X-Timezone-Offset", timezoneOffsetMinutes())
            .header("X-Timezone-Name", TimeZone.getDefault().id)
            .header("Accept-Language", "zh-CN,zh;q=0.9")

        if (auth) {
            val token = tokenOverride ?: store.token
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "bearer $token")
            }
        }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete((body ?: "{}").toRequestBody(jsonMediaType))
            else -> builder.method(method.uppercase(), (body ?: "{}").toRequestBody(jsonMediaType))
        }

        val response = try {
            (if (quick) quickClient else client).newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException(-102, "无法连接服务器，请检查网络或服务器地址")
        }

        val status = response.code
        val text = response.use { it.body?.string().orEmpty() }

        if (status !in 200..299) {
            throw buildError(status, text)
        }
        text.ifBlank { "{}" }
    }

    private fun buildError(status: Int, text: String): ApiException {
        val parsed = runCatching {
            json.decodeFromString<ApiResponse<JsonElement>>(text)
        }.getOrNull()

        val code = parsed?.errorCode?.takeIf { it != 0 } ?: -status
        val message = parsed?.errorMessage?.takeIf { it.isNotBlank() }
            ?: defaultMessage(status)
        return ApiException(code, message)
    }

    private fun defaultMessage(status: Int): String = when (status) {
        401 -> "登录状态已失效，请重新登录"
        403 -> "没有权限执行该操作"
        404 -> "接口不存在，请确认服务端版本"
        429 -> "操作过于频繁，请稍后再试"
        in 500..599 -> "服务器内部错误（HTTP $status）"
        else -> "请求失败（HTTP $status）"
    }

    /** 带 401 自动刷新与重试的鉴权请求 */
    private suspend fun callAuthed(
        method: String,
        path: String,
        query: Map<String, String?>? = null,
        body: String? = null
    ): String {
        return try {
            doCall(method, path, query, body, auth = true)
        } catch (e: ApiException) {
            if (!e.isAuthError) throw e
            if (!refreshSession()) {
                expireSession()
            }
            try {
                doCall(method, path, query, body, auth = true)
            } catch (e2: ApiException) {
                if (e2.isAuthError) expireSession()
                throw e2
            }
        }
    }

    /**
     * 主动续期：趁 token 仍然有效时调用 `tokens/refresh.json` 换一个新 token。
     *
     * 服务端 token 默认 30 天过期，而续期接口本身要求 token 有效、且距上次签发需超过
     * `token_min_refresh_interval`（默认 1 天）。所以只有在过期前主动续期，登录状态才能
     * 一直延续；一旦过期，服务端会直接拒绝，只能重新登录。
     *
     * @return true = 当前会话仍可用（已续期/暂不需要续期）；false = 会话已失效
     */
    suspend fun renewSession(force: Boolean = false): Boolean {
        if (store.token.isNullOrBlank()) return false

        val now = System.currentTimeMillis() / 1000
        if (!force && now - store.tokenRefreshedAt in 0 until RENEW_INTERVAL_SECONDS) {
            return true
        }

        return try {
            refreshMutex.withLock {
                val sinceRefresh = System.currentTimeMillis() / 1000 - store.tokenRefreshedAt
                if (store.token.isNullOrBlank()) {
                    false
                } else if (!force && sinceRefresh in 0 until RENEW_INTERVAL_SECONDS) {
                    // 排队期间已被其它调用续过
                    true
                } else {
                    requestRefresh()
                    true
                }
            }
        } catch (e: SessionExpiredException) {
            false
        } catch (e: ApiException) {
            // 网络异常 / 服务端 5xx：不打断使用，下次启动或回到前台再试
            true
        } catch (e: Exception) {
            true
        }
    }

    /** 401 后的兜底刷新：失败返回 false，由调用方决定是否登出 */
    private suspend fun refreshSession(): Boolean {
        val before = store.token
        if (before.isNullOrBlank()) return false

        return refreshMutex.withLock {
            val current = store.token
            when {
                // 已被其它请求登出
                current.isNullOrBlank() -> false
                // 其它请求已刷新过，直接复用
                current != before -> true
                else -> try {
                    requestRefresh()
                    true
                } catch (e: ApiException) {
                    // 只有服务端明确判定 token 无效时才登出；网络异常交给上层提示，保留登录状态
                    if (e.isAuthError) false else throw e
                }
            }
        }
    }

    /**
     * 执行一次续期请求：成功时保存新 token（鉴权失败会抛 [ApiException]，网络异常抛对应异常）
     */
    private suspend fun requestRefresh() {
        val text = doCall("POST", "/api/v1/tokens/refresh.json", null, "{}", auth = true, quick = true)
        val envelope = json.decodeFromString<ApiResponse<TokenRefreshResponse>>(text)

        if (!envelope.success) {
            throw ApiException(envelope.errorCode, envelope.errorMessage.ifBlank { "会话续期失败" })
        }
        val result = envelope.result ?: throw ApiException(-104, "服务端未返回续期结果")

        result.user?.let { store.saveUser(it) }

        val newToken = result.newToken?.takeIf { it.isNotBlank() && it != store.token }
        if (newToken != null) {
            store.updateToken(newToken)
            result.oldTokenId?.let { oldId ->
                runCatching {
                    val payload = json.encodeToString(
                        RevokeTokenRequest.serializer(),
                        RevokeTokenRequest(oldId)
                    )
                    doCall("POST", "/api/v1/tokens/revoke.json", null, payload, auth = true)
                }
            }
        }
        store.markTokenRefreshed()
    }

    private fun expireSession(): Nothing {
        store.logout()
        // 并发请求同时失效时只提示、跳转一次
        if (expiredReported.compareAndSet(false, true)) {
            onSessionExpired?.invoke()
        }
        throw SessionExpiredException()
    }

    private inline fun <reified T> decode(text: String): T {
        val envelope = json.decodeFromString<ApiResponse<T>>(text)
        if (!envelope.success) {
            throw ApiException(envelope.errorCode, envelope.errorMessage.ifBlank { "请求失败" })
        }
        return envelope.result ?: throw ApiException(envelope.errorCode, "服务端未返回数据")
    }

    private fun ensureSuccess(text: String) {
        val envelope = runCatching {
            json.decodeFromString<ApiResponse<JsonElement>>(text)
        }.getOrNull() ?: throw ApiException(-103, "服务器返回了无法解析的响应")

        if (!envelope.success) {
            throw ApiException(envelope.errorCode, envelope.errorMessage.ifBlank { "操作失败" })
        }
    }

    /* ------------------------- 服务探测 ------------------------- */

    suspend fun probeServer(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return@withContext false
        try {
            val request = Request.Builder()
                .url("$base/healthz.json")
                .header("X-Timezone-Offset", timezoneOffsetMinutes())
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /* ------------------------- 认证 ------------------------- */

    suspend fun login(loginName: String, password: String): AuthResponse {
        val payload = json.encodeToString(LoginRequest.serializer(), LoginRequest(loginName, password))
        val text = doCall("POST", "/api/authorize.json", null, payload, auth = false)
        expiredReported.set(false)
        return decode(text)
    }

    suspend fun authorizeTwoFactor(tempToken: String, passcode: String): AuthResponse {
        val payload = json.encodeToString(TwoFactorRequest.serializer(), TwoFactorRequest(passcode))
        val text = doCall(
            "POST", "/api/2fa/authorize.json", null, payload,
            auth = true, tokenOverride = tempToken
        )
        expiredReported.set(false)
        return decode(text)
    }

    suspend fun logout() {
        runCatching { doCall("GET", "/api/logout.json", null, null, auth = true) }
        store.logout()
    }

    suspend fun serverVersion(): ServerVersion =
        decode(callAuthed("GET", "/api/v1/systems/version.json"))

    /* ------------------------- 账户 / 分类 / 标签 ------------------------- */

    suspend fun accounts(visibleOnly: Boolean = false): List<Account> =
        decode(
            callAuthed(
                "GET", "/api/v1/accounts/list.json",
                mapOf("visible_only" to visibleOnly.toString())
            )
        )

    /**
     * 分类接口返回的是「按类型分组」的字典：{"1": [..收入..], "2": [..支出..], "3": [..转账..]}
     * 这里按 收入 -> 支出 -> 转账 的顺序拍平成一个列表返回。
     */
    suspend fun categories(type: Int = 0, parentId: Long = -1L): List<Category> {
        val text = callAuthed(
            "GET", "/api/v1/transaction/categories/list.json",
            mapOf("type" to type.toString(), "parent_id" to parentId.toString())
        )
        val grouped = decode<Map<String, List<Category>>>(text)
        val ordered = listOf("1", "2", "3")
        val result = ArrayList<Category>()
        ordered.forEach { key ->
            grouped[key]?.let { result.addAll(it) }
        }
        grouped.forEach { (key, value) ->
            if (!ordered.contains(key)) {
                result.addAll(value)
            }
        }
        return result
    }

    suspend fun tags(): List<Tag> =
        decode(callAuthed("GET", "/api/v1/transaction/tags/list.json"))

    /* ------------------------- 交易 ------------------------- */

    suspend fun transactionsInMonth(
        year: Int,
        month: Int,
        type: Int = 0,
        accountId: String? = null
    ): List<TransactionItem> {
        val query = mutableMapOf(
            "year" to year.toString(),
            "month" to month.toString(),
            "type" to type.toString()
        )
        // 服务端会自动展开子账户
        accountId?.takeIf { it.isNotBlank() && it != "0" }?.let { query["account_ids"] = it }
        val text = callAuthed("GET", "/api/v1/transactions/list/by_month.json", query)
        return decode<TransactionPage>(text).items
    }

    suspend fun transaction(id: String): TransactionItem =
        decode(callAuthed("GET", "/api/v1/transactions/get.json", mapOf("id" to id)))

    suspend fun createTransaction(request: TransactionSaveRequest) {
        val payload = json.encodeToString(TransactionSaveRequest.serializer(), request)
        ensureSuccess(callAuthed("POST", "/api/v1/transactions/add.json", null, payload))
    }

    suspend fun modifyTransaction(request: TransactionSaveRequest) {
        val payload = json.encodeToString(TransactionSaveRequest.serializer(), request)
        ensureSuccess(callAuthed("POST", "/api/v1/transactions/modify.json", null, payload))
    }

    suspend fun deleteTransaction(id: String) {
        val payload = json.encodeToString(IdRequest.serializer(), IdRequest(id))
        ensureSuccess(callAuthed("POST", "/api/v1/transactions/delete.json", null, payload))
    }

    /* ------------------------- 统计分析 ------------------------- */

    /**
     * 分类分析：返回「分类 + 账户」维度的原始汇总，
     * 金额为最小货币单位整数的字符串，恒为正数，转账会成对出现两行。
     */
    suspend fun statistics(startTime: Long, endTime: Long): List<StatisticItem> {
        val text = callAuthed(
            "GET", "/api/v1/transactions/statistics.json",
            mapOf(
                "start_time" to startTime.toString(),
                "end_time" to endTime.toString()
            )
        )
        return decode<StatisticResponse>(text).items
    }

    /**
     * 趋势分析：按月返回，只含有数据的月份，需要客户端补齐时间轴。
     * @param startYearMonth 形如 "2024-3"（月份不补零）
     */
    suspend fun statisticsTrends(startYearMonth: String, endYearMonth: String): List<TrendMonth> {
        val text = callAuthed(
            "GET", "/api/v1/transactions/statistics/trends.json",
            mapOf(
                "start_year_month" to startYearMonth,
                "end_year_month" to endYearMonth
            )
        )
        return decode<List<TrendMonth>>(text)
    }

    /**
     * 资产趋势：按天返回各账户的日初 / 日末余额，只含有交易的日期。
     */
    suspend fun assetTrends(startTime: Long, endTime: Long): List<AssetTrendDay> {
        val text = callAuthed(
            "GET", "/api/v1/transactions/statistics/asset_trends.json",
            mapOf(
                "start_time" to startTime.toString(),
                "end_time" to endTime.toString()
            )
        )
        return decode<List<AssetTrendDay>>(text)
    }
}
