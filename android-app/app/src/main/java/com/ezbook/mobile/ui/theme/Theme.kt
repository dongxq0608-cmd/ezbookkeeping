package com.ezbook.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5E6D3),
    onPrimaryContainer = Color(0xFF002014),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6373),
    background = Color(0xFFF6FAF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainer = Color(0xFFEDF3EE),
    surfaceContainerHigh = Color(0xFFE6EDE8),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D2BB),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF13513D),
    onPrimaryContainer = Color(0xFFB5E6D3),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA5CDDF),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF151B17),
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C2),
    surfaceContainer = Color(0xFF1B221D),
    surfaceContainerHigh = Color(0xFF252C27),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/**
 * 记账语义色：支出 / 收入 / 转账 / 余额调整。
 *
 * 配色遵循中式习惯（与 ezBookkeeping 的「收入红 / 支出绿」显示设置一致）：
 * 红色 = 收入、净资产；绿色 = 支出、负债；蓝色 = 转账；靛蓝 = 总资产；灰色 = 余额变更。
 */
data class LedgerColors(
    val expense: Color,
    val income: Color,
    val transfer: Color,
    val balance: Color,
    val asset: Color
)

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerColors(
        expense = Color(0xFF1F8B4C),
        income = Color(0xFFD5443C),
        transfer = Color(0xFF2E6FBF),
        balance = Color(0xFF6B7280),
        asset = Color(0xFF3B5BDB)
    )
}

private val LightLedger = LedgerColors(
    expense = Color(0xFF1F8B4C),
    income = Color(0xFFD5443C),
    transfer = Color(0xFF2E6FBF),
    balance = Color(0xFF6B7280),
    asset = Color(0xFF3B5BDB)
)

private val DarkLedger = LedgerColors(
    expense = Color(0xFF6ED49A),
    income = Color(0xFFFF8F87),
    transfer = Color(0xFF8FB8F0),
    balance = Color(0xFFA0A8B0),
    asset = Color(0xFF8FA8F5)
)

private val AppTypography = Typography()

@Composable
fun EzBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLedgerColors provides if (darkTheme) DarkLedger else LightLedger
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}
