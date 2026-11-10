package com.benlee.icalcs26

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.*

/** No eval(), JavaScript runtime or third-party expression API. */
object CalcEngine {
    fun evaluate(expression: String, degrees: Boolean = true, variables: Map<String, Double> = emptyMap()): Double {
        val input = expression.replace("×", "*").replace("÷", "/")
            .replace("−", "-").replace("π", "pi").replace("√", "sqrt")
        val parser = Parser(input, degrees, variables)
        val result = parser.expression()
        parser.whitespace()
        if (!parser.atEnd() || !result.isFinite()) throw IllegalArgumentException("올바른 수식이 아닙니다")
        return result
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "오류"
        if (value == 0.0) return "0"
        if (abs(value) < 1e-9 || abs(value) >= 1e13) return String.format(Locale.US, "%.8e", value)
        val n = BigDecimal.valueOf(value).round(java.math.MathContext(12, RoundingMode.HALF_UP))
            .stripTrailingZeros().toPlainString()
        return n.split('.').let { parts ->
            val sign = if (parts[0].startsWith("-")) "-" else ""
            val digits = parts[0].removePrefix("-").reversed().chunked(3).joinToString(",").reversed()
            sign + digits + if (parts.size > 1) ".${parts[1]}" else ""
        }
    }

    private class Parser(val source: String, val degrees: Boolean, val variables: Map<String, Double>) {
        var pos = 0
        fun whitespace() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
        fun atEnd() = pos >= source.length
        private fun eat(char: Char): Boolean {
            whitespace()
            if (pos < source.length && source[pos] == char) { pos++; return true }
            return false
        }
        fun expression(): Double {
            var x = term()
            while (true) {
                when {
                    eat('+') -> {
                        val start = pos
                        val y = term()
                        // iOS-style 200 + 10% = 220, but 200 * 10% = 20.
                        x += if (source.substring(start, pos).trimEnd().endsWith('%')) x * y else y
                    }
                    eat('-') -> {
                        val start = pos
                        val y = term()
                        x -= if (source.substring(start, pos).trimEnd().endsWith('%')) x * y else y
                    }
                    else -> return x
                }
            }
        }
        private fun term(): Double {
            var x = unary()
            while (true) {
                when {
                    eat('*') -> x *= unary()
                    eat('/') -> {
                        val d = unary()
                        if (d == 0.0) throw IllegalArgumentException("0으로 나눌 수 없습니다")
                        x /= d
                    }
                    else -> return x
                }
            }
        }
        private fun unary(): Double = when {
            eat('+') -> unary()
            eat('-') -> -unary()
            else -> power()
        }
        private fun power(): Double {
            var x = primary()
            while (true) {
                when {
                    eat('%') -> x /= 100.0
                    eat('!') -> {
                        if (x < 0 || x > 170 || x != floor(x)) throw IllegalArgumentException("팩토리얼 범위 오류")
                        var z = 1.0
                        for (j in 2..x.toInt()) z *= j.toDouble()
                        x = z
                    }
                    else -> break
                }
            }
            if (eat('^')) x = x.pow(unary())
            return x
        }
        private fun primary(): Double {
            whitespace()
            if (eat('(')) {
                val x = expression()
                if (!eat(')')) throw IllegalArgumentException("닫는 괄호가 없습니다")
                return x
            }
            if (pos >= source.length) throw IllegalArgumentException("불완전한 수식")
            val begin = pos
            if (source[pos].isDigit() || source[pos] == '.') {
                while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
                if (pos < source.length && (source[pos] == 'E' || source[pos] == 'e')) {
                    val eStart = pos++
                    if (pos < source.length && (source[pos] == '-' || source[pos] == '+')) pos++
                    val digitStart = pos
                    while (pos < source.length && source[pos].isDigit()) pos++
                    if (digitStart == pos) pos = eStart
                }
                return source.substring(begin, pos).toDoubleOrNull()
                    ?: throw IllegalArgumentException("잘못된 숫자")
            }
            if (source[pos].isLetter()) {
                while (pos < source.length && (source[pos].isLetter() || source[pos].isDigit() || source[pos] == '_')) pos++
                val word = source.substring(begin, pos)
                if (word == "pi") return Math.PI
                if (word == "e") return Math.E
                if (variables.containsKey(word)) return variables.getValue(word)
                if (!eat('(')) throw IllegalArgumentException("알 수 없는 기호: $word")
                val argument = expression()
                if (!eat(')')) throw IllegalArgumentException("닫는 괄호가 없습니다")
                val t = if (degrees) Math.toRadians(argument) else argument
                return when (word.lowercase(Locale.US)) {
                    "sin" -> sin(t); "cos" -> cos(t); "tan" -> tan(t)
                    "asin" -> if (degrees) Math.toDegrees(asin(argument)) else asin(argument)
                    "acos" -> if (degrees) Math.toDegrees(acos(argument)) else acos(argument)
                    "atan" -> if (degrees) Math.toDegrees(atan(argument)) else atan(argument)
                    "sinh" -> sinh(argument); "cosh" -> cosh(argument); "tanh" -> tanh(argument)
                    "sqrt" -> sqrt(argument); "cbrt" -> cbrt(argument)
                    "ln" -> ln(argument); "log" -> log10(argument)
                    "exp" -> exp(argument); "abs" -> abs(argument)
                    else -> throw IllegalArgumentException("지원하지 않는 함수: $word")
                }
            }
            throw IllegalArgumentException("잘못된 문자: ${source[pos]}")
        }
    }
}

object MathNotesEngine {
    fun resolve(text: String): List<Pair<String, String>> {
        val vars = mutableMapOf<String, Double>()
        return text.lines().filter { it.isNotBlank() }.map { line ->
            try {
                val trimmed = line.trim().removeSuffix("=").trim()
                val varDef = Regex("^([a-zA-Z][a-zA-Z0-9_]*)\\s*=\\s*(.+)$").matchEntire(trimmed)
                if (varDef != null) {
                    val key = varDef.groupValues[1]
                    val value = CalcEngine.evaluate(varDef.groupValues[2], variables = vars)
                    vars[key] = value
                    line to "$key = ${CalcEngine.format(value)}"
                } else {
                    line to CalcEngine.format(CalcEngine.evaluate(trimmed, variables = vars))
                }
            } catch (_: Exception) { line to "입력 확인" }
        }
    }
}
