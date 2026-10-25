package com.ezbook.mobile.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezbook.mobile.util.Fmt
import java.util.Locale
import kotlin.math.abs

/** 分类配色板 */
val chartPalette = listOf(
    Color(0xFF4E79A7),
    Color(0xFFF28E2B),
    Color(0xFF59A14F),
    Color(0xFFE15759),
    Color(0xFF76B7B2),
    Color(0xFFEDC948),
    Color(0xFFB07AA1),
    Color(0xFF9C755F)
)

/**
 * 环形占比图
 */
@Composable
fun DonutChart(
    values: List<Long>,
    colors: List<Color>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    diameter: Int = 180
) {
    val total = values.sum().takeIf { it > 0 } ?: 1L

    Box(
        modifier = modifier.size(diameter.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 24.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var startAngle = -90f

            values.forEachIndexed { index, value ->
                val sweep = value.toFloat() / total.toFloat() * 360f
                if (sweep <= 0f) return@forEachIndexed
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle + 1f,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0.8f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = centerValue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 节点金额标签：过万用「万/亿」，避免标签过宽互相遮挡
 */
private fun amountLabel(cents: Long): String {
    val yuan = cents / 100.0
    val magnitude = abs(yuan)
    return when {
        magnitude >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", yuan / 100_000_000)
        magnitude >= 10_000 -> String.format(Locale.CHINA, "%.1f万", yuan / 10_000)
            .replace(".0万", "万")
        magnitude >= 1 -> String.format(Locale.CHINA, "%.0f", yuan)
        magnitude > 0 -> String.format(Locale.CHINA, "%.2f", yuan)
        else -> "0"
    }
}

/**
 * 节点较多时横向滚动，保证每个节点的金额标签有足够宽度、互不挤压
 */
@Composable
private fun ChartScroller(
    pointCount: Int,
    minPointWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val contentWidth = maxOf(maxWidth, minPointWidth * pointCount)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.width(contentWidth)) {
                content()
            }
        }
    }
}

/**
 * 在节点旁绘制金额标签（above = true 画在锚点上方，否则画在下方），并限制在画布内
 */
private fun DrawScope.drawValueLabel(
    measurer: TextMeasurer,
    text: String,
    centerX: Float,
    anchorY: Float,
    above: Boolean,
    color: Color,
    style: TextStyle
) {
    if (text.isBlank()) return
    val layout = measurer.measure(text, style = style.copy(color = color))
    val labelWidth = layout.size.width.toFloat()
    val labelHeight = layout.size.height.toFloat()
    val x = (centerX - labelWidth / 2f)
        .coerceIn(1f, (size.width - labelWidth - 1f).coerceAtLeast(1f))
    val y = if (above) {
        (anchorY - labelHeight - 2.dp.toPx()).coerceAtLeast(0f)
    } else {
        (anchorY + 2.dp.toPx()).coerceAtMost((size.height - labelHeight).coerceAtLeast(0f))
    }
    drawText(layout, topLeft = Offset(x, y))
}

/**
 * 按月分组柱状图（收入 / 支出并排），每个柱子标注金额
 */
@Composable
fun MonthlyColumnChart(
    points: List<MonthPoint>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    barsHeight: Int = 156
) {
    if (points.isEmpty()) {
        return
    }

    val maxValue = points.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L
    val labelStep = if (points.size > 8) 2 else 1
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium)
    val valueLabels = remember(points) {
        points.map { amountLabel(it.income) to amountLabel(it.expense) }
    }

    ChartScroller(pointCount = points.size, minPointWidth = 84.dp, modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barsHeight.dp)
        ) {
            val slot = size.width / points.size
            val topSpace = 15.dp.toPx()
            val barArea = (size.height - topSpace).coerceAtLeast(1f)
            val baseline = topSpace + barArea
            val barWidth = slot * 0.3f
            val gap = slot * 0.08f
            val groupWidth = barWidth * 2 + gap

            points.forEachIndexed { index, point ->
                val startX = slot * index + (slot - groupWidth) / 2f
                val incomeHeight = barArea * (point.income.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val expenseHeight = barArea * (point.expense.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(startX, baseline - incomeHeight),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(startX + barWidth + gap, baseline - expenseHeight),
                    size = Size(barWidth, expenseHeight),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )

                drawValueLabel(
                    measurer = measurer,
                    text = valueLabels[index].first,
                    centerX = startX + barWidth / 2f,
                    anchorY = baseline - incomeHeight,
                    above = true,
                    color = incomeColor,
                    style = labelStyle
                )
                drawValueLabel(
                    measurer = measurer,
                    text = valueLabels[index].second,
                    centerX = startX + barWidth * 1.5f + gap,
                    anchorY = baseline - expenseHeight,
                    above = true,
                    color = expenseColor,
                    style = labelStyle
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % labelStep == 0) "${point.month}" else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }

        Text(
            text = "月份",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Long>
)

/**
 * 折线图，用于资产趋势（净值 / 总资产 / 总负债）
 */
@Composable
fun MultiLineChart(
    series: List<LineSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    chartHeight: Int = 190
) {
    val allValues = series.flatMap { it.values }
    if (allValues.isEmpty() || labels.isEmpty()) {
        return
    }

    var minValue = allValues.min()
    var maxValue = allValues.max()
    if (minValue == maxValue) {
        minValue -= 100L
        maxValue += 100L
    }
    // 上下留白，给金额标签腾出空间
    val span = (maxValue - minValue).toFloat()
    val lower = minValue - (span * 0.1f).toLong()
    val upper = maxValue + (span * 0.1f).toLong()
    val range = (upper - lower).toFloat().coerceAtLeast(1f)

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium)
    val valueLabels = remember(series) {
        series.map { line -> line.values.map { amountLabel(it) } }
    }
    val count = labels.size

    ChartScroller(pointCount = count, minPointWidth = 96.dp, modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp)
        ) {
            val h = size.height
            val inset = 24.dp.toPx()
            val usable = (size.width - inset * 2).coerceAtLeast(1f)

            // 水平网格线
            listOf(0f, 0.5f, 1f).forEach { ratio ->
                val y = h * ratio
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            fun xOf(index: Int): Float =
                if (count == 1) size.width / 2 else inset + usable * index / (count - 1).toFloat()

            fun yOf(value: Long): Float = h - h * (((value - lower) / range))

            series.forEachIndexed { seriesIndex, line ->
                if (line.values.size < 2) {
                    return@forEachIndexed
                }
                val path = Path()
                line.values.forEachIndexed { index, value ->
                    val x = xOf(index)
                    val y = yOf(value)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = line.color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // 端点标记 + 金额标签（首条在点上方，其余在点下方，避免互相压盖）
                line.values.forEachIndexed { index, value ->
                    val x = xOf(index)
                    val y = yOf(value)
                    drawCircle(
                        color = line.color,
                        radius = 2.5f.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawValueLabel(
                        measurer = measurer,
                        text = valueLabels.getOrNull(seriesIndex)?.getOrNull(index).orEmpty(),
                        centerX = x,
                        anchorY = y,
                        above = seriesIndex == 0,
                        color = line.color,
                        style = labelStyle
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = axisLabelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 分类分析：横向柱状图（对应网页端 Bar Chart），按金额排序，最长条为最大值
 */
@Composable
fun CategoryBarChart(
    names: List<String>,
    values: List<Long>,
    colors: List<Color>,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit = {}
) {
    val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        names.forEachIndexed { index, name ->
            val value = values.getOrElse(index) { 0L }
            val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(index) }
                    .padding(vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Fmt.moneyWithSymbol(value, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp)
                        .height(10.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(5.dp)
                        )
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .background(colors[index % colors.size], RoundedCornerShape(5.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 趋势分析：面积图（对应网页端 Area Chart）
 */
@Composable
fun TrendAreaChart(
    points: List<MonthPoint>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Int = 170
) {
    if (points.isEmpty()) {
        return
    }

    val maxValue = points.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L
    val labelStep = if (points.size > 8) 2 else 1
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium)
    val incomeLabels = remember(points) { points.map { amountLabel(it.income) } }
    val expenseLabels = remember(points) { points.map { amountLabel(it.expense) } }

    ChartScroller(pointCount = points.size, minPointWidth = 88.dp, modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp)
        ) {
            val h = size.height
            val inset = 22.dp.toPx()
            val usable = (size.width - inset * 2).coerceAtLeast(1f)
            // 顶部/底部留白，保证极值处的金额标签不被裁掉
            val topSpace = 14.dp.toPx()
            val bottomSpace = 14.dp.toPx()
            val plotHeight = (h - topSpace - bottomSpace).coerceAtLeast(1f)

            listOf(0f, 0.5f, 1f).forEach { ratio ->
                val y = topSpace + plotHeight * ratio
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            fun xOf(index: Int): Float =
                if (points.size == 1) size.width / 2 else inset + usable * index / (points.size - 1).toFloat()

            fun yOf(value: Long): Float =
                topSpace + plotHeight * (1f - (value.toFloat() / maxValue.toFloat()))

            fun drawSeries(color: Color, values: List<Long>, labels: List<String>, labelAbove: Boolean) {
                val line = Path()
                val area = Path()

                values.forEachIndexed { index, value ->
                    val x = xOf(index)
                    val y = yOf(value)
                    if (index == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, h - bottomSpace)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(xOf(values.lastIndex), h - bottomSpace)
                area.close()

                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                        startY = topSpace,
                        endY = h
                    )
                )
                drawPath(
                    path = line,
                    color = color,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                values.forEachIndexed { index, value ->
                    val x = xOf(index)
                    val y = yOf(value)
                    drawCircle(
                        color = color,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                    // 收入标签画在点上方，支出标签画在点下方，避免两条线接近时互相遮盖
                    drawValueLabel(
                        measurer = measurer,
                        text = labels.getOrNull(index).orEmpty(),
                        centerX = x,
                        anchorY = y,
                        above = labelAbove,
                        color = color,
                        style = labelStyle
                    )
                }
            }

            drawSeries(incomeColor, points.map { it.income }, incomeLabels, true)
            drawSeries(expenseColor, points.map { it.expense }, expenseLabels, false)
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % labelStep == 0) "${point.month}" else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 资产趋势：分组柱状图（对应网页端 Column Chart），支持负值（负债），以 0 为基线
 */
@Composable
fun AssetColumnChart(
    points: List<AssetPoint>,
    assetColor: Color,
    liabilityColor: Color,
    netColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Int = 170
) {
    if (points.isEmpty()) {
        return
    }

    val allValues = points.flatMap { listOf(it.assets, it.liabilities, it.net) }
    var lower = minOf(allValues.min(), 0L)
    var upper = maxOf(allValues.max(), 0L)
    if (lower == upper) {
        lower -= 100L
        upper += 100L
    }
    val range = (upper - lower).toFloat().coerceAtLeast(1f)
    val labelStep = if (points.size > 8) 2 else 1

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp)
        ) {
            val w = size.width
            val h = size.height

            fun yOf(value: Long): Float = h - h * ((value - lower) / range)

            listOf(0f, 0.5f, 1f).forEach { ratio ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * ratio),
                    end = Offset(w, h * ratio),
                    strokeWidth = 1f
                )
            }

            val baseline = yOf(0L)
            drawLine(
                color = baselineColor,
                start = Offset(0f, baseline),
                end = Offset(w, baseline),
                strokeWidth = 1.5f
            )

            val groupWidth = w / points.size
            val barWidth = groupWidth * 0.2f
            val gap = barWidth * 0.18f
            val groupBarsWidth = barWidth * 3 + gap * 2
            val corner = CornerRadius(2.dp.toPx())

            points.forEachIndexed { index, point ->
                val startX = index * groupWidth + (groupWidth - groupBarsWidth) / 2f
                val bars = listOf(
                    point.assets to assetColor,
                    point.liabilities to liabilityColor,
                    point.net to netColor
                )

                bars.forEachIndexed { barIndex, (value, color) ->
                    val x = startX + barIndex * (barWidth + gap)
                    val y = yOf(value)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, minOf(y, baseline)),
                        size = Size(barWidth, abs(y - baseline).coerceAtLeast(1f)),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % labelStep == 0) "${point.month}" else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}
