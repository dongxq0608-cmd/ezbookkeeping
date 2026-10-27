package com.ezbook.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * 毛玻璃（iOS 风）配色：所有 surface 系列都是半透明，背景由 `BackgroundBackdrop` 提供，
 * 这样原有的卡片 / 顶栏 / 底部导航 / 弹窗不用逐个改就能呈现磨砂效果。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xCCB5E6D3),
    onPrimaryContainer = Color(0xFF002014),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xB3CFE9DA),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6373),
    background = Color.Transparent,
    onBackground = Color(0xFF171D1A),
    surface = Color(0xB8FFFFFF),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0x73FFFFFF),
    onSurfaceVariant = Color(0xFF3C453F),
    surfaceContainerLowest = Color(0xE0FFFFFF),
    surfaceContainerLow = Color(0xADFFFFFF),
    surfaceContainer = Color(0x99FFFFFF),
    surfaceContainerHigh = Color(0xC2FFFFFF),
    surfaceContainerHighest = Color(0xD1FFFFFF),
    outline = Color(0xFF6B736D),
    outlineVariant = Color(0x66FFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D2BB),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xCC13513D),
    onPrimaryContainer = Color(0xFFB5E6D3),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xB3354B41),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA5CDDF),
    background = Color.Transparent,
    onBackground = Color(0xFFDFE4E0),
    surface = Color(0xB2151B17),
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0x73404943),
    onSurfaceVariant = Color(0xFFC8D1CA),
    surfaceContainerLowest = Color(0xE00E1411),
    surfaceContainerLow = Color(0xB2141A17),
    surfaceContainer = Color(0xA21B221D),
    surfaceContainerHigh = Color(0xC2252C27),
    surfaceContainerHighest = Color(0xD12B332D),
    outline = Color(0xFF9AA39C),
    outlineVariant = Color(0x40FFFFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/** iOS 风格的大圆角 */
private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
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
            shapes = GlassShapes,
            content = content
        )
    }
}

/**
 * 弹层（底部弹窗 / 对话框 / 下拉菜单）的底色。
 *
 * 页面级卡片用半透明是毛玻璃效果，但弹层叠在内容之上，如果也半透明，
 * 下层界面的文字和按钮会透出来与弹层内容重叠，因此这里用接近不透明的底色。
 */
@Composable
fun glassOverlayContainer(): Color {
    // 深色主题的 surface 本身是暗色（亮度 < 0.5），浅色主题反之
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xF71A211C) else Color(0xF7F8FAF7)
}
