package com.ezbook.mobile.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.EzBookRepository
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.data.model.CATEGORY_TYPE_EXPENSE
import com.ezbook.mobile.data.model.CATEGORY_TYPE_INCOME
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.data.model.TX_TYPE_EXPENSE
import com.ezbook.mobile.data.model.TX_TYPE_INCOME
import com.ezbook.mobile.data.model.TransactionItem
import com.ezbook.mobile.util.Dates
import com.ezbook.mobile.util.describeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class TransactionListUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val filterType: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<TransactionItem> = emptyList(),
    val accountsById: Map<String, Account> = emptyMap(),
    val categoriesById: Map<String, Category> = emptyMap(),
    /** 分类树（父分类带子分类），用于类别筛选面板 */
    val categoryTree: List<Category> = emptyList(),
    /** 已选中的分类：选中父分类时其子分类的账单同样命中 */
    val selectedCategoryIds: Set<String> = emptySet(),
    /** 账户过滤：非空时只加载该账户（含子账户）的明细 */
    val accountId: String? = null,
    val accountName: String = "",
    /** 正在向前加载更早的一周 */
    val loadingMore: Boolean = false,
    /** 是否还可能存在更早的明细 */
    val hasMore: Boolean = false,
    /** 已展开的周数：默认 1 周，滚动到底部再加一周 */
    val weeksLoaded: Int = 1,
    val error: String? = null
) {
    val hasCategoryFilter: Boolean get() = selectedCategoryIds.isNotEmpty()

    val hasAccountFilter: Boolean get() = !accountId.isNullOrBlank()

    /** 应用类别筛选后的账单（整月，日历视图使用） */
    val visibleItems: List<TransactionItem>
        get() = if (selectedCategoryIds.isEmpty()) {
            items
        } else {
            items.filter { transaction ->
                transaction.categoryId in selectedCategoryIds ||
                    categoriesById[transaction.categoryId]?.parentId in selectedCategoryIds
            }
        }

    /** 一周窗口的起始日：当月从今天往前，历史月份从月末往前 */
    val windowStartDay: LocalDate
        get() {
            val today = LocalDate.now()
            val monthFirst = LocalDate.of(year, month, 1)
            val monthLast = monthFirst.plusMonths(1).minusDays(1)
            val anchor = if (today.isAfter(monthLast)) monthLast else today
            return anchor.minusDays(weeksLoaded.toLong() * 7 - 1)
        }

    /** 列表实际展示的账单：只包含当前一周窗口内的（默认最近 7 天） */
    val listItems: List<TransactionItem>
        get() {
            val since = Dates.toEpochSeconds(windowStartDay, LocalTime.MIN)
            return visibleItems.filter { it.time >= since }
        }


    val incomeTotal: Long
        get() = visibleItems.filter { it.type == TX_TYPE_INCOME }.sumOf { it.sourceAmount }

    val expenseTotal: Long
        get() = visibleItems.filter { it.type == TX_TYPE_EXPENSE }.sumOf { it.sourceAmount }

    val netTotal: Long get() = incomeTotal - expenseTotal

    /** 类别筛选标签文案 */
    val categoryFilterLabel: String
        get() {
            if (selectedCategoryIds.isEmpty()) return "类别"
            if (selectedCategoryIds.size == 1) {
                val id = selectedCategoryIds.first()
                return categoriesById[id]?.name ?: "类别"
            }
            return "类别 · ${selectedCategoryIds.size}"
        }
}

class TransactionListViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        TransactionListUiState(
            year = LocalDate.now().year,
            month = LocalDate.now().monthValue
        )
    )
    val state: StateFlow<TransactionListUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadMoreJob: Job? = null
    private var loadedOnce = false

    /** 已加载的月份页数（含当月） */
    private var pagesLoaded = 0

    /** 已加载到更早月份仍没有任何账单，可停止继续拉取 */
    private var reachedStart = false

    fun onEnter() {
        if (!loadedOnce) {
            reload(showLoading = true)
        } else {
            reload(showLoading = false)
        }
    }

    fun refresh() = reload(showLoading = false)

    fun shiftMonth(delta: Int) {
        val current = _state.value
        val target = LocalDate.of(current.year, current.month, 1).plusMonths(delta.toLong())
        _state.update { it.copy(year = target.year, month = target.monthValue, weeksLoaded = 1) }
        reload(showLoading = true)
    }

    fun goToCurrentMonth() {
        val today = LocalDate.now()
        val current = _state.value
        if (current.year == today.year && current.month == today.monthValue) return
        _state.update { it.copy(year = today.year, month = today.monthValue, weeksLoaded = 1) }
        reload(showLoading = true)
    }

    /** 通过日历快速跳转到指定年月 */
    fun goToMonth(year: Int, month: Int) {
        val current = _state.value
        if (current.year == year && current.month == month) return
        _state.update { it.copy(year = year, month = month, weeksLoaded = 1) }
        reload(showLoading = true)
    }

    fun setFilter(type: Int) {
        if (_state.value.filterType == type) return
        _state.update { it.copy(filterType = type) }
        reload(showLoading = true)
    }

    /** 切换某个分类（父分类被选中时，子分类账单也会命中） */
    fun toggleCategory(categoryId: String) {
        _state.update {
            val selected = it.selectedCategoryIds
            it.copy(
                selectedCategoryIds = if (categoryId in selected) {
                    selected - categoryId
                } else {
                    selected + categoryId
                }
            )
        }
    }

    fun clearCategoryFilter() {
        if (_state.value.selectedCategoryIds.isEmpty()) return
        _state.update { it.copy(selectedCategoryIds = emptySet()) }
    }

    /**
     * 从统计页跳转：定位到指定分类（含子分类）的账单，并按分类类型切换支出 / 收入筛选
     */
    fun applyCategoryFilter(categoryId: String, categoryType: Int?) {
        val txType = when (categoryType) {
            CATEGORY_TYPE_EXPENSE -> TX_TYPE_EXPENSE
            CATEGORY_TYPE_INCOME -> TX_TYPE_INCOME
            else -> null
        }

        if (categoryId.isBlank() || categoryId == "0") {
            clearCategoryFilter()
            return
        }

        val needReload = txType != null && txType != _state.value.filterType
        _state.update {
            it.copy(
                selectedCategoryIds = setOf(categoryId),
                filterType = txType ?: it.filterType
            )
        }
        if (needReload) reload(showLoading = true)
    }

    /**
     * 从账户页跳转：只看该账户（含子账户）的明细，并从当月开始往前逐月加载
     */
    fun applyAccountFilter(accountId: String, accountName: String) {
        if (accountId.isBlank() || accountId == "0") {
            clearAccountFilter()
            return
        }
        val today = LocalDate.now()
        val current = _state.value
        if (current.accountId == accountId) return
        _state.update {
            it.copy(
                accountId = accountId,
                accountName = accountName,
                year = today.year,
                month = today.monthValue,
                selectedCategoryIds = emptySet(),
                weeksLoaded = 1
            )
        }
        pagesLoaded = 0
        reachedStart = false
        reload(showLoading = true)
    }

    /** 退出账户过滤，回到正常按月浏览 */
    fun clearAccountFilter() {
        if (_state.value.accountId == null) return
        val today = LocalDate.now()
        _state.update {
            it.copy(
                accountId = null,
                accountName = "",
                loadingMore = false,
                hasMore = false,
                year = today.year,
                month = today.monthValue,
                weeksLoaded = 1
            )
        }
        pagesLoaded = 0
        reachedStart = false
        reload(showLoading = true)
    }

    /**
     * 重置为默认状态：清除账户 / 类别 / 类型过滤，回到当月
     */
    fun resetFilters() {
        val today = LocalDate.now()
        val current = _state.value
        val isDefault = current.accountId == null &&
            current.selectedCategoryIds.isEmpty() &&
            current.filterType == 0 &&
            current.year == today.year &&
            current.month == today.monthValue
        if (isDefault) return

        _state.update {
            it.copy(
                accountId = null,
                accountName = "",
                selectedCategoryIds = emptySet(),
                filterType = 0,
                loadingMore = false,
                hasMore = false,
                year = today.year,
                month = today.monthValue,
                weeksLoaded = 1
            )
        }
        pagesLoaded = 0
        reachedStart = false
        reload(showLoading = false)
    }

    /**
     * 向前加载更早的一周（列表滚到底部时触发）。
     *
     * 数据仍按「月」从服务端拉取（日历与月度汇总都需要整月数据），但列表一次只展示一周；
     * 当窗口越过已加载月份的第一天时，再拉取上一个月。
     */
    fun loadOlderWeek() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore) return
        if (reachedStart) return

        val oldestMonth = YearMonth.of(snapshot.year, snapshot.month)
            .minusMonths((pagesLoaded - 1).coerceAtLeast(0).toLong())
        val nextStart = snapshot.windowStartDay.minusDays(7)

        // 窗口还在已加载的月份内，直接展开，无需请求
        if (!nextStart.isBefore(oldestMonth.atDay(1))) {
            _state.update { it.copy(weeksLoaded = it.weeksLoaded + 1, hasMore = true) }
            return
        }

        val target = oldestMonth.minusMonths(1)
        val accountId = snapshot.accountId

        loadMoreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = Graph.repo.monthTransactions(
                    target.year,
                    target.monthValue,
                    snapshot.filterType,
                    accountId
                )
                val latest = _state.value
                if (latest.accountId != accountId || latest.year != snapshot.year || latest.month != snapshot.month) {
                    return@launch
                }
                // 整月没有账单：认为已经加载到最早
                if (page.isEmpty()) reachedStart = true
                pagesLoaded += 1
                _state.update { current ->
                    val known = current.items.mapTo(HashSet()) { it.id }
                    current.copy(
                        loadingMore = false,
                        weeksLoaded = current.weeksLoaded + 1,
                        hasMore = !reachedStart,
                        items = current.items + page.filter { it.id !in known },
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = describeError(e)) }
            }
        }
    }

    /** 删除一笔账单（账户明细左滑删除） */
    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                Graph.repo.deleteTransaction(id)
                _state.update { current ->
                    current.copy(items = current.items.filterNot { it.id == id }, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = describeError(e)) }
            }
        }
    }

    private fun reload(showLoading: Boolean) {
        job?.cancel()
        loadMoreJob?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = showLoading,
                    refreshing = !showLoading,
                    loadingMore = false,
                    error = null
                )
            }

            val snapshot = _state.value
            val accountId = snapshot.accountId

            try {
                // 刷新时保留已经展开的月份，重新拉取整段数据
                val pages = pagesLoaded.coerceAtLeast(1)
                val items = ArrayList<TransactionItem>()
                var reachedEnd = false
                for (offset in 0 until pages) {
                    val target = LocalDate.of(snapshot.year, snapshot.month, 1)
                        .minusMonths(offset.toLong())
                    val page = Graph.repo.monthTransactions(
                        target.year,
                        target.monthValue,
                        snapshot.filterType,
                        accountId
                    )
                    // 非当月的整个月没有明细时，认为已经加载到最早（当月为空仍允许继续往前翻）
                    if (page.isEmpty() && offset > 0) {
                        pagesLoaded = offset
                        reachedEnd = true
                        break
                    }
                    items.addAll(page)
                    pagesLoaded = offset + 1
                }
                reachedStart = reachedEnd

                val accounts = runCatching { Graph.repo.accounts() }.getOrDefault(emptyList())
                val categories = runCatching { Graph.repo.categories() }.getOrDefault(emptyList())

                loadedOnce = true

                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        items = items,
                        hasMore = !reachedEnd,
                        accountsById = EzBookRepository.flattenAccounts(accounts).associateBy { a -> a.id },
                        categoriesById = EzBookRepository.flattenCategories(categories).associateBy { c -> c.id },
                        categoryTree = categories,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = describeError(e)
                    )
                }
            }
        }
    }
}
