package com.ezbook.mobile.util

import androidx.compose.ui.graphics.Color
import com.ezbook.mobile.data.model.TX_TYPE_EXPENSE
import com.ezbook.mobile.data.model.TX_TYPE_INCOME
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {

    private val grouped = DecimalFormat("#,##0.00")
    private val plain = DecimalFormat("0.00")

    /** 分 -> "1,234.56" */
    fun money(cents: Long): String =
        grouped.format(BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP))

    /** 分 -> "1234.56"（无千分位，用于输入框） */
    fun moneyPlain(cents: Long): String =
        plain.format(BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP))

    /** 分 -> 紧凑金额（日历格子等窄空间）："1.2万" / "3亿" / "128" */
    fun compactMoney(cents: Long): String {
        val yuan = cents / 100.0
        val magnitude = kotlin.math.abs(yuan)
        return when {
            magnitude >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", yuan / 100_000_000)
            magnitude >= 10_000 -> String.format(Locale.CHINA, "%.1f万", yuan / 10_000)
                .replace(".0万", "万")
            magnitude >= 100 -> String.format(Locale.CHINA, "%.0f", yuan)
            magnitude > 0 -> String.format(Locale.CHINA, "%.2f", yuan)
            else -> "0"
        }
    }

    /**
     * 带币种符号且符号位置正确：负数显示为 "-¥1,234.56" 而不是 "¥-1,234.56"
     */
    fun moneyWithSymbol(cents: Long, currency: String): String {
        val symbol = currencySymbol(currency)
        return if (cents < 0) {
            "-$symbol${money(-cents)}"
        } else {
            "$symbol${money(cents)}"
        }
    }

    /**
     * 账户 balance 字符串 -> 分。
     *
     * 服务端是 `utils.Int64ToString(a.Balance)`，即**已经是 ×100 的整数**：
     * "234560" 表示 2345.60，**不能**再乘 100。
     * 仅当字符串里带小数点时（非标准格式）才按「元」为单位换算。
     */
    fun stringToCents(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val trimmed = text.trim()
        return runCatching {
            if (trimmed.contains('.')) {
                BigDecimal(trimmed).setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100)).toLong()
            } else {
                BigDecimal(trimmed).toLong()
            }
        }.getOrDefault(0L)
    }

    /** 输入框文本 -> 分，非法返回 null */
    fun parseCents(input: String): Long? {
        val text = input.trim().replace(",", "").replace(" ", "")
        if (text.isEmpty() || text == "." || text == "-") return null
        return runCatching {
            BigDecimal(text).setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).toLong()
        }.getOrNull()
    }

    fun currencySymbol(currency: String): String {
        val code = currency.uppercase(Locale.ROOT)
        return when (code) {
            "CNY", "RMB" -> "¥"
            "JPY" -> "JP¥"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "HKD" -> "HK$"
            "TWD" -> "NT$"
            "KRW" -> "₩"
            "AUD" -> "A$"
            "CAD" -> "C$"
            "SGD" -> "S$"
            "RUB" -> "₽"
            "INR" -> "₹"
            "THB" -> "฿"
            "MYR" -> "RM"
            "PHP" -> "₱"
            "VND" -> "₫"
            else -> if (code.isBlank()) "¥" else "$code "
        }
    }

    fun hexColor(hex: String, fallback: Color): Color {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6) return fallback
        return runCatching {
            Color(0xFF000000.toInt() or cleaned.toLong(16).toInt())
        }.getOrDefault(fallback)
    }
}

object Dates {

    private val dayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val weekFormatter = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun toLocalDate(epochSeconds: Long, utcOffsetMinutes: Int): LocalDate =
        Instant.ofEpochSecond(epochSeconds)
            .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60))
            .toLocalDate()

    fun toLocalTime(epochSeconds: Long, utcOffsetMinutes: Int): LocalTime =
        Instant.ofEpochSecond(epochSeconds)
            .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60))
            .toLocalTime()

    fun formatDate(date: LocalDate): String = dayFormatter.format(date)

    fun formatWeek(date: LocalDate): String = weekFormatter.format(date)

    fun formatTime(time: LocalTime): String = timeFormatter.format(time)

    fun dayHeader(date: LocalDate): String {
        val today = LocalDate.now()
        val prefix = when (date) {
            today -> "今天 · "
            today.minusDays(1) -> "昨天 · "
            else -> ""
        }
        return "$prefix${formatDate(date)} ${formatWeek(date)}"
    }

    fun monthLabel(year: Int, month: Int): String = "${year}年${month}月"

    fun yearMonthKey(year: Int, month: Int): String =
        String.format(Locale.ROOT, "%04d-%02d", year, month)

    fun currentUtcOffsetMinutes(): Int {
        val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
        return offset.totalSeconds / 60
    }

    /** 本地 日期+时间 -> Unix 秒 */
    fun toEpochSeconds(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toEpochSecond()

    fun startOfMonthEpoch(year: Int, month: Int): Long =
        LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

    fun endOfMonthEpoch(year: Int, month: Int): Long {
        val first = LocalDate.of(year, month, 1)
        return first.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() - 1
    }

    /** M3 DatePicker 使用 UTC 零点毫秒，这里做双向转换 */
    fun localDateToUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun utcMillisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
