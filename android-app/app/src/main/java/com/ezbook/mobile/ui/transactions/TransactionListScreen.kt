package com.ezbook.mobile.ui.transactions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.data.model.CATEGORY_TYPE_EXPENSE
import com.ezbook.mobile.data.model.CATEGORY_TYPE_INCOME
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.data.model.TX_TYPE_BALANCE
import com.ezbook.mobile.data.model.TX_TYPE_EXPENSE
import com.ezbook.mobile.data.model.TX_TYPE_INCOME
import com.ezbook.mobile.data.model.TX_TYPE_TRANSFER
import com.ezbook.mobile.data.model.TransactionItem
import com.ezbook.mobile.ui.components.AmountText
import com.ezbook.mobile.ui.components.EmptyState
import com.ezbook.mobile.ui.components.LedgerIconBadge
import com.ezbook.mobile.ui.components.LedgerIconKind
import com.ezbook.mobile.ui.components.LoadingBox
import com.ezbook.mobile.ui.components.YearMonthPickerDialog
import com.ezbook.mobile.ui.components.ledgerColorOf
import com.ezbook.mobile.ui.components.typeColor
import com.ezbook.mobile.ui.components.typeLabel
import com.ezbook.mobile.ui.theme.LocalLedgerColors
import com.ezbook.mobile.ui.theme.glassOverlayContainer
import com.ezbook.mobile.util.Dates
import com.ezbook.mobile.util.Fmt
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth

private data class DayGroup(
    val date: LocalDate,
    val items: List<TransactionItem>,
    val income: Long,
    val expense: Long
)

private val filterOptions = listOf(
    0 to "全部",
    TX_TYPE_EXPENSE to "支出",
    TX_TYPE_INCOME to "收入",
    TX_TYPE_TRANSFER to "转账"
)

/** 从其他页面跳转到账单明细时携带的分类筛选请求 */
data class CategoryFilterRequest(
    val categoryId: String,
    val categoryName: String,
    /** CATEGORY_TYPE_EXPENSE / CATEGORY_TYPE_INCOME */
    val categoryType: Int
)

/** 从账户页跳转到账单明细时携带的账户筛选请求 */
data class AccountFilterRequest(
    val accountId: String,
    val accountName: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionListScreen(
    onOpenTransaction: (String) -> Unit,
    onCopyTransaction: (String, Boolean) -> Unit = { _, _ -> },
    pendingCategory: CategoryFilterRequest? = null,
    onPendingCategoryHandled: () -> Unit = {},
    pendingAccount: AccountFilterRequest? = null,
    onPendingAccountHandled: () -> Unit = {},
    /** 每次从其他 Tab 重新进入账单页时递增，用于清除上一次的过滤条件 */
    resetToken: Int = 0,
    /** 记账保存后需要定位的明细 id / 时间 */
    focusTxId: String? = null,
    focusTxTime: Long = 0L,
    onFocusHandled: () -> Unit = {},
    viewModel: TransactionListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    var actionTarget by remember { mutableStateOf<TransactionItem?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val currency = Graph.store.user?.defaultCurrency?.takeIf { it.isNotBlank() } ?: "CNY"
    // 左滑删除同一时间只允许一行展开
    val swipeReveal = remember { SwipeRevealState() }

    LaunchedEffect(Unit) { viewModel.onEnter() }

    LaunchedEffect(pendingCategory) {
        val request = pendingCategory ?: return@LaunchedEffect
        viewModel.applyCategoryFilter(request.categoryId, request.categoryType)
        onPendingCategoryHandled()
    }

    LaunchedEffect(pendingAccount) {
        val request = pendingAccount ?: return@LaunchedEffect
        viewModel.applyAccountFilter(request.accountId, request.accountName)
        onPendingAccountHandled()
    }

    // 从其他 Tab 重新进入账单页时，清除上一次的过滤条件，回到默认状态
    LaunchedEffect(resetToken) {
        if (resetToken > 0) viewModel.resetFilters()
    }

    val monthItems = state.visibleItems
    // 列表一次只展示一周，日历仍使用整月数据
    val listItems = state.listItems
    val groups = remember(listItems) { buildDayGroups(listItems) }

    var calendarMode by rememberSaveable { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    // 账户明细模式是从当前月往前累计的列表，暂不提供日历视图
    val showCalendar = calendarMode && !state.hasAccountFilter

    val currentMonth = YearMonth.of(state.year, state.month)

    // 切换月份后重置选中日期：当月选今天，其余月份选 1 号
    LaunchedEffect(state.year, state.month) {
        val today = LocalDate.now()
        selectedDate = if (today.year == state.year && today.monthValue == state.month) {
            today
        } else {
            LocalDate.of(state.year, state.month, 1)
        }
    }

    // 日历只展示当前所选月份的每日收支（使用整月数据，不受一周窗口影响）
    val monthStats = remember(monthItems, currentMonth) {
        buildDayGroups(monthItems)
            .filter { YearMonth.from(it.date) == currentMonth }
            .associateBy { it.date }
    }
    val selectedGroup = monthStats[selectedDate]

    val listState = rememberLazyListState()

    // 记账保存后：让这笔明细进入当前列表窗口，再把它滚动到屏幕第一条
    LaunchedEffect(focusTxId) {
        if (focusTxId != null) viewModel.revealTransaction(focusTxTime)
    }
    LaunchedEffect(focusTxId, listItems, calendarMode) {
        val id = focusTxId ?: return@LaunchedEffect
        // 日历视图下按日期展示，无需定位
        if (calendarMode) return@LaunchedEffect
        val index = groups.indexOfTransaction(id)
        if (index >= 0) {
            listState.scrollToItem(index)
            onFocusHandled()
        }
    }
    // 滚动到底部附近时自动加载更早的一周
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - 2) viewModel.loadOlderWeek()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (state.hasAccountFilter) {
            AccountFilterBar(
                name = state.accountName,
                subtitle = buildString {
                    append(Dates.monthLabel(state.year, state.month))
                    append(" 起 · 共 ")
                    append(state.items.size)
                    append(" 笔")
                },
                onClear = { viewModel.clearAccountFilter() }
            )
        } else {
            MonthHeader(
                year = state.year,
                month = state.month,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
                onToday = { viewModel.goToCurrentMonth() },
                onPickMonth = { showMonthPicker = true },
                calendarMode = calendarMode,
                onToggleCalendar = {
                    val enterCalendar = !calendarMode
                    calendarMode = enterCalendar
                    if (enterCalendar) {
                        // 每次进入日历都重置为「今天」；若当前不在当月则先回到当月
                        val today = LocalDate.now()
                        if (state.year != today.year || state.month != today.monthValue) {
                            viewModel.goToCurrentMonth()
                        }
                        selectedDate = today
                    }
                }
            )
        }

        if (state.error != null && state.items.isNotEmpty()) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = ledgerColors.expense,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        if (showCalendar) {
            CalendarSection(
                year = state.year,
                month = state.month,
                selectedDate = selectedDate,
                stats = monthStats,
                onSelect = { selectedDate = it }
            )

            SelectedDayBar(
                date = selectedDate,
                group = selectedGroup,
                currency = currency
            )

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    val dayItems = selectedGroup?.items.orEmpty()
                    if (dayItems.isEmpty()) {
                        item(key = "day-empty") {
                            EmptyState(
                                title = "这一天没有账单",
                                subtitle = "在上方日历中选择其他日期，查看当天明细"
                            )
                        }
                    } else {
                        items(dayItems, key = { it.id }) { transaction ->
                            SwipeToDeleteRow(
                                rowId = transaction.id,
                                revealState = swipeReveal,
                                onDelete = { viewModel.deleteTransaction(transaction.id) }
                            ) {
                                TransactionRow(
                                    transaction = transaction,
                                    accounts = state.accountsById,
                                    categories = state.categoriesById,
                                    onClick = { onOpenTransaction(transaction.id) },
                                    onLongClick = { actionTarget = transaction }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            SummaryBar(
                income = state.incomeTotal,
                expense = state.expenseTotal,
                currency = currency
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.hasCategoryFilter,
                    onClick = { showCategorySheet = true },
                    label = { Text(state.categoryFilterLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                filterOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = state.filterType == type,
                        onClick = { viewModel.setFilter(type) },
                        label = { Text(label) }
                    )
                }
                if (state.hasCategoryFilter) {
                    TextButton(onClick = { viewModel.clearCategoryFilter() }) { Text("清除") }
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
                    state.loading && state.items.isEmpty() -> LoadingBox()
                    state.error != null && state.items.isEmpty() -> ErrorBlock(
                        message = state.error!!,
                        onRetry = { viewModel.refresh() }
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        if (groups.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = when {
                                        state.hasCategoryFilter -> "该类别下没有账单"
                                        state.filterType == 0 -> "最近一周还没有账单"
                                        else -> "没有符合条件的账单"
                                    },
                                    subtitle = if (state.hasCategoryFilter) {
                                        "可点上方「${state.categoryFilterLabel}」重新选择类别"
                                    } else {
                                        "上拉可加载更早的明细"
                                    }
                                )
                            }
                        }
                        var lastMonth: YearMonth? = null
                        groups.forEach { group ->
                            val groupMonth = YearMonth.from(group.date)
                            if (groupMonth != lastMonth) {
                                item(key = "m-$groupMonth") { MonthDividerRow(month = groupMonth) }
                                lastMonth = groupMonth
                            }
                            item(key = "d-${group.date}") {
                                DayHeaderRow(group = group, currency = currency)
                            }
                            items(group.items, key = { it.id }) { transaction ->
                                SwipeToDeleteRow(
                                    rowId = transaction.id,
                                    revealState = swipeReveal,
                                    onDelete = { viewModel.deleteTransaction(transaction.id) }
                                ) {
                                    TransactionRow(
                                        transaction = transaction,
                                        accounts = state.accountsById,
                                        categories = state.categoriesById,
                                        onClick = { onOpenTransaction(transaction.id) },
                                        onLongClick = { actionTarget = transaction }
                                    )
                                }
                            }
                        }
                        item(key = "load-more") {
                            LoadMoreRow(
                                loading = state.loadingMore,
                                hasMore = state.hasMore,
                                empty = groups.isEmpty(),
                                onLoadMore = { viewModel.loadOlderWeek() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        YearMonthPickerDialog(
            initial = YearMonth.of(state.year, state.month),
            onDismiss = { showMonthPicker = false },
            onConfirm = { picked ->
                showMonthPicker = false
                viewModel.goToMonth(picked.year, picked.monthValue)
            }
        )
    }

    if (showCategorySheet) {
        CategoryFilterSheet(
            state = state,
            onToggle = { viewModel.toggleCategory(it) },
            onClear = { viewModel.clearCategoryFilter() },
            onDismiss = { showCategorySheet = false }
        )
    }

actionTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = sheetState,
            containerColor = glassOverlayContainer(),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    text = titleOf(target, state.accountsById, state.categoriesById),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                ActionSheetItem("复制（时间改为当前时间）") {
                    actionTarget = null
                    onCopyTransaction(target.id, false)
                }
                ActionSheetItem("复制（含时间）") {
                    actionTarget = null
                    onCopyTransaction(target.id, true)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 账单日历：上半部分展示当月每日收支，点击某天后下方明细随之切换 */
@Composable
private fun CalendarSection(
    year: Int,
    month: Int,
    selectedDate: LocalDate,
    stats: Map<LocalDate, DayGroup>,
    onSelect: (LocalDate) -> Unit
) {
    val monthStart = LocalDate.of(year, month, 1)
    // 周一作为一周的第一天
    val leading = (monthStart.dayOfWeek.value + 6) % 7
    val daysInMonth = monthStart.lengthOfMonth()
    val today = LocalDate.now()

    val cells = remember(year, month) {
        val list = ArrayList<LocalDate?>(42)
        repeat(leading) { list.add(null) }
        for (day in 1..daysInMonth) {
            list.add(monthStart.withDayOfMonth(day))
        }
        while (list.size % 7 != 0) {
            list.add(null)
        }
        list
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                        } else {
                            CalendarDayCell(
                                modifier = Modifier.weight(1f),
                                date = date,
                                group = stats[date],
                                selected = date == selectedDate,
                                isToday = date == today,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 日历中的单日格子：日期 + 当日支出 / 收入 */
@Composable
private fun CalendarDayCell(
    date: LocalDate,
    group: DayGroup?,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val income = group?.income ?: 0L
    val expense = group?.expense ?: 0L
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .height(48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
        if (expense > 0) {
            Text(
                text = "-${Fmt.compactMoney(expense)}",
                fontSize = 7.5.sp,
                lineHeight = 9.sp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                } else {
                    ledgerColors.expense
                },
                maxLines = 1
            )
        }
        if (income > 0) {
            Text(
                text = "+${Fmt.compactMoney(income)}",
                fontSize = 7.5.sp,
                lineHeight = 9.sp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                } else {
                    ledgerColors.income
                },
                maxLines = 1
            )
        }
    }
}

/** 选中日期的标题行：日期 + 当日收支合计 */
@Composable
private fun SelectedDayBar(date: LocalDate, group: DayGroup?, currency: String) {
    val ledgerColors = LocalLedgerColors.current
    val income = group?.income ?: 0L
    val expense = group?.expense ?: 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Dates.dayHeader(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        if (income > 0) {
            Text(
                text = "+${Fmt.moneyWithSymbol(income, currency)}",
                fontSize = 11.sp,
                color = ledgerColors.income
            )
        }
        if (income > 0 && expense > 0) {
            Spacer(Modifier.width(8.dp))
        }
        if (expense > 0) {
            Text(
                text = "-${Fmt.moneyWithSymbol(expense, currency)}",
                fontSize = 11.sp,
                color = ledgerColors.expense
            )
        }
    }
}

/** 账户明细模式下的顶部过滤条：显示账户名与已加载范围，可一键回到全部账单 */
@Composable
private fun AccountFilterBar(
    name: String,
    subtitle: String,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "账户明细" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onClear) { Text("全部账单") }
        }
    }
}

/** 账户明细跨月展示时的月份分隔 */
@Composable
private fun MonthDividerRow(month: YearMonth) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Dates.monthLabel(month.year, month.monthValue),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        )
    }
}

private val DELETE_ACTION_WIDTH = 88.dp

/** 左滑删除的共享状态：同一时间只允许一行处于展开状态 */
private class SwipeRevealState {
    var openedId by mutableStateOf<String?>(null)
}

/**
 * 左滑露出右侧红色「删除」区域，点击后才真正删除。
 * 未滑开时不会响应点击，滑开后点内容区域则先收起；
 * 开始滑动某一行时，其它已展开的行会自动复位。
 */
@Composable
private fun SwipeToDeleteRow(
    rowId: String,
    revealState: SwipeRevealState,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val revealPx = with(LocalDensity.current) { DELETE_ACTION_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf(false) }

    // 别的行开始滑动 / 展开时，本行自动收起
    LaunchedEffect(revealState.openedId) {
        if (revealState.openedId != rowId && (revealed || offsetX.value != 0f)) {
            offsetX.animateTo(0f)
            revealed = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(revealPx, rowId) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // 抢占展开权，其它行随之收起
                        if (revealState.openedId != rowId) {
                            revealState.openedId = rowId
                        }
                    },
                    onDragEnd = {
                        val open = offsetX.value <= -revealPx / 2
                        scope.launch { offsetX.animateTo(if (open) -revealPx else 0f) }
                        revealed = open
                        if (open) {
                            revealState.openedId = rowId
                        } else if (revealState.openedId == rowId) {
                            revealState.openedId = null
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f) }
                        revealed = false
                        if (revealState.openedId == rowId) {
                            revealState.openedId = null
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealPx, 0f))
                    }
                }
            }
    ) {
        // 只绘制被滑开露出的那一段：未滑动时完全不绘制，
        // 否则半透明的毛玻璃行会把下方的红色「删除」透出来
        val revealedPx = (-offsetX.value).coerceIn(0f, revealPx)
        if (revealedPx > 0.5f) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(with(LocalDensity.current) { revealedPx.toDp() })
                        .fillMaxHeight()
                        .background(colors.error)
                        .clipToBounds()
                        .clickable(enabled = revealed) {
                            scope.launch { offsetX.snapTo(0f) }
                            revealed = false
                            if (revealState.openedId == rowId) {
                                revealState.openedId = null
                            }
                            onDelete()
                        }
                ) {
                    // 文案固定在右侧，随滑开宽度被裁切
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(DELETE_ACTION_WIDTH)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "删除",
                            color = colors.onError,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(colors.background)
        ) {
            content()
        }

        if (revealed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = DELETE_ACTION_WIDTH)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        scope.launch { offsetX.animateTo(0f) }
                        revealed = false
                        if (revealState.openedId == rowId) {
                            revealState.openedId = null
                        }
                    }
            )
        }
    }
}

/** 列表底部：自动加载更早月份，也可点击手动加载 */
@Composable
private fun LoadMoreRow(
    loading: Boolean,
    hasMore: Boolean,
    empty: Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在加载上一周…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            hasMore -> TextButton(onClick = onLoadMore) { Text("上拉或点击加载上一周") }

            else -> Text(
                text = if (empty) "还没有账单" else "没有更早的明细了",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickMonth: () -> Unit,
    calendarMode: Boolean,
    onToggleCalendar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickMonth)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Dates.monthLabel(year, month),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "选择年月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleCalendar) {
            Icon(
                imageVector = if (calendarMode) {
                    Icons.Filled.FormatListBulleted
                } else {
                    Icons.Filled.CalendarMonth
                },
                contentDescription = if (calendarMode) "切换到列表" else "切换到日历",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onToday) { Text("本月") }
    }
}

@Composable
private fun SummaryBar(income: Long, expense: Long, currency: String) {
    val colors = LocalLedgerColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                label = "收入",
                value = "${Fmt.currencySymbol(currency)}${Fmt.money(income)}",
                color = colors.income,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            SummaryItem(
                label = "支出",
                value = "${Fmt.currencySymbol(currency)}${Fmt.money(expense)}",
                color = colors.expense,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            SummaryItem(
                label = "结余",
                value = "${Fmt.currencySymbol(currency)}${Fmt.money(income - expense)}",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

@Composable
private fun DayHeaderRow(group: DayGroup, currency: String) {
    val colors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Dates.dayHeader(group.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (group.income > 0) {
            Text(
                text = "+${Fmt.money(group.income)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.income
            )
        }
        if (group.income > 0 && group.expense > 0) {
            Spacer(Modifier.width(8.dp))
        }
        if (group.expense > 0) {
            Text(
                text = "-${Fmt.money(group.expense)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.expense
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    transaction: TransactionItem,
    accounts: Map<String, Account>,
    categories: Map<String, Category>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = typeColor(transaction.type)
    val title = titleOf(transaction, accounts, categories)
    val subtitle = subtitleOf(transaction, accounts)

    val isAccountIcon = transaction.type == TX_TYPE_TRANSFER || transaction.type == TX_TYPE_BALANCE
    val iconKind = if (isAccountIcon) LedgerIconKind.ACCOUNT else LedgerIconKind.CATEGORY
    val category = transaction.category ?: categories[transaction.categoryId]
    val sourceAccount = transaction.sourceAccount ?: accounts[transaction.sourceAccountId]
    val iconId = if (isAccountIcon) sourceAccount?.icon else category?.icon
    val badgeColor = ledgerColorOf(
        if (isAccountIcon) sourceAccount?.color else category?.color,
        color
    )
    val currency = transaction.sourceAccount?.currency
        ?: Graph.store.user?.defaultCurrency?.takeIf { it.isNotBlank() }
        ?: "CNY"
    val amount = when (transaction.type) {
        TX_TYPE_TRANSFER -> transaction.sourceAmount
        else -> transaction.sourceAmount
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedgerIconBadge(
            iconId = iconId,
            kind = iconKind,
            color = badgeColor,
            fallbackText = title,
            size = 38
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                cents = amount,
                type = transaction.type,
                currency = currency,
                hidden = transaction.hideAmount,
                fontSize = 16
            )
            if (transaction.type == TX_TYPE_TRANSFER && transaction.destinationAmount != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "→ ${Fmt.money(transaction.destinationAmount!!)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionSheetItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private fun titleOf(
    transaction: TransactionItem,
    accounts: Map<String, Account>,
    categories: Map<String, Category>
): String = when (transaction.type) {
    TX_TYPE_TRANSFER -> {
        val from = transaction.sourceAccount?.name
            ?: accounts[transaction.sourceAccountId]?.name
            ?: "账户"
        val to = transaction.destinationAccount?.name
            ?: transaction.destinationAccountId?.let { accounts[it]?.name }
            ?: "账户"
        "$from → $to"
    }

    TX_TYPE_BALANCE -> transaction.sourceAccount?.name
        ?: accounts[transaction.sourceAccountId]?.name
        ?: typeLabel(transaction.type)

    else -> transaction.category?.name
        ?: categories[transaction.categoryId]?.name
        ?: typeLabel(transaction.type)
}

private fun subtitleOf(
    transaction: TransactionItem,
    accounts: Map<String, Account>
): String {
    if (transaction.comment.isNotBlank()) return transaction.comment
    val accountName = transaction.sourceAccount?.name
        ?: accounts[transaction.sourceAccountId]?.name
        ?: ""
    val tags = transaction.tags?.joinToString(" ") { "#${it.name}" }.orEmpty()
    return listOf(accountName, tags).filter { it.isNotBlank() }.joinToString(" · ")
}

/** 计算某条明细在列表中的下标（含月份分隔行与日期头），找不到返回 -1 */
private fun List<DayGroup>.indexOfTransaction(id: String): Int {
    var index = 0
    var lastMonth: YearMonth? = null
    forEach { group ->
        val month = YearMonth.from(group.date)
        if (month != lastMonth) {
            index++
            lastMonth = month
        }
        index++
        val offset = group.items.indexOfFirst { it.id == id }
        if (offset >= 0) return index + offset
        index += group.items.size
    }
    return -1
}

private fun buildDayGroups(items: List<TransactionItem>): List<DayGroup> {
    val grouped = LinkedHashMap<LocalDate, MutableList<TransactionItem>>()
    items.forEach { item ->
        val date = Dates.toLocalDate(item.time, item.utcOffset)
        grouped.getOrPut(date) { mutableListOf() }.add(item)
    }
    return grouped.map { (date, list) ->
        DayGroup(
            date = date,
            items = list,
            income = list.filter { it.type == TX_TYPE_INCOME }.sumOf { it.sourceAmount },
            expense = list.filter { it.type == TX_TYPE_EXPENSE }.sumOf { it.sourceAmount }
        )
    }
}

/* ------------------------- 类别筛选 ------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    state: TransactionListUiState,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassOverlayContainer(),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "按类别筛选",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear, enabled = state.hasCategoryFilter) { Text("清除") }
            }

            val tree = state.categoryTree
                .filter { category ->
                    when (state.filterType) {
                        TX_TYPE_EXPENSE -> category.type == CATEGORY_TYPE_EXPENSE
                        TX_TYPE_INCOME -> category.type == CATEGORY_TYPE_INCOME
                        else -> category.type == CATEGORY_TYPE_EXPENSE || category.type == CATEGORY_TYPE_INCOME
                    }
                }
                .sortedByDescending { it.type }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                var lastType = Int.MIN_VALUE
                tree.forEach { parent ->
                    if (parent.type != lastType) {
                        lastType = parent.type
                        item(key = "h-${parent.type}") {
                            Text(
                                text = if (parent.type == CATEGORY_TYPE_INCOME) "收入分类" else "支出分类",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = "p-${parent.id}") {
                        CategoryFilterRow(
                            category = parent,
                            selected = parent.id in state.selectedCategoryIds,
                            indent = false,
                            onClick = { onToggle(parent.id) }
                        )
                    }
                    items(parent.subCategories, key = { "c-${it.id}" }) { child ->
                        CategoryFilterRow(
                            category = child,
                            selected = child.id in state.selectedCategoryIds,
                            indent = true,
                            onClick = { onToggle(child.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    category: Category,
    selected: Boolean,
    indent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 24.dp else 0.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedgerIconBadge(
            iconId = category.icon,
            kind = LedgerIconKind.CATEGORY,
            color = ledgerColorOf(category.color, MaterialTheme.colorScheme.primary),
            fallbackText = category.name,
            size = if (indent) 28 else 34
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
