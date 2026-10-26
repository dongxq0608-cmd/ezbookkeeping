package com.ezbook.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 毛玻璃风格的背景层：铺满整个窗口，内容绘制在其上。
 *
 * 使用柔和渐变打底，配合主题里半透明的 surface 系列，形成磨砂玻璃质感。
 * 需要在 `EzBookTheme` 内部调用（依赖主题色）。
 */
@Composable
fun BackgroundBackdrop(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF0B100D) else Color(0xFFEFF4F0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (dark) {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0B100D),
                                Color(0xFF11201A),
                                Color(0xFF181314)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFE8F3ED),
                                Color(0xFFDCE9F6),
                                Color(0xFFF7EFE7)
                            )
                        )
                    }
                )
        )

        content()
    }
}
