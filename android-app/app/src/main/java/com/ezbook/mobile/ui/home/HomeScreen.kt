package com.ezbook.mobile.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ezbook.mobile.ui.accounts.AccountsScreen
import com.ezbook.mobile.ui.stats.StatisticsScreen
import com.ezbook.mobile.ui.transactions.AccountFilterRequest
import com.ezbook.mobile.ui.transactions.CategoryFilterRequest
import com.ezbook.mobile.ui.transactions.TransactionListScreen

private data class TabItem(val label: String, val icon: ImageVector)

/** 底部菜单顺序：首页 / 账单 / 记一笔（加号） / 统计 / 账户 */
private const val ADD_BUTTON_INDEX = 2

private val tabs = listOf(
    TabItem("首页", Icons.Filled.Home),
    TabItem("账单", Icons.Filled.ReceiptLong),
    TabItem("统计", Icons.Filled.PieChart),
    TabItem("账户", Icons.Filled.AccountBalance)
)

const val TAB_HOME = 0
const val TAB_BILLS = 1
const val TAB_STATS = 2
const val TAB_ACCOUNTS = 3

@Composable
fun HomeScreen(
    onOpenTransaction: (String) -> Unit,
    onCopyTransaction: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    selected: Int,
    onSelectTab: (Int) -> Unit
) {
    var pendingCategory by remember { mutableStateOf<CategoryFilterRequest?>(null) }
    var pendingAccount by remember { mutableStateOf<AccountFilterRequest?>(null) }
    // 从其他 Tab 重新点进「账单」时递增，让账单页清除上一次的过滤条件
    var billsResetToken by remember { mutableIntStateOf(0) }

    // 下钻层级：记录下钻前所在的 Tab，返回时逐级回退到上一级功能；
    // 用 rememberSaveable 保存，进出记账页后返回仍能逐级回退
    var drillStack by rememberSaveable(
        stateSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it })
    ) {
        mutableStateOf(emptyList<Int>())
    }

    BackHandler(enabled = drillStack.isNotEmpty()) {
        val previous = drillStack.last()
        drillStack = drillStack.dropLast(1)
        pendingCategory = null
        pendingAccount = null
        onSelectTab(previous)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, item ->
                    if (index == ADD_BUTTON_INDEX) {
                        AddTransactionButton(onClick = { onOpenTransaction("0") })
                    }
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = {
                            // 手动切换 Tab 视为回到一级菜单，清空下钻层级
                            drillStack = emptyList()
                            if (index == TAB_BILLS) {
                                // 进入或再次点击「账单」都回到未过滤的账单列表
                                pendingCategory = null
                                pendingAccount = null
                                billsResetToken += 1
                            }
                            onSelectTab(index)
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selected) {
                TAB_HOME -> OverviewScreen(onOpenSettings = onOpenSettings)
                TAB_BILLS -> TransactionListScreen(
                    onOpenTransaction = onOpenTransaction,
                    onCopyTransaction = onCopyTransaction,
                    pendingCategory = pendingCategory,
                    onPendingCategoryHandled = { pendingCategory = null },
                    pendingAccount = pendingAccount,
                    onPendingAccountHandled = { pendingAccount = null },
                    resetToken = billsResetToken
                )
                TAB_STATS -> StatisticsScreen(
                    onOpenCategoryTransactions = { categoryId, name, categoryType ->
                        drillStack = drillStack + selected
                        pendingCategory = CategoryFilterRequest(categoryId, name, categoryType)
                        onSelectTab(TAB_BILLS)
                    }
                )
                else -> AccountsScreen(
                    onOpenAccount = { account ->
                        drillStack = drillStack + selected
                        pendingAccount = AccountFilterRequest(account.id, account.name)
                        onSelectTab(TAB_BILLS)
                    }
                )
            }
        }
    }
}

/** 底部菜单中间的圆形加号按钮 */
@Composable
private fun RowScope.AddTransactionButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "记一笔",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
