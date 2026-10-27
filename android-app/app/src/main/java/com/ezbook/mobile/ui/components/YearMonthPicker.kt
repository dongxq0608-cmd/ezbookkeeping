package com.ezbook.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.YearMonth
import com.ezbook.mobile.ui.theme.glassOverlayContainer

private const val MIN_YEAR = 2000
private val MAX_YEAR: Int get() = YearMonth.now().year + 1

/**
 * 只选择年月的弹窗（不含“日”），用于账单月份快速跳转
 */
@Composable
fun YearMonthPickerDialog(
    initial: YearMonth,
    title: String = "选择年月",
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    var year by remember { mutableIntStateOf(initial.year) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = glassOverlayContainer()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                YearSwitcher(
                    year = year,
                    onPrev = { if (year > MIN_YEAR) year-- },
                    onNext = { if (year < MAX_YEAR) year++ }
                )
                Spacer(Modifier.height(4.dp))
                MonthGrid(
                    selectedMonth = if (year == initial.year) initial.monthValue else null,
                    onSelect = { month -> onConfirm(YearMonth.of(year, month)) }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}

/**
 * 以“月”为粒度的起止月份选择弹窗
 */
@Composable
fun YearMonthRangeDialog(
    initialStart: YearMonth?,
    initialEnd: YearMonth?,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth, YearMonth) -> Unit
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var editingStart by remember { mutableStateOf(initialStart == null) }
    var year by remember { mutableIntStateOf((initialStart ?: YearMonth.now()).year) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = glassOverlayContainer()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "自定义月份范围",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeField(
                        label = "开始月份",
                        value = start,
                        active = editingStart,
                        onClick = {
                            editingStart = true
                            start?.let { year = it.year }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    RangeField(
                        label = "结束月份",
                        value = end,
                        active = !editingStart,
                        onClick = {
                            editingStart = false
                            end?.let { year = it.year }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                YearSwitcher(
                    year = year,
                    onPrev = { if (year > MIN_YEAR) year-- },
                    onNext = { if (year < MAX_YEAR) year++ }
                )
                Spacer(Modifier.height(4.dp))

                MonthGrid(
                    selectedMonth = if (editingStart) {
                        start?.takeIf { it.year == year }?.monthValue
                    } else {
                        end?.takeIf { it.year == year }?.monthValue
                    },
                    onSelect = { month ->
                        val picked = YearMonth.of(year, month)
                        if (editingStart) {
                            start = picked
                            if (end == null || end!!.isBefore(picked)) end = picked
                            editingStart = false
                            year = (end ?: picked).year
                        } else {
                            end = picked
                            if (start == null || start!!.isAfter(picked)) start = picked
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            val from = start
                            val to = end
                            if (from != null && to != null) {
                                if (from.isAfter(to)) onConfirm(to, from) else onConfirm(from, to)
                            }
                        },
                        enabled = start != null && end != null
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun YearSwitcher(
    year: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = year > MIN_YEAR) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "上一年")
        }
        Text(
            text = "$year 年",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(110.dp)
        )
        IconButton(onClick = onNext, enabled = year < MAX_YEAR) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "下一年")
        }
    }
}

@Composable
private fun MonthGrid(
    selectedMonth: Int?,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..3).forEach { column ->
                    val month = row * 3 + column
                    MonthCell(
                        month = month,
                        selected = month == selectedMonth,
                        onClick = { onSelect(month) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    month: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val idleBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedBg else idleBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${month}月",
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun RangeField(
    label: String,
    value: YearMonth?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value?.let { "${it.year}/${it.monthValue}" } ?: "未选择",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
