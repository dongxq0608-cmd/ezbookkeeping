package com.ezbook.mobile.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.data.model.TX_TYPE_BALANCE
import com.ezbook.mobile.data.model.TX_TYPE_EXPENSE
import com.ezbook.mobile.data.model.TX_TYPE_INCOME
import com.ezbook.mobile.data.model.TX_TYPE_TRANSFER
import com.ezbook.mobile.data.model.Tag
import com.ezbook.mobile.Graph
import com.ezbook.mobile.ui.components.LedgerIconBadge
import com.ezbook.mobile.ui.components.LedgerIconKind
import com.ezbook.mobile.ui.components.LoadingBox
import com.ezbook.mobile.ui.components.ledgerColorOf
import com.ezbook.mobile.ui.components.groupAccounts
import com.ezbook.mobile.ui.components.typeColor
import com.ezbook.mobile.ui.theme.LocalLedgerColors
import com.ezbook.mobile.ui.theme.glassOverlayContainer
import com.ezbook.mobile.util.AmountCalculator
import com.ezbook.mobile.util.Dates
import com.ezbook.mobile.util.Fmt
import java.time.LocalTime

private enum class PickerTarget { CATEGORY, SOURCE_ACCOUNT, DEST_ACCOUNT, TAGS }

private val typeOptions = listOf(
    TX_TYPE_EXPENSE to "支出",
    TX_TYPE_INCOME to "收入",
    TX_TYPE_TRANSFER to "转账",
    TX_TYPE_BALANCE to "余额"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(
    txId: String,
    copyMode: Boolean = false,
    copyWithTime: Boolean = false,
    onFinished: () -> Unit,
    onCopy: (String, Boolean) -> Unit = { _, _ -> },
    /** 保存成功后的回调（参数为账单 id 与时间，用于定位）：默认与关闭一致 */
    onSaved: (String?, Long) -> Unit = { _, _ -> onFinished() },
    viewModel: TransactionEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(txId, copyMode) {
        if (copyMode) {
            viewModel.startDuplicate(txId, copyWithTime)
        } else {
            viewModel.start(txId)
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) onSaved(state.savedTxId, state.savedTxTime)
    }

    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var calculatorForDestination by remember { mutableStateOf(false) }
    var showCopyMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "记一笔" else "编辑账单") },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        Box {
                            IconButton(onClick = { showCopyMenu = true }, enabled = !state.saving) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                            }
                            DropdownMenu(
                                expanded = showCopyMenu,
                                onDismissRequest = { showCopyMenu = false },
                                containerColor = glassOverlayContainer()
                            ) {
                                DropdownMenuItem(
                                    text = { Text("复制") },
                                    onClick = {
                                        showCopyMenu = false
                                        onCopy(txId, false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("复制（含时间）") },
                                    onClick = {
                                        showCopyMenu = false
                                        onCopy(txId, true)
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !state.saving) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !state.saving && state.editable
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("保存", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (state.loading) {
            LoadingBox(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                typeOptions.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = typeOptions.size
                        ),
                        label = { Text(label, fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            AmountCard(
                state = state,
                onOpenCalculator = {
                    calculatorForDestination = false
                    showCalculator = true
                },
                onDestinationAmountChange = viewModel::setDestinationAmount,
                onOpenDestinationCalculator = {
                    calculatorForDestination = true
                    showCalculator = true
                }
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (state.type != TX_TYPE_BALANCE) {
                        FormRow(
                            label = "分类",
                            value = state.selectedCategory?.let { categoryLabel(state, it) } ?: "",
                            placeholder = "选择分类",
                            swatch = state.selectedCategory?.let { it.type } ?: state.type,
                            onClick = { picker = PickerTarget.CATEGORY }
                        )
                        RowDivider()
                    }

                    FormRow(
                        label = if (state.type == TX_TYPE_TRANSFER) "转出账户" else "账户",
                        value = state.sourceAccount?.name ?: "",
                        placeholder = "选择账户",
                        onClick = { picker = PickerTarget.SOURCE_ACCOUNT }
                    )

                    if (state.type == TX_TYPE_TRANSFER) {
                        RowDivider()
                        FormRow(
                            label = "转入账户",
                            value = state.destinationAccount?.name ?: "",
                            placeholder = "选择账户",
                            onClick = { picker = PickerTarget.DEST_ACCOUNT }
                        )
                    }

                    RowDivider()
                    FormRow(
                        label = "日期",
                        value = "${Dates.formatDate(state.localDate)} ${Dates.formatWeek(state.localDate)}",
                        placeholder = "",
                        onClick = { showDatePicker = true }
                    )

                    RowDivider()
                    FormRow(
                        label = "时间",
                        value = Dates.formatTime(state.localTime),
                        placeholder = "",
                        onClick = { showTimePicker = true }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "标签",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { picker = PickerTarget.TAGS }
                        )
                    }

                    if (state.selectedTagIds.isEmpty()) {
                        Text(
                            text = "未选择标签",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.selectedTagIds.forEach { tagId ->
                                val tag = state.tags.firstOrNull { it.id == tagId }
                                if (tag != null) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.toggleTag(tagId) },
                                        label = { Text(tag.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::setComment,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                placeholder = { Text("可选，最多 255 字") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!state.editable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "该账单超出可编辑时间范围，仅可查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    picker?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { picker = null },
            sheetState = sheetState,
            containerColor = glassOverlayContainer(),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            when (target) {
                PickerTarget.CATEGORY -> CategorySheet(
                    categories = state.availableCategories,
                    type = state.type,
                    selectedId = state.categoryId,
                    onSelect = {
                        viewModel.setCategory(it)
                        picker = null
                    }
                )

                PickerTarget.SOURCE_ACCOUNT -> AccountSheet(
                    title = if (state.type == TX_TYPE_TRANSFER) "选择转出账户" else "选择账户",
                    accountTree = state.accountTree,
                    selectedId = state.sourceAccountId,
                    disabledId = null,
                    onSelect = {
                        viewModel.setSourceAccount(it)
                        picker = null
                    }
                )

                PickerTarget.DEST_ACCOUNT -> AccountSheet(
                    title = "选择转入账户",
                    accountTree = state.accountTree,
                    selectedId = state.destinationAccountId,
                    disabledId = state.sourceAccountId,
                    onSelect = {
                        viewModel.setDestinationAccount(it)
                        picker = null
                    }
                )

                PickerTarget.TAGS -> TagSheet(
                    tags = state.tags,
                    selectedIds = state.selectedTagIds,
                    onToggle = viewModel::toggleTag,
                    onDone = { picker = null }
                )
            }
        }
    }

    if (showCalculator) {
        val calculatorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCalculator = false },
            sheetState = calculatorSheetState,
            containerColor = glassOverlayContainer(),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            CalculatorSheet(
                title = if (calculatorForDestination) "转入金额" else "金额",
                value = if (calculatorForDestination) state.destinationAmountText else state.amountText,
                currency = state.currency,
                onValueChange = if (calculatorForDestination) {
                    viewModel::setDestinationAmount
                } else {
                    viewModel::setAmount
                },
                onDone = {
                    showCalculator = false
                    calculatorForDestination = false
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = Dates.localDateToUtcMillis(state.localDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Dates.utcMillisToLocalDate(millis)
                        viewModel.setDateTime(date, state.localTime)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.localTime.hour,
            initialMinute = state.localTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = glassOverlayContainer(),
            title = { Text("选择时间") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    viewModel.setDateTime(state.localDate, time)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = glassOverlayContainer(),
            title = { Text("删除这笔账单？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AmountCard(
    state: TransactionEditUiState,
    onOpenCalculator: () -> Unit,
    onDestinationAmountChange: (String) -> Unit,
    onOpenDestinationCalculator: () -> Unit
) {
    val symbol = Fmt.currencySymbol(state.currency)
    val color = typeColor(state.type)

    val hasOperator = state.amountText.any { it in "+-*/×÷()" }
    val preview = remember(state.amountText) {
        if (hasOperator) AmountCalculator.evaluate(state.amountText) else null
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (state.type) {
                    TX_TYPE_TRANSFER -> "转出金额"
                    TX_TYPE_BALANCE -> "余额变更金额（可为负数）"
                    else -> "金额"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCalculator),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = symbol,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.amountText.isEmpty()) {
                        Text(
                            text = "0.00",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    } else {
                        Text(
                            text = state.amountText,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (preview != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "= ${Fmt.moneyWithSymbol(preview, state.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "点击金额可调出计算器，支持 + − × ÷ 与括号",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (state.type == TX_TYPE_TRANSFER) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "转入金额（留空则与转出金额相同）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDestinationCalculator),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.destinationAmountText.isEmpty()) {
                        Text(
                            text = "同转出金额",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    } else {
                        Text(
                            text = state.destinationAmountText,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "计算",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 金额计算器：数字与四则运算符，实时显示结果
 */
@Composable
private fun CalculatorSheet(
    title: String,
    value: String,
    currency: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val symbol = Fmt.currencySymbol(currency)
    val preview = remember(value) { AmountCalculator.evaluate(value) }

    // 表达式校验提示：未按 = 直接完成、或 = 无法计算时显示
    var hint by remember { mutableStateOf<String?>(null) }

    val keys = listOf(
        listOf("C", "⌫", "(", ")"),
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", "=", "+")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (value.isBlank()) "0" else value,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    hint?.let { text ->
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (preview != null) {
                        "= ${Fmt.moneyWithSymbol(preview, currency)}"
                    } else {
                        "= $symbol--"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        keys.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            hint = null
                            when (key) {
                                "C" -> onValueChange("")
                                "⌫" -> onValueChange(value.dropLast(1))
                                "=" -> {
                                    // 校验公式正确性：无法计算时提示，等待用户修正
                                    val result = AmountCalculator.evaluate(value)
                                    if (result != null) {
                                        onValueChange(Fmt.moneyPlain(result))
                                    } else {
                                        hint = "计算公式有误，请检查"
                                    }
                                }

                                else -> onValueChange(value + key)
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        KeypadButton(
            key = "完成",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (value.any { it in "+-*/×÷()" }) {
                    hint = "请先点击 = 键完成计算"
                } else {
                    onDone()
                }
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeypadButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isPrimary = key == "完成"
    // 运算符与 C（一键清空）使用同一套样式
    val isOperator = key in listOf("÷", "×", "-", "+", "(", ")", "=", "C")

    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isPrimary -> MaterialTheme.colorScheme.primary
            isOperator -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (key == "⌫") {
                Icon(
                    Icons.Filled.Backspace,
                    contentDescription = "退格",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = key,
                    fontSize = 20.sp,
                    fontWeight = if (isOperator || isPrimary) FontWeight.Medium else FontWeight.Normal,
                    color = if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun FormRow(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    swatch: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (swatch != null && value.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(typeColor(swatch), RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = value.ifBlank { placeholder },
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(max = 24.dp)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun categoryLabel(state: TransactionEditUiState, category: Category): String {
    if (category.parentId == "0") {
        return category.name
    }
    val parent = state.categoryTree.firstOrNull { it.id == category.parentId }
    return if (parent != null) "${parent.name} / ${category.name}" else category.name
}

@Composable
private fun CategorySheet(
    categories: List<Category>,
    type: Int,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    // 一级分类只作为分组标题，不能直接选中；记账只能选二级分类
    val groups = categories.filter { it.subCategories.isNotEmpty() }

    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (categories.isEmpty()) {
                    "没有可用分类，请先在网页版创建"
                } else {
                    "没有可选的二级分类，请先在网页版创建"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 二级分类默认折叠（可在「我的 → 记账分类折叠二级分类」关闭）
    val collapse = Graph.store.collapseTransactionCategories
    // 当前选中项所在的一级分类默认展开，便于看到当前选择
    var expandedIds by remember(groups, selectedId) {
        mutableStateOf(
            groups.filter { parent -> parent.subCategories.any { it.id == selectedId } }
                .map { it.id }
                .toSet()
        )
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        item {
            Text(
                text = "选择分类",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        groups.forEach { parent ->
            val expanded = !collapse || parent.id in expandedIds
            item(key = "p-${parent.id}") {
                CategoryGroupRow(
                    name = parent.name,
                    iconId = parent.icon,
                    color = ledgerColorOf(parent.color, typeColor(type)),
                    expanded = expanded,
                    collapsible = collapse,
                    onClick = {
                        expandedIds = if (parent.id in expandedIds) {
                            expandedIds - parent.id
                        } else {
                            expandedIds + parent.id
                        }
                    }
                )
            }
            if (expanded) {
                parent.subCategories.forEach { child ->
                    item(key = "c-${child.id}") {
                        CategoryOption(
                            name = child.name,
                            iconId = child.icon,
                            selected = child.id == selectedId,
                            color = ledgerColorOf(child.color, typeColor(type)),
                            indent = true,
                            onClick = { onSelect(child.id) }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 分类分组标题：一级分类不可选（仅作分组），保留服务端下发的图标，点击可展开 / 收起二级分类 */
@Composable
private fun CategoryGroupRow(
    name: String,
    iconId: String?,
    color: Color,
    expanded: Boolean,
    collapsible: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (collapsible) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 2.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (collapsible) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowRight
                },
                contentDescription = if (expanded) "收起二级分类" else "展开二级分类",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
        LedgerIconBadge(
            iconId = iconId,
            kind = LedgerIconKind.CATEGORY,
            color = color,
            fallbackText = name,
            size = 30
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryOption(
    name: String,
    iconId: String?,
    selected: Boolean,
    color: Color,
    indent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) 44.dp else 20.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedgerIconBadge(
            iconId = iconId,
            kind = LedgerIconKind.CATEGORY,
            color = color,
            fallbackText = name,
            size = if (indent) 28 else 34
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 账户选择：左右结构——左侧为账户分类（储蓄账户、信用卡等），右侧为该分类下的账户
 * （子账户缩进显示）；支持按名称模糊搜索，点击账户即选中
 */
@Composable
private fun AccountSheet(
    title: String,
    accountTree: List<Account>,
    selectedId: String,
    disabledId: String?,
    onSelect: (String) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    val groups = remember(accountTree, keyword) { groupAccounts(accountTree, keyword) }

    // 分类合计金额使用的币种：优先取账户币种
    val currency = accountTree.firstOrNull()?.currency
        ?: Graph.store.user?.defaultCurrency
        ?: "CNY"

    // 默认定位到当前选中账户所在的分类
    val selectedGroupTitle = remember(groups, selectedId) {
        groups.firstOrNull { group -> group.rows.any { it.id == selectedId } }?.title
    }

    var activeGroupTitle by remember { mutableStateOf<String?>(null) }
    val currentGroup = groups.firstOrNull { it.title == activeGroupTitle }
        ?: groups.firstOrNull { it.title == selectedGroupTitle }
        ?: groups.firstOrNull()

    val accountListState = rememberLazyListState()
    LaunchedEffect(currentGroup?.title) {
        if (accountListState.layoutInfo.totalItemsCount > 0) {
            accountListState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("搜索账户名称") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        Spacer(Modifier.height(8.dp))

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (accountTree.isEmpty()) "没有可用账户" else "没有匹配的账户",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 2.dp)
        ) {
            Text(
                text = "账户分类",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currentGroup?.title ?: "账户",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // 固定高度：左右两栏各展示 5 行，超出部分上下滚动
        Row(modifier = Modifier.fillMaxWidth().height(PickerRowHeight * PickerVisibleRows)) {
            // 左栏：账户分类（储蓄账户、信用卡等），点击切换右栏内容
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(groups, key = { "group-${it.title}" }) { group ->
                    CategoryPickRow(
                        title = group.title,
                        total = group.total,
                        currency = currency,
                        liability = group.liability,
                        active = group.title == currentGroup?.title,
                        onClick = { activeGroupTitle = group.title }
                    )
                }
            }

            // 右栏：该分类下的账户，子账户缩进显示
            LazyColumn(modifier = Modifier.weight(1f), state = accountListState) {
                currentGroup?.let { group ->
                    items(group.rows, key = { "account-${it.id}" }) { account ->
                        val isChild = account.parentId != "0"
                        AccountPickRow(
                            account = account,
                            liability = group.liability,
                            selected = account.id == selectedId,
                            disabled = account.id == disabledId,
                            startPadding = if (isChild) 28.dp else 12.dp,
                            endPadding = 20.dp,
                            badgeSize = if (isChild) 26 else 34,
                            onClick = { onSelect(account.id) }
                        )
                    }
                }
            }
        }
    }
}

/** 账户选择左右两栏的行高，配合 [PickerVisibleRows] 固定弹窗高度 */
private val PickerRowHeight = 60.dp

/** 账户选择左右两栏可见行数，超出后滚动 */
private const val PickerVisibleRows = 5

/** 账户选择左栏：账户分类（储蓄账户、信用卡等）及其合计金额 */
@Composable
private fun CategoryPickRow(
    title: String,
    total: Long,
    currency: String,
    liability: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalLedgerColors.current
    // 负债账户合计显示为负数
    val displayTotal = if (liability) -kotlin.math.abs(total) else total

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .height(PickerRowHeight)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = Fmt.moneyWithSymbol(displayTotal, currency),
                style = MaterialTheme.typography.labelSmall,
                color = if (liability) {
                    colors.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 账户选择中的账户行（左侧图标 + 名称 / 余额 + 选中标记） */
@Composable
private fun AccountPickRow(
    account: Account,
    liability: Boolean,
    selected: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    startPadding: Dp = 20.dp,
    endPadding: Dp = 20.dp,
    badgeSize: Int = 34
) {
    val colors = LocalLedgerColors.current
    val balance = Fmt.stringToCents(account.balance)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = !disabled, onClick = onClick)
            .height(PickerRowHeight)
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedgerIconBadge(
            iconId = account.icon,
            kind = LedgerIconKind.ACCOUNT,
            color = ledgerColorOf(
                account.color,
                if (liability) colors.expense else MaterialTheme.colorScheme.primary
            ),
            fallbackText = account.name,
            size = badgeSize
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (disabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = Fmt.moneyWithSymbol(balance, account.currency),
                style = MaterialTheme.typography.bodySmall,
                color = if (liability) {
                    colors.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSheet(
    tags: List<Tag>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "选择标签（最多 10 个）",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("完成") }
        }

        if (tags.isEmpty()) {
            Text(
                text = "还没有标签，可在网页版创建",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedIds.contains(tag.id),
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
