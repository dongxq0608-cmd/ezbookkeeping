package com.ezbook.mobile.ui.accounts

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.ui.components.EmptyState
import com.ezbook.mobile.ui.components.LedgerIconBadge
import com.ezbook.mobile.ui.components.LedgerIconKind
import com.ezbook.mobile.ui.components.ledgerColorOf
import com.ezbook.mobile.ui.components.LoadingBox
import com.ezbook.mobile.ui.components.groupAccounts
import com.ezbook.mobile.ui.theme.LocalLedgerColors
import com.ezbook.mobile.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenAccount: (Account) -> Unit = {},
    viewModel: AccountsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalLedgerColors.current

    LaunchedEffect(Unit) { viewModel.onEnter() }

    val groups = remember(state.accounts) { groupAccounts(state.accounts) }

    // 分类折叠：默认折叠（可在「我的 → 账户页折叠账户分类」关闭），点击分类标题展开该分类下的账户
    val collapseCategories = Graph.store.collapseAccountCategories
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    // 仅用一级账户汇总，避免父子账户重复计入
    val totalAsset = state.accounts
        .filter { it.isAsset && !it.isLiability }
        .sumOf { Fmt.stringToCents(it.balance) }

    // 负债余额本身为负数，总负债保留负号展示
    val totalLiability = state.accounts
        .filter { it.isLiability }
        .sumOf { Fmt.stringToCents(it.balance) }

    // 净资产 = 总资产 + 总负债（负债为负值）
    val netAssets = totalAsset + totalLiability

    val currency = state.accounts.firstOrNull()?.currency
        ?: Graph.store.user?.defaultCurrency
        ?: "CNY"

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "账户",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 总资产：黑色
                TotalItem(
                    label = "总资产",
                    value = Fmt.moneyWithSymbol(totalAsset, currency),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 总负债：绿色，显示为负数
                TotalItem(
                    label = "总负债",
                    value = Fmt.moneyWithSymbol(totalLiability, currency),
                    color = colors.expense,
                    modifier = Modifier.weight(1f)
                )
                // 净资产：红色
                TotalItem(
                    label = "净资产",
                    value = Fmt.moneyWithSymbol(netAssets, currency),
                    color = colors.income,
                    modifier = Modifier.weight(1f)
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
                state.loading && groups.isEmpty() -> LoadingBox()

                state.error != null && groups.isEmpty() -> Column(
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

                groups.isEmpty() -> EmptyState("还没有账户", "请先在网页版创建账户")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    groups.forEach { group ->
                        val expanded = !collapseCategories || group.title in expandedGroups
                        item(key = "header-${group.title}") {
                            SectionHeaderRow(
                                title = group.title,
                                total = group.total,
                                currency = currency,
                                liability = group.liability,
                                collapsible = collapseCategories,
                                expanded = expanded,
                                onToggle = {
                                    expandedGroups = if (group.title in expandedGroups) {
                                        expandedGroups - group.title
                                    } else {
                                        expandedGroups + group.title
                                    }
                                }
                            )
                        }
                        // 分类折叠时隐藏该分类下的账户，展开后按「一级账户 → 子账户」顺序显示
                        if (expanded) {
                            items(group.rows, key = { it.id }) { account ->
                                AccountRow(
                                    account = account,
                                    liability = group.liability,
                                    fallbackCurrency = currency,
                                    onClick = { onOpenAccount(account) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
private fun SectionHeaderRow(
    title: String,
    total: Long,
    currency: String,
    liability: Boolean,
    collapsible: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {}
) {
    val colors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (collapsible) {
                    Modifier.clickable(onClick = onToggle)
                } else {
                    Modifier
                }
            )
            .padding(
                start = if (collapsible) 2.dp else 20.dp,
                end = 20.dp,
                top = 18.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (collapsible) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowRight
                },
                contentDescription = if (expanded) "收起该分类账户" else "展开该分类账户",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = Fmt.moneyWithSymbol(total, currency),
            style = MaterialTheme.typography.labelMedium,
            color = if (liability) colors.expense else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccountRow(
    account: Account,
    liability: Boolean,
    fallbackCurrency: String,
    onClick: () -> Unit = {}
) {
    val colors = LocalLedgerColors.current
    val indent = account.parentId != "0"
    val balance = Fmt.stringToCents(account.balance)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) 44.dp else 20.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
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
            size = if (indent) 30 else 36
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.hidden) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "隐藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (account.comment.isNotBlank()) {
                Text(
                    text = account.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = Fmt.moneyWithSymbol(
                balance,
                account.currency.takeIf { it.isNotBlank() } ?: fallbackCurrency
            ),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (liability) colors.expense else MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "查看明细",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp)
        )
    }
}
