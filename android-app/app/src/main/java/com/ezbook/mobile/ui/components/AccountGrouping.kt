package com.ezbook.mobile.ui.components

import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.util.Fmt

/**
 * 账户分类，名称与顺序对齐网页端 AccountCategory（src/core/account.ts）
 */
data class AccountCategoryMeta(
    val type: Int,
    val name: String,
    val liability: Boolean
)

val accountCategoryMetas = listOf(
    AccountCategoryMeta(type = 1, name = "现金", liability = false),
    AccountCategoryMeta(type = 2, name = "借记账户", liability = false),
    AccountCategoryMeta(type = 8, name = "储蓄账户", liability = false),
    AccountCategoryMeta(type = 3, name = "信用卡", liability = true),
    AccountCategoryMeta(type = 4, name = "虚拟账户", liability = false),
    AccountCategoryMeta(type = 5, name = "负债账户", liability = true),
    AccountCategoryMeta(type = 6, name = "应收款项", liability = false),
    AccountCategoryMeta(type = 9, name = "存单", liability = false),
    AccountCategoryMeta(type = 7, name = "投资账户", liability = false)
)

private const val OTHER_CATEGORY_TITLE = "其他账户"

data class AccountGroup(
    val title: String,
    val liability: Boolean,
    /** 一级账户 + 其二级子账户，已按 displayOrder 排好 */
    val rows: List<Account>,
    /** 该分类一级账户余额之和（原始值，负债为负） */
    val total: Long
)

/**
 * 按账户分类分组，组内保持「一级账户 → 二级子账户」层级。
 *
 * @param keyword 模糊查询关键字，为空时返回全部；命中子账户时会连同其一级账户一起显示
 */
fun groupAccounts(roots: List<Account>, keyword: String = ""): List<AccountGroup> {
    val kw = keyword.trim()
    val knownTypes = accountCategoryMetas.map { it.type }.toSet()

    fun build(title: String, liability: Boolean, candidates: List<Account>): AccountGroup? {
        if (candidates.isEmpty()) {
            return null
        }

        val ordered = candidates.sortedBy { it.displayOrder }
        val rows = ArrayList<Account>()

        ordered.forEach { root ->
            val children = root.subAccounts.sortedBy { it.displayOrder }
            val rootMatched = kw.isEmpty() || root.name.contains(kw, ignoreCase = true)
            val matchedChildren = when {
                kw.isEmpty() || rootMatched -> children
                else -> children.filter { it.name.contains(kw, ignoreCase = true) }
            }
            if (rootMatched || matchedChildren.isNotEmpty()) {
                rows.add(root)
                rows.addAll(matchedChildren)
            }
        }

        if (rows.isEmpty()) {
            return null
        }

        val total = ordered.sumOf { Fmt.stringToCents(it.balance) }
        return AccountGroup(title = title, liability = liability, rows = rows, total = total)
    }

    val result = ArrayList<AccountGroup>()

    accountCategoryMetas.forEach { meta ->
        build(
            title = meta.name,
            liability = meta.liability,
            candidates = roots.filter { it.category == meta.type }
        )?.let { result.add(it) }
    }

    build(
        title = OTHER_CATEGORY_TITLE,
        liability = false,
        candidates = roots.filter { it.category !in knownTypes }
    )?.let { result.add(it) }

    return result
}

/** 子账户可能没有 isLiability 标记，向上找一级账户 */
fun isLiabilityAccount(account: Account, all: List<Account>): Boolean {
    if (account.isLiability) {
        return true
    }
    var parentId = account.parentId
    var guard = 0
    while (parentId != "0" && guard < 4) {
        val parent = all.firstOrNull { it.id == parentId } ?: return false
        if (parent.isLiability) {
            return true
        }
        parentId = parent.parentId
        guard++
    }
    return false
}
