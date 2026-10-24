package dev.vetki.core

import java.math.BigDecimal
import java.math.MathContext

/** Decimal arithmetic shared by the standalone tool and canvas cards. */
object Calculator {
    private val precision = MathContext.DECIMAL128
    private val hundred = BigDecimal(100)

    fun number(text: String): BigDecimal {
        val normalized = text.trim().replace(',', '.')
        require(
            normalized.length in 1..80 && normalized.matches(Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)"))
        )
        return normalized.toBigDecimal(precision)
    }

    fun expression(text: String): BigDecimal = Parser(text).parse()

    fun percent(base: BigDecimal, percent: BigDecimal): BigDecimal =
        base.multiply(percent, precision).divide(hundred, precision)

    fun discount(price: BigDecimal, rate: BigDecimal): BigDecimal {
        require(price.signum() >= 0 && rate >= BigDecimal.ZERO && rate <= hundred)
        return price.subtract(percent(price, rate), precision)
    }

    fun ratio(part: BigDecimal, whole: BigDecimal): BigDecimal =
        part.multiply(hundred, precision).divide(whole, precision)

    data class Pay(val gross: BigDecimal, val deduction: BigDecimal, val net: BigDecimal)

    /** Rates and deductions are entered by the user; no tax rules are assumed. */
    fun salary(
        base: BigDecimal,
        worked: BigDecimal,
        planned: BigDecimal?,
        bonus: BigDecimal,
        deductionRate: BigDecimal,
    ): Pay {
        require(listOf(base, worked, bonus, deductionRate).all { it.signum() >= 0 })
        require(deductionRate <= hundred)
        if (planned != null) require(planned.signum() > 0 && worked <= planned)
        val gross =
            (if (planned == null) base.multiply(worked, precision)
                else base.multiply(worked, precision).divide(planned, precision))
                .add(bonus, precision)
        val deduction = percent(gross, deductionRate)
        return Pay(gross, deduction, gross.subtract(deduction, precision))
    }

    private class Parser(source: String) {
        private val input =
            source.replace(',', '.').replace('×', '*').replace('÷', '/').replace('−', '-')
        private var index = 0
        private var depth = 0

        fun parse(): BigDecimal {
            require(input.length in 1..512)
            val value = sum()
            spaces()
            require(index == input.length)
            return value
        }

        private fun spaces() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun take(char: Char): Boolean {
            spaces()
            if (index < input.length && input[index] == char) {
                index++
                return true
            }
            return false
        }

        private fun sum(): BigDecimal {
            var value = product()
            while (true) value =
                when {
                    take('+') -> value.add(product(), precision)
                    take('-') -> value.subtract(product(), precision)
                    else -> return value
                }
        }

        private fun product(): BigDecimal {
            var value = unary()
            while (true) value =
                when {
                    take('*') -> value.multiply(unary(), precision)
                    take('/') -> value.divide(unary(), precision)
                    else -> return value
                }
        }

        private fun unary(): BigDecimal {
            require(++depth <= 32)
            val value =
                when {
                    take('+') -> unary()
                    take('-') -> unary().negate()
                    else -> atom()
                }
            depth--
            return value
        }

        private fun atom(): BigDecimal {
            var value =
                if (take('(')) sum().also { require(take(')')) }
                else {
                    spaces()
                    val start = index
                    while (
                        index < input.length && (input[index].isDigit() || input[index] == '.')
                    ) index++
                    number(input.substring(start, index))
                }
            if (take('%')) value = value.divide(hundred, precision)
            return value
        }
    }
}
