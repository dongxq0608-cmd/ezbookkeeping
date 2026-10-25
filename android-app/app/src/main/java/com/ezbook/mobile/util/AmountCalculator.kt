package com.ezbook.mobile.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 金额表达式计算：支持 + - × ÷ 与括号，结果为「分」。
 * 表达式不完整或非法时返回 null（输入过程中会频繁出现这种情况，属于正常）。
 */
object AmountCalculator {

    private const val MAX_LENGTH = 64

    fun sanitize(text: String): String = text
        .replace("×", "*")
        .replace("÷", "/")
        .replace(",", "")
        .replace("，", "")
        .replace(" ", "")

    /** 表达式 -> 分；无法计算返回 null */
    fun evaluate(expression: String): Long? {
        val cleaned = sanitize(expression)
        if (cleaned.isEmpty() || cleaned.length > MAX_LENGTH) {
            return null
        }

        val value = runCatching { Parser(cleaned).parseAll() }.getOrNull() ?: return null

        return runCatching {
            value.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    /** 是否为「纯数字」表达式（用于决定是否显示 "=" 预览） */
    fun isPlainNumber(expression: String): Boolean =
        sanitize(expression).let { it.isNotEmpty() && it.all { ch -> ch.isDigit() || ch == '.' } }

    private class Parser(private val input: String) {

        private var pos = 0

        fun parseAll(): BigDecimal? {
            val value = parseExpression() ?: return null
            return if (pos == input.length) value else null
        }

        private fun peek(): Char? = if (pos < input.length) input[pos] else null

        private fun parseExpression(): BigDecimal? {
            var left: BigDecimal = parseTerm() ?: return null

            while (true) {
                when (peek()) {
                    '+' -> {
                        pos++
                        left = left.add(parseTerm() ?: return null)
                    }

                    '-' -> {
                        pos++
                        left = left.subtract(parseTerm() ?: return null)
                    }

                    else -> return left
                }
            }
        }

        private fun parseTerm(): BigDecimal? {
            var left: BigDecimal = parseFactor() ?: return null

            while (true) {
                when (peek()) {
                    '*' -> {
                        pos++
                        left = left.multiply(parseFactor() ?: return null)
                    }

                    '/' -> {
                        pos++
                        val divisor = parseFactor() ?: return null
                        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                            return null
                        }
                        left = left.divide(divisor, MathContext.DECIMAL64)
                    }

                    else -> return left
                }
            }
        }

        private fun parseFactor(): BigDecimal? {
            when (peek()) {
                '+' -> {
                    pos++
                    return parseFactor()
                }

                '-' -> {
                    pos++
                    return parseFactor()?.negate()
                }

                '(' -> {
                    pos++
                    val inner = parseExpression() ?: return null
                    if (peek() != ')') {
                        return null
                    }
                    pos++
                    return inner
                }

                else -> return parseNumber()
            }
        }

        private fun parseNumber(): BigDecimal? {
            val start = pos
            var dotCount = 0
            while (pos < input.length) {
                val ch = input[pos]
                if (ch.isDigit()) {
                    pos++
                } else if (ch == '.') {
                    dotCount++
                    if (dotCount > 1) return null
                    pos++
                } else {
                    break
                }
            }
            if (pos == start) {
                return null
            }
            return runCatching { BigDecimal(input.substring(start, pos)) }.getOrNull()
        }
    }
}
