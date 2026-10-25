package com.ezbook.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.EzBookRepository
import com.ezbook.mobile.data.model.CATEGORY_TYPE_EXPENSE
import com.ezbook.mobile.data.model.CATEGORY_TYPE_INCOME
import com.ezbook.mobile.data.model.StatisticItem
import com.ezbook.mobile.util.Fmt
import com.ezbook.mobile.util.describeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 首页概览展示的时间段 */
enum class OverviewRange(val label: String) {
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
    YEAR("今年")
}

data class PeriodStat(
    val range: OverviewRange,
    val expense: Long = 0,
    val income: Long = 0
) {
    val balance: Long get() = income - expense
}

data class OverviewUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val currency: String = "CNY",
    val stats: List<PeriodStat> = emptyList()
) {
    val hasData: Boolean get() = stats.isNotEmpty()

    fun stat(range: OverviewRange): PeriodStat =
        stats.firstOrNull { it.range == range } ?: PeriodStat(range)
}

private data class PeriodWindow(
    val range: OverviewRange,
    val start: Long,
    val end: Long
)

class OverviewViewModel : ViewModel() {

    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedOnce = false

    /** 每次回到首页都会调用：首次显示整页加载，之后静默刷新 */
    fun onEnter() {
        reload(showLoading = !loadedOnce)
    }

    fun refresh() = reload(showLoading = false)

    private fun reload(showLoading: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                val fullScreenLoading = showLoading && !it.hasData
                it.copy(loading = fullScreenLoading, refreshing = !fullScreenLoading, error = null)
            }

            try {
                val categories = runCatching { Graph.repo.categories() }.getOrDefault(emptyList())
                val typeById = EzBookRepository
                    .flattenCategories(categories)
                    .associate { it.id to it.type }

                val stats = coroutineScope {
                    buildWindows().map { window ->
                        async {
                            val items = Graph.repo.statistics(window.start, window.end)
                            PeriodStat(
                                range = window.range,
                                expense = sumOfType(items, typeById, CATEGORY_TYPE_EXPENSE),
                                income = sumOfType(items, typeById, CATEGORY_TYPE_INCOME)
                            )
                        }
                    }.awaitAll()
                }

                loadedOnce = true
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        stats = stats,
                        currency = currencyOf(),
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = describeError(e))
                }
            }
        }
    }

    private fun currencyOf(): String =
        Graph.store.user?.defaultCurrency?.takeIf { it.isNotBlank() } ?: "CNY"

    private fun sumOfType(
        items: List<StatisticItem>,
        typeById: Map<String, Int>,
        type: Int
    ): Long = items
        .filter { typeById[it.categoryId] == type }
        .sumOf { Fmt.stringToCents(it.amount) }

    private fun buildWindows(): List<PeriodWindow> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val firstOfMonth = today.withDayOfMonth(1)
        val firstOfYear = today.withDayOfYear(1)

        // 服务端 firstDayOfWeek：0 = 周日，1 = 周一
        val mondayFirst = (Graph.store.user?.firstDayOfWeek ?: 0) == 1
        val firstOfWeek = today.with(
            TemporalAdjusters.previousOrSame(
                if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
            )
        )

        fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toEpochSecond()
        fun endOf(date: LocalDate): Long =
            date.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1

        return listOf(
            PeriodWindow(OverviewRange.TODAY, startOf(today), endOf(today)),
            PeriodWindow(OverviewRange.WEEK, startOf(firstOfWeek), endOf(today)),
            PeriodWindow(OverviewRange.MONTH, startOf(firstOfMonth), endOf(today)),
            PeriodWindow(OverviewRange.YEAR, startOf(firstOfYear), endOf(today))
        )
    }
}
