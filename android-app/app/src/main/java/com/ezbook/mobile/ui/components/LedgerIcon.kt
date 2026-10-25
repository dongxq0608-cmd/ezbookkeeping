package com.ezbook.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezbook.mobile.ui.icons.AccountIconCodes
import com.ezbook.mobile.ui.icons.CategoryIconCodes
import com.ezbook.mobile.ui.icons.LaSolid

/** 图标来源：账户图标与分类图标的 id 空间是独立的 */
enum class LedgerIconKind { ACCOUNT, CATEGORY }

/**
 * 解析网页端分类 / 账户颜色（6 位十六进制、可带 #），解析失败时回退
 */
fun ledgerColorOf(hex: String?, fallback: Color): Color {
    val value = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return fallback
    return runCatching { Color(("FF" + value).toLong(16)) }.getOrDefault(fallback)
}

/**
 * 与网页端一致的系统图标圆形底徽标；
 * 自定义图标（id 不在系统图标表里）或没有图标时，回退为名称首字。
 */
@Composable
fun LedgerIconBadge(
    iconId: String?,
    kind: LedgerIconKind,
    color: Color,
    fallbackText: String = "",
    modifier: Modifier = Modifier,
    size: Int = 38
) {
    val codes = if (kind == LedgerIconKind.ACCOUNT) AccountIconCodes else CategoryIconCodes
    val code = iconId?.takeIf { it.isNotBlank() && it != "0" }?.let { codes[it] }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (code != null) {
            Text(
                text = String(Character.toChars(code)),
                fontFamily = LaSolid,
                style = TextStyle(
                    fontSize = (size * 0.48f).sp,
                    lineHeight = (size * 0.48f).sp
                ),
                color = color
            )
        } else {
            Text(
                text = fallbackText.trim().take(1).ifBlank { "?" },
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}
