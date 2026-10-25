package com.ezbook.mobile.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezbook.mobile.data.model.CATEGORY_TYPE_EXPENSE
import com.ezbook.mobile.data.model.CATEGORY_TYPE_INCOME
import com.ezbook.mobile.ui.components.EmptyState
import com.ezbook.mobile.ui.components.LoadingBox
import com.ezbook.mobile.ui.components.YearMonthRangeDialog
import com.ezbook.mobile.ui.theme.LocalLedgerColors
import com.ezbook.mobile.util.Fmt
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onOpenCategoryTransactions: (categoryId: String, name: String, categoryType: Int) -> Unit = { _, _, _ -> },
    viewModel: StatisticsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalLedgerColors.current
    var showCustomRangePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.onEnter() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "统计",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val analyses = StatsAnalysis.entries
            analyses.forEachIndexed { index, analysis ->
                SegmentedButton(
                    selected = state.analysis == analysis,
                    onClick = { viewModel.selectAnalysis(analysis) },
                    shape = SegmentedButtonDefaults.itemShape(index, analyses.size),
                    label = { Text(analysis.label, fontSize = 13.sp, maxLines = 1) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.isCustomRange,
                onClick = { showCustomRangePicker = true },
                label = { Text(state.customRangeLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            state.ranges.forEachIndexed { index, label ->
                FilterChip(
                    selected = !state.isCustomRange && state.rangeIndex == index,
                    onClick = { viewModel.selectRange(index) },
                    label = { Text(label) }
                )
            }
        }

        if (showCustomRangePicker) {
            YearMonthRangeDialog(
                initialStart = state.customStart?.let { YearMonth.from(it) },
                initialEnd = state.customEnd?.let { YearMonth.from(it) },
                onDismiss = { showCustomRangePicker = false },
                onConfirm = { start, end ->
                    showCustomRangePicker = false
                    viewModel.applyCustomMonthRange(start, end)
                }
            )
        }

        PullToRefreshBox(
            isRefreshing = state.loading && state.hasContent,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.loading && !state.hasContent -> LoadingBox()

                state.error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    when (state.analysis) {
                        StatsAnalysis.CATEGORY -> categoryContent(state, viewModel, onOpenCategoryTransactions)
                        StatsAnalysis.TREND -> trendContent(state, colors, viewModel)
                        StatsAnalysis.ASSET -> assetContent(state, colors, viewModel)
                    }
                }
            }
        }
    }
}

/* ------------------------- 分类分析 ------------------------- */

private fun androidx.compose.foundation.lazy.LazyListScope.categoryContent(
    state: StatsUiState,
    viewModel: StatisticsViewModel,
    onOpenCategoryTransactions: (String, String, Int) -> Unit
) {
    val isExpense = state.categoryType == CATEGORY_TYPE_EXPENSE

    item {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isExpense,
                    onClick = { viewModel.setCategoryType(CATEGORY_TYPE_EXPENSE) },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = { viewModel.setCategoryType(CATEGORY_TYPE_INCOME) },
                    label = { Text("收入") }
                )
            }
            ChartTypeChips(
                options = CategoryChartType.entries,
                selected = state.categoryChart,
                labelOf = { it.label },
                onSelect = { viewModel.setCategoryChart(it) }
            )
        }
    }

    if (state.categorySlices.isEmpty()) {
        item { EmptyState("该时间段没有数据", "换个区间试试") }
        return
    }

    val palette = state.categorySlices.mapIndexed { index, _ ->
        chartPalette[index % chartPalette.size]
    }

    if (state.categoryChart == CategoryChartType.PIE) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DonutChart(
                        values = state.categorySlices.map { it.amount },
                        colors = palette,
                        centerLabel = if (isExpense) "总支出" else "总收入",
                        centerValue = Fmt.moneyWithSymbol(state.categoryTotal, state.currency)
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.categorySlices.forEachIndexed { index, slice ->
                        val fraction = if (state.categoryTotal > 0) {
                            slice.amount.toFloat() / state.categoryTotal.toFloat()
                        } else {
                            0f
                        }
                        SliceRow(
                            name = slice.name,
                            amount = slice.amount,
                            fraction = fraction,
                            color = palette[index % palette.size],
                            currency = state.currency,
                            onClick = {
                                onOpenCategoryTransactions(slice.id, slice.name, state.categoryType)
                            }
                        )
                    }
                }
            }
        }
    } else {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isExpense) "总支出" else "总收入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = Fmt.moneyWithSymbol(state.categoryTotal, state.currency),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    CategoryBarChart(
                        names = state.categorySlices.map { it.name },
                        values = state.categorySlices.map { it.amount },
                        colors = palette,
                        currency = state.currency,
                        onClick = { index ->
                            state.categorySlices.getOrNull(index)?.let { slice ->
                                onOpenCategoryTransactions(slice.id, slice.name, state.categoryType)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 图表类型切换（对应网页端图表类型的自由切换） */
@Composable
private fun <T> ChartTypeChips(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), fontSize = 13.sp) }
            )
        }
    }
}

@Composable
private fun SliceRow(
    name: String,
    amount: Long,
    fraction: Float,
    color: Color,
    currency: String,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = percentText(fraction),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = Fmt.moneyWithSymbol(amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 17.dp)
                .height(5.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(3.dp)
                )
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

private fun percentText(fraction: Float): String = when {
    fraction <= 0f -> "0%"
    fraction * 100f < 1f -> "<1%"
    else -> "${(fraction * 100).toInt()}%"
}

/* ------------------------- 趋势分析 ------------------------- */

private fun androidx.compose.foundation.lazy.LazyListScope.trendContent(
    state: StatsUiState,
    colors: com.ezbook.mobile.ui.theme.LedgerColors,
    viewModel: StatisticsViewModel
) {
    if (state.trendPoints.isEmpty()) {
        item { EmptyState("没有趋势数据") }
        return
    }

    item {
        ChartTypeChips(
            options = TrendChartType.entries,
            selected = state.trendChart,
            labelOf = { it.label },
            onSelect = { viewModel.setTrendChart(it) }
        )
    }

    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryBlock(
                    label = "收入",
                    value = Fmt.moneyWithSymbol(state.trendIncome, state.currency),
                    color = colors.income,
                    modifier = Modifier.weight(1f)
                )
                SummaryBlock(
                    label = "支出",
                    value = Fmt.moneyWithSymbol(state.trendExpense, state.currency),
                    color = colors.expense,
                    modifier = Modifier.weight(1f)
                )
                SummaryBlock(
                    label = "结余",
                    value = Fmt.moneyWithSymbol(state.trendIncome - state.trendExpense, state.currency),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendDot("收入", colors.income)
                    LegendDot("支出", colors.expense)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.trendPoints.size} 个月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (state.trendChart == TrendChartType.COLUMN) {
                    MonthlyColumnChart(
                        points = state.trendPoints,
                        incomeColor = colors.income,
                        expenseColor = colors.expense
                    )
                } else {
                    TrendAreaChart(
                        points = state.trendPoints,
                        incomeColor = colors.income,
                        expenseColor = colors.expense
                    )
                }
            }
        }
    }

    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "月度明细",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                state.trendPoints.reversed().forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${point.year}年${point.month}月",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(88.dp)
                        )
                        Text(
                            text = Fmt.moneyWithSymbol(point.income, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.income,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Fmt.moneyWithSymbol(point.expense, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.expense,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Fmt.moneyWithSymbol(point.income - point.expense, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------- 资产趋势 ------------------------- */

private fun androidx.compose.foundation.lazy.LazyListScope.assetContent(
    state: StatsUiState,
    colors: com.ezbook.mobile.ui.theme.LedgerColors,
    viewModel: StatisticsViewModel
) {
    val latest = state.latestAsset
    if (latest == null || state.assetPoints.isEmpty() || !state.hasAssetBalance) {
        item { EmptyState("没有资产数据", "该区间内没有交易，无法推算余额走势") }
        return
    }

    item {
        ChartTypeChips(
            options = AssetChartType.entries,
            selected = state.assetChart,
            labelOf = { it.label },
            onSelect = { viewModel.setAssetChart(it) }
        )
    }

    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryBlock(
                    label = "总资产",
                    value = Fmt.moneyWithSymbol(latest.assets, state.currency),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SummaryBlock(
                    label = "总负债",
                    value = Fmt.moneyWithSymbol(latest.liabilities, state.currency),
                    color = colors.expense,
                    modifier = Modifier.weight(1f)
                )
                SummaryBlock(
                    label = "净资产",
                    value = Fmt.moneyWithSymbol(latest.net, state.currency),
                    color = colors.income,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LegendDot("总资产", colors.asset)
                    LegendDot("总负债", colors.expense)
                    LegendDot("净值", colors.income)
                }

                Spacer(Modifier.height(16.dp))

                if (state.assetChart == AssetChartType.AREA) {
                    MultiLineChart(
                        series = listOf(
                            LineSeries(
                                label = "总资产",
                                color = colors.asset,
                                values = state.assetPoints.map { it.assets }
                            ),
                            LineSeries(
                                label = "总负债",
                                color = colors.expense,
                                values = state.assetPoints.map { it.liabilities }
                            ),
                            LineSeries(
                                label = "净值",
                                color = colors.income,
                                values = state.assetPoints.map { it.net }
                            )
                        ),
                        labels = state.assetPoints.map { "${it.month}" }
                    )
                } else {
                    AssetColumnChart(
                        points = state.assetPoints,
                        assetColor = colors.asset,
                        liabilityColor = colors.expense,
                        netColor = colors.income
                    )
                }
            }
        }
    }

    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "月度明细",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                state.assetPoints.reversed().forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${point.year}年${point.month}月",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(88.dp)
                        )
                        Text(
                            text = Fmt.moneyWithSymbol(point.assets, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Fmt.moneyWithSymbol(point.liabilities, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.expense,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Fmt.moneyWithSymbol(point.net, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.income,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
