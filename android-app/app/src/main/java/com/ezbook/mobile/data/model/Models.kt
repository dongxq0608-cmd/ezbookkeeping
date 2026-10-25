package com.ezbook.mobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* ---------------- 交易类型 ---------------- */

const val TX_TYPE_BALANCE = 1
const val TX_TYPE_INCOME = 2
const val TX_TYPE_EXPENSE = 3
const val TX_TYPE_TRANSFER = 4

/* ---------------- 分类类型 ---------------- */

const val CATEGORY_TYPE_INCOME = 1
const val CATEGORY_TYPE_EXPENSE = 2
const val CATEGORY_TYPE_TRANSFER = 3

/* ---------------- 账户类型 ---------------- */

const val ACCOUNT_CATEGORY_CASH = 1
const val ACCOUNT_CATEGORY_CHECKING = 2
const val ACCOUNT_CATEGORY_CREDIT_CARD = 3
const val ACCOUNT_CATEGORY_VIRTUAL = 4
const val ACCOUNT_CATEGORY_DEBT = 5
const val ACCOUNT_CATEGORY_RECEIVABLES = 6
const val ACCOUNT_CATEGORY_INVESTMENT = 7
const val ACCOUNT_CATEGORY_SAVINGS = 8
const val ACCOUNT_CATEGORY_CERTIFICATE = 9

/* ---------------- 通用响应包装 ---------------- */

@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val result: T? = null,
    val errorCode: Int = 0,
    val errorMessage: String = "",
    val path: String = "",
    val context: JsonObject? = null
)

/* ---------------- 用户 / 认证 ---------------- */

@Serializable
data class UserBasicInfo(
    val username: String = "",
    val email: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val defaultAccountId: String = "0",
    val defaultCurrency: String = "CNY",
    val language: String = "",
    val firstDayOfWeek: Int = 0,
    val transactionEditScope: Int = 0,
    val emailVerified: Boolean = true
) {
    val displayName: String get() = nickname.ifBlank { username }
}

@Serializable
data class LoginRequest(
    val loginName: String,
    val password: String
)

@Serializable
data class TwoFactorRequest(
    val passcode: String
)

@Serializable
data class AuthResponse(
    val token: String = "",
    val need2FA: Boolean = false,
    val user: UserBasicInfo? = null
)

@Serializable
data class TokenRefreshResponse(
    val newToken: String? = null,
    val oldTokenId: String? = null,
    val user: UserBasicInfo? = null
)

@Serializable
data class ServerVersion(
    val version: String = "",
    val commitHash: String = "",
    val buildTime: String = ""
)

/* ---------------- 账户 ---------------- */

@Serializable
data class Account(
    val id: String = "0",
    val name: String = "",
    val parentId: String = "0",
    val category: Int = 0,
    val type: Int = 1,
    val icon: String = "0",
    val color: String = "",
    val currency: String = "CNY",
    val balance: String = "0",
    val comment: String = "",
    val displayOrder: Int = 0,
    val isAsset: Boolean = false,
    val isLiability: Boolean = false,
    val hidden: Boolean = false,
    val subAccounts: List<Account> = emptyList()
)

/* ---------------- 分类 ---------------- */

@Serializable
data class Category(
    val id: String = "0",
    val name: String = "",
    val parentId: String = "0",
    val type: Int = CATEGORY_TYPE_EXPENSE,
    val icon: String = "0",
    val color: String = "",
    val comment: String = "",
    val displayOrder: Int = 0,
    val hidden: Boolean = false,
    val subCategories: List<Category> = emptyList()
)

/* ---------------- 标签 ---------------- */

@Serializable
data class Tag(
    val id: String = "0",
    val name: String = "",
    val groupId: String = "0",
    val displayOrder: Int = 0,
    val hidden: Boolean = false
)

/* ---------------- 交易 ---------------- */

@Serializable
data class TransactionItem(
    val id: String = "0",
    val timeSequenceId: String = "0",
    val type: Int = TX_TYPE_EXPENSE,
    val categoryId: String = "0",
    val category: Category? = null,
    val time: Long = 0,
    val utcOffset: Int = 0,
    val sourceAccountId: String = "0",
    val sourceAccount: Account? = null,
    val destinationAccountId: String? = null,
    val destinationAccount: Account? = null,
    val sourceAmount: Long = 0,
    val destinationAmount: Long? = null,
    val hideAmount: Boolean = false,
    val tagIds: List<String> = emptyList(),
    val tags: List<Tag>? = null,
    val comment: String = "",
    val editable: Boolean = true
) {
    val isEditable: Boolean get() = editable
}

@Serializable
data class TransactionPage(
    val items: List<TransactionItem> = emptyList(),
    val nextTimeSequenceId: String? = null,
    val totalCount: Long? = null
)

@Serializable
data class TransactionSaveRequest(
    val id: String? = null,
    val type: Int,
    val categoryId: String = "0",
    val time: Long,
    val utcOffset: Int = 0,
    val sourceAccountId: String,
    val destinationAccountId: String = "0",
    val sourceAmount: Long = 0,
    val destinationAmount: Long = 0,
    val hideAmount: Boolean = false,
    val tagIds: List<String> = emptyList(),
    val comment: String = "",
    val clientSessionId: String? = null
)

@Serializable
data class IdRequest(
    val id: String
)

@Serializable
data class RevokeTokenRequest(
    val tokenId: String
)

/* ---------------- 统计 ---------------- */

@Serializable
data class StatisticResponse(
    val startTime: Long = 0,
    val endTime: Long = 0,
    val items: List<StatisticItem> = emptyList()
)

@Serializable
data class StatisticItem(
    val categoryId: String = "0",
    val accountId: String = "0",
    val relatedAccountId: String? = null,
    /** 1 = 转账转出方，2 = 转账转入方 */
    val relatedAccountType: Int = 0,
    /** 最小货币单位整数的字符串 */
    val amount: String = "0"
)

/** 趋势分析：按月分组，月份为 1-based，只含有数据的月份 */
@Serializable
data class TrendMonth(
    val year: Int = 0,
    val month: Int = 0,
    val items: List<StatisticItem> = emptyList()
)

/** 资产趋势：按天分组，只含有交易的日期 */
@Serializable
data class AssetTrendDay(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val items: List<AssetTrendAccount> = emptyList()
)

@Serializable
data class AssetTrendAccount(
    val accountId: String = "0",
    val accountOpeningBalance: String = "0",
    val accountClosingBalance: String = "0"
)

@Serializable
data class HealthResponse(
    val success: Boolean = false
)

@Serializable
data class VersionPayload(
    val version: String = "",
    val commitHash: String = "",
    val buildTime: String = ""
)

@Serializable
data class TwoFactorAuthResponse(
    val token: String = "",
    val need2FA: Boolean = false,
    val user: UserBasicInfo? = null
)
