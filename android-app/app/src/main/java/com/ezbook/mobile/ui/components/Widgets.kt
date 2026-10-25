package com.ezbook.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezbook.mobile.data.model.TX_TYPE_BALANCE
import com.ezbook.mobile.data.model.TX_TYPE_EXPENSE
import com.ezbook.mobile.data.model.TX_TYPE_INCOME
import com.ezbook.mobile.data.model.TX_TYPE_TRANSFER
import com.ezbook.mobile.ui.theme.LocalLedgerColors
import com.ezbook.mobile.util.Fmt

@Composable
fun typeColor(type: Int): Color {
    val colors = LocalLedgerColors.current
    return when (type) {
        TX_TYPE_EXPENSE -> colors.expense
        TX_TYPE_INCOME -> colors.income
        TX_TYPE_TRANSFER -> colors.transfer
        TX_TYPE_BALANCE -> colors.balance
        else -> colors.balance
    }
}

fun typeLabel(type: Int): String = when (type) {
    TX_TYPE_EXPENSE -> "支出"
    TX_TYPE_INCOME -> "收入"
    TX_TYPE_TRANSFER -> "转账"
    TX_TYPE_BALANCE -> "余额变更"
    else -> "其他"
}

fun typePrefix(type: Int): String = when (type) {
    TX_TYPE_EXPENSE -> "-"
    TX_TYPE_INCOME -> "+"
    else -> ""
}

/** 带符号与着色的金额文本 */
@Composable
fun AmountText(
    cents: Long,
    type: Int,
    currency: String,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    fontSize: Int = 16
) {
    val colors = LocalLedgerColors.current
    val color = when (type) {
        TX_TYPE_EXPENSE -> colors.expense
        TX_TYPE_INCOME -> colors.income
        TX_TYPE_TRANSFER -> colors.transfer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = if (hidden) "****" else "${typePrefix(type)}${Fmt.currencySymbol(currency)}${Fmt.money(kotlin.math.abs(cents))}",
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

/** 分类/账户头像占位：彩色圆 + 首字 */
@Composable
fun CircleBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1),
            color = color,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
