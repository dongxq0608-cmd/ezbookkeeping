package com.ezbook.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezbook.mobile.ui.components.HorizontalDivider
import com.ezbook.mobile.ui.components.LoadingBox
import com.ezbook.mobile.ui.theme.LedgerColors
import com.ezbook.mobile.ui.theme.LocalLedgerColors
import com.ezbook.mobile.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenSettings: () -> Unit,
    viewModel: OverviewViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalLedgerColors.current
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) { viewModel.onEnter() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "概览",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${today.monthValue}月${today.dayOfMonth}日 ${weekdayText(today.dayOfWeek)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "我的",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.loading && !state.hasData -> LoadingBox()

                state.error != null && !state.hasData -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
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
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    state.error?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item { TodayCard(state, colors) }
                    item { RangeCard(state, colors) }
                }
            }
        }
    }
}

private fun weekdayText(dayOfWeek: DayOfWeek): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)

/* ------------------------- 今天 ------------------------- */

@Composable
private fun TodayCard(state: OverviewUiState, colors: LedgerColors) {
    val stat = state.stat(OverviewRange.TODAY)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "今天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                AmountBlock(
                    label = "支出",
                    amount = stat.expense,
                    color = colors.expense,
                    currency = state.currency,
                    big = true,
                    modifier = Modifier.weight(1f)
                )
                AmountBlock(
                    label = "收入",
                    amount = stat.income,
                    color = colors.income,
                    currency = state.currency,
                    big = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "结余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Fmt.moneyWithSymbol(stat.balance, state.currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/* ------------------------- 本周 / 本月 / 今年 ------------------------- */

@Composable
private fun RangeCard(state: OverviewUiState, colors: LedgerColors) {
    val ranges = remember { listOf(OverviewRange.WEEK, OverviewRange.MONTH, OverviewRange.YEAR) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "支出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "收入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.2f)
                )
            }

            ranges.forEachIndexed { index, range ->
                val stat = state.stat(range)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = range.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Fmt.moneyWithSymbol(stat.expense, state.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.expense,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = Fmt.moneyWithSymbol(stat.income, state.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.income,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                if (index < ranges.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

/* ------------------------- 通用 ------------------------- */

@Composable
private fun AmountBlock(
    label: String,
    amount: Long,
    color: Color,
    currency: String,
    big: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = Fmt.moneyWithSymbol(amount, currency),
            fontSize = if (big) 22.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
