package com.ezbook.mobile.data

import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.data.model.AssetTrendDay
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.data.model.StatisticItem
import com.ezbook.mobile.data.model.Tag
import com.ezbook.mobile.data.model.TrendMonth
import com.ezbook.mobile.data.model.TransactionItem
import com.ezbook.mobile.data.model.TransactionSaveRequest
import com.ezbook.mobile.data.remote.ApiClient

/**
 * 面向界面的数据仓库，负责基础数据（账户 / 分类 / 标签）的内存缓存。
 */
class EzBookRepository(private val api: ApiClient) {

    private var accountsCache: List<Account>? = null
    private var categoriesCache: List<Category>? = null
    private var tagsCache: List<Tag>? = null

    suspend fun accounts(force: Boolean = false): List<Account> {
        if (!force) {
            accountsCache?.let { return it }
        }
        val list = api.accounts(visibleOnly = false)
        accountsCache = list
        return list
    }

    suspend fun categories(force: Boolean = false): List<Category> {
        if (!force) {
            categoriesCache?.let { return it }
        }
        val tree = api.categories(type = 0)
        categoriesCache = tree
        return tree
    }

    suspend fun tags(force: Boolean = false): List<Tag> {
        if (!force) {
            tagsCache?.let { return it }
        }
        val list = api.tags()
        tagsCache = list
        return list
    }

    suspend fun monthTransactions(
        year: Int,
        month: Int,
        type: Int = 0,
        accountId: String? = null
    ): List<TransactionItem> = api.transactionsInMonth(year, month, type, accountId)

    suspend fun transaction(id: String): TransactionItem = api.transaction(id)

    suspend fun save(request: TransactionSaveRequest, isNew: Boolean) {
        if (isNew) {
            api.createTransaction(request)
        } else {
            api.modifyTransaction(request)
        }
    }

    suspend fun deleteTransaction(id: String) = api.deleteTransaction(id)

    suspend fun statistics(startTime: Long, endTime: Long): List<StatisticItem> =
        api.statistics(startTime, endTime)

    suspend fun statisticsTrends(startYearMonth: String, endYearMonth: String): List<TrendMonth> =
        api.statisticsTrends(startYearMonth, endYearMonth)

    suspend fun assetTrends(startTime: Long, endTime: Long): List<AssetTrendDay> =
        api.assetTrends(startTime, endTime)

    fun invalidate() {
        accountsCache = null
        categoriesCache = null
        tagsCache = null
    }

    companion object {
        /** 把账户树拍平（含子账户） */
        fun flattenAccounts(accounts: List<Account>): List<Account> {
            val result = ArrayList<Account>()
            fun walk(list: List<Account>) {
                list.forEach { account ->
                    result.add(account)
                    walk(account.subAccounts)
                }
            }
            walk(accounts)
            return result
        }

        /** 把分类树拍平 */
        fun flattenCategories(categories: List<Category>): List<Category> {
            val result = ArrayList<Category>()
            fun walk(list: List<Category>) {
                list.forEach { category ->
                    result.add(category)
                    walk(category.subCategories)
                }
            }
            walk(categories)
            return result
        }
    }
}
