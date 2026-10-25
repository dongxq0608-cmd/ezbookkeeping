package com.ezbook.mobile.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.EzBookRepository
import com.ezbook.mobile.data.model.CATEGORY_TYPE_EXPENSE
import com.ezbook.mobile.data.model.CATEGORY_TYPE_INCOME
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.util.Fmt
import com.ezbook.mobile.util.describeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 分析类型，对应网页端 StatisticsAnalysisType */
enum class StatsAnalysis(val label: String) {
    CATEGORY("分类分析"),
    TREND("趋势分析"),
    ASSET("资产趋势")
}

/** 分类分析图表类型，对应网页端 CategoricalChartType（移动端可用 Pie / Bar） */
enum class CategoryChartType(val label: String) {
    PIE("饼图"),
    BAR("柱状图")
}

/** 趋势分析图表类型，对应网页端 TrendChartType（Area / Column） */
enum class TrendChartType(val label: String) {
    COLUMN("柱状图"),
    AREA("面积图")
}

/** 资产趋势图表类型，对应网页端 AccountBalanceTrendChartType（Area / Column） */
enum class AssetChartType(val label: String) {
    AREA("面积图"),
    COLUMN("柱状图")
}

data class CategorySlice(
    val id: String,
    val name: String,
    val amount: Long
)

data class MonthPoint(
    val year: Int,
    val month: Int,
    val income: Long,
    val expense: Long
)

data class AssetPoint(
    val year: Int,
    val month: Int,
    val assets: Long,
    val liabilities: Long,
    val net: Long
)

data class StatsUiState(
    val analysis: StatsAnalysis = StatsAnalysis.CATEGORY,
    val rangeIndex: Int = 0,
    val ranges: List<String> = rangesOf(StatsAnalysis.CATEGORY),
    /** 使用自定义时间范围时覆盖预置区间 */
    val isCustomRange: Boolean = false,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    /** 分类分析：支出 / 收入 */
    val categoryType: Int = CATEGORY_TYPE_EXPENSE,
    val categoryChart: CategoryChartType = CategoryChartType.PIE,
    val trendChart: TrendChartType = TrendChartType.COLUMN,
    val assetChart: AssetChartType = AssetChartType.AREA,
    val loading: Boolean = true,
    val error: String? = null,
    val categorySlices: List<CategorySlice> = emptyList(),
    val categoryTotal: Long = 0,
    val trendPoints: List<MonthPoint> = emptyList(),
    val assetPoints: List<AssetPoint> = emptyList(),
    val currency: String = "CNY"
) {
    val trendIncome: Long get() = trendPoints.sumOf { it.income }
    val trendExpense: Long get() = trendPoints.sumOf { it.expense }
    val latestAsset: AssetPoint? get() = assetPoints.lastOrNull()

    /** 资产走势是否至少有一天有效余额，用于区分「无交易」与「加载中」 */
    val hasAssetBalance: Boolean
        get() = assetPoints.any { it.assets != 0L || it.liabilities != 0L }

    /** 当前分析类型下是否已有可展示内容，用于刷新时保留旧数据 */
    val hasContent: Boolean
        get() = when (analysis) {
            StatsAnalysis.CATEGORY -> categorySlices.isNotEmpty()
            StatsAnalysis.TREND -> trendPoints.isNotEmpty()
            StatsAnalysis.ASSET -> hasAssetBalance
        }

    /** 自定义区间标签（按月展示），未选择时显示「自定义」 */
    val customRangeLabel: String
        get() {
            val start = customStart ?: return "自定义"
            val end = customEnd ?: return "自定义"
            val from = YearMonth.from(start)
            val to = YearMonth.from(end)
            return if (from.year == to.year) {
                "${from.monthValue}月~${to.monthValue}月"
            } else {
                "${from.year}/${from.monthValue}~${to.year}/${to.monthValue}"
            }
        }
}

/** 各分析可选的日期区间 */
fun rangesOf(analysis: StatsAnalysis): List<String> = when (analysis) {
    StatsAnalysis.CATEGORY -> listOf("本月", "上月", "近 3 个月", "今年")
    StatsAnalysis.TREND -> listOf("近 6 个月", "近 12 个月", "今年")
    StatsAnalysis.ASSET -> listOf("近 3 个月", "近 6 个月", "今年")
}

private data class Window(
    val startEpoch: Long,
    val endEpoch: Long,
    val startYear: Int,
    val startMonth: Int,
    val endYear: Int,
    val endMonth: Int
) {
    val startYearMonth: String get() = "$startYear-$startMonth"
    val endYearMonth: String get() = "$endYear-$endMonth"
}

class StatisticsViewModel : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedOnce = false

    fun onEnter() {
        if (!loadedOnce) {
            reload()
        }
    }

    fun selectAnalysis(analysis: StatsAnalysis) {
        if (_state.value.analysis == analysis) return
        // 自定义时间范围与具体分析项无关，切换分析时保留
        _state.update {
            it.copy(
                analysis = analysis,
                rangeIndex = 0,
                ranges = rangesOf(analysis),
                loading = true,
                error = null
            )
        }
        reload()
    }

    fun selectRange(index: Int) {
        val current = _state.value
        if (!current.isCustomRange && current.rangeIndex == index) return
        _state.update { it.copy(rangeIndex = index, isCustomRange = false) }
        reload()
    }

    /** 自定义时间范围：以“月”为粒度，覆盖起止月份整月 */
    fun applyCustomMonthRange(start: YearMonth, end: YearMonth) {
        val from = if (start.isAfter(end)) end else start
        val to = if (start.isAfter(end)) start else end
        _state.update {
            it.copy(
                isCustomRange = true,
                customStart = from.atDay(1),
                customEnd = to.atEndOfMonth()
            )
        }
        reload()
    }

    fun setCategoryType(type: Int) {
        if (_state.value.categoryType == type) return
        _state.update { it.copy(categoryType = type) }
        reload()
    }

    /** 图表类型只影响渲染，不重新请求数据 */
    fun setCategoryChart(type: CategoryChartType) {
        _state.update { it.copy(categoryChart = type) }
    }

    fun setTrendChart(type: TrendChartType) {
        _state.update { it.copy(trendChart = type) }
    }

    fun setAssetChart(type: AssetChartType) {
        _state.update { it.copy(assetChart = type) }
    }

    fun refresh() = reload()

    private fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val snapshot = _state.value
            val window = windowOf(snapshot)

            try {
                when (snapshot.analysis) {
                    StatsAnalysis.CATEGORY -> loadCategory(window, snapshot.categoryType)
                    StatsAnalysis.TREND -> loadTrend(window)
                    StatsAnalysis.ASSET -> loadAsset(window)
                }
                loadedOnce = true
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        currency = Graph.store.user?.defaultCurrency?.takeIf { code -> code.isNotBlank() }
                            ?: "CNY"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = describeError(e)) }
            }
        }
    }

    /* ------------------------- 分类分析 ------------------------- */

    private suspend fun loadCategory(window: Window, categoryType: Int) {
        val items = Graph.repo.statistics(window.startEpoch, window.endEpoch)
        val tree = runCatching { Graph.repo.categories() }.getOrDefault(emptyList())
        val flat = EzBookRepository.flattenCategories(tree)

        val categoryTypeById = flat.associate { it.id to it.type }
        val names = flat.associate { it.id to it.name }
        val rootOf = rootCategoryMap(tree)
        val wanted = if (categoryType == CATEGORY_TYPE_INCOME) CATEGORY_TYPE_INCOME else CATEGORY_TYPE_EXPENSE

        val slices = items
            .filter { categoryTypeById[it.categoryId] == wanted }
            .groupBy { item -> rootOf[item.categoryId] ?: item.categoryId }
            .map { (rootId, list) ->
                CategorySlice(
                    id = rootId,
                    name = names[rootId] ?: "未分类",
                    amount = list.sumOf { Fmt.stringToCents(it.amount) }
                )
            }
            .filter { it.amount > 0 }
            .sortedByDescending { it.amount }

        _state.update {
            it.copy(
                categorySlices = slices,
                categoryTotal = slices.sumOf { slice -> slice.amount }
            )
        }
    }

    /* ------------------------- 趋势分析 ------------------------- */

    private suspend fun loadTrend(window: Window) {
        val months = Graph.repo.statisticsTrends(window.startYearMonth, window.endYearMonth)
        val tree = runCatching { Graph.repo.categories() }.getOrDefault(emptyList())
        val categoryTypeById = EzBookRepository.flattenCategories(tree).associate { it.id to it.type }

        val byKey = months.associateBy { it.year * 100 + it.month }

        val points = ArrayList<MonthPoint>()
        var cursor = LocalDate.of(window.startYear, window.startMonth, 1)
        val last = LocalDate.of(window.endYear, window.endMonth, 1)

        while (!cursor.isAfter(last)) {
            val key = cursor.year * 100 + cursor.monthValue
            val monthData = byKey[key]

            var income = 0L
            var expense = 0L
            monthData?.items?.forEach { item ->
                when (categoryTypeById[item.categoryId]) {
                    CATEGORY_TYPE_INCOME -> income += Fmt.stringToCents(item.amount)
                    CATEGORY_TYPE_EXPENSE -> expense += Fmt.stringToCents(item.amount)
                    else -> Unit
                }
            }

            points.add(
                MonthPoint(
                    year = cursor.year,
                    month = cursor.monthValue,
                    income = income,
                    expense = expense
                )
            )
            cursor = cursor.plusMonths(1)
        }

        _state.update { it.copy(trendPoints = points) }
    }

    /* ------------------------- 资产趋势 ------------------------- */

    private suspend fun loadAsset(window: Window) {
        val days = Graph.repo.assetTrends(window.startEpoch, window.endEpoch)
        val accounts = runCatching { Graph.repo.accounts() }.getOrDefault(emptyList())
        val flat = EzBookRepository.flattenAccounts(accounts)

        val assetIds = flat.filter { it.isAsset && !it.isLiability }.map { it.id }.toSet()
        val liabilityIds = flat.filter { it.isLiability }.map { it.id }.toSet()

        // 逐日更新余额快照，服务端只返回有交易的日期
        val running = HashMap<String, Long>()
        val snapshots = HashMap<Int, Map<String, Long>>()

        days
            .sortedWith(compareBy({ it.year }, { it.month }, { it.day }))
            .forEach { day ->
                day.items.forEach { item ->
                    running[item.accountId] = Fmt.stringToCents(item.accountClosingBalance)
                }
                snapshots[day.year * 100 + day.month] = HashMap(running)
            }

        val points = ArrayList<AssetPoint>()
        var cursor = LocalDate.of(window.startYear, window.startMonth, 1)
        val last = LocalDate.of(window.endYear, window.endMonth, 1)
        var lastSnapshot: Map<String, Long> = emptyMap()

        // 区间内第一个有交易的月份，之前的月份没有余额可推算，直接跳过
        val firstDataKey = snapshots.keys.minOrNull()

        while (!cursor.isAfter(last)) {
            val key = cursor.year * 100 + cursor.monthValue
            if (firstDataKey != null && key < firstDataKey) {
                cursor = cursor.plusMonths(1)
                continue
            }
            val snapshot = snapshots[key] ?: lastSnapshot
            lastSnapshot = snapshot

            var assets = 0L
            var liabilities = 0L
            snapshot.forEach { (accountId, balance) ->
                when (accountId) {
                    in assetIds -> assets += balance
                    in liabilityIds -> liabilities += balance
                    else -> Unit
                }
            }

            points.add(
                AssetPoint(
                    year = cursor.year,
                    month = cursor.monthValue,
                    assets = assets,
                    liabilities = liabilities,
                    // 负债余额本身为负数，净值 = 总资产 + 总负债
                    net = assets + liabilities
                )
            )
            cursor = cursor.plusMonths(1)
        }

        _state.update { it.copy(assetPoints = points) }
    }

    /* ------------------------- 工具 ------------------------- */

    private fun windowOf(state: StatsUiState): Window {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val firstOfMonth = today.withDayOfMonth(1)

        fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toEpochSecond()
        fun endOf(date: LocalDate): Long =
            date.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1

        val customStart = state.customStart
        val customEnd = state.customEnd

        val (from, to) = if (state.isCustomRange && customStart != null && customEnd != null) {
            customStart to customEnd
        } else {
            when (state.analysis) {
                StatsAnalysis.CATEGORY -> when (state.rangeIndex) {
                    0 -> firstOfMonth to today
                    1 -> firstOfMonth.minusMonths(1) to firstOfMonth.minusDays(1)
                    2 -> firstOfMonth.minusMonths(2) to today
                    else -> LocalDate.of(today.year, 1, 1) to today
                }

                StatsAnalysis.TREND -> when (state.rangeIndex) {
                    0 -> firstOfMonth.minusMonths(5) to today
                    1 -> firstOfMonth.minusMonths(11) to today
                    else -> LocalDate.of(today.year, 1, 1) to today
                }

                StatsAnalysis.ASSET -> when (state.rangeIndex) {
                    0 -> firstOfMonth.minusMonths(2) to today
                    1 -> firstOfMonth.minusMonths(5) to today
                    else -> LocalDate.of(today.year, 1, 1) to today
                }
            }
        }

        return Window(
            startEpoch = startOf(from),
            endEpoch = endOf(to),
            startYear = from.year,
            startMonth = from.monthValue,
            endYear = to.year,
            endMonth = to.monthValue
        )
    }

    private fun rootCategoryMap(tree: List<Category>): Map<String, String> {
        val result = HashMap<String, String>()

        fun walk(list: List<Category>, root: String?) {
            list.forEach { category ->
                val top = root ?: category.id
                result[category.id] = top
                walk(category.subCategories, top)
            }
        }

        walk(tree, null)
        return result
    }
}
