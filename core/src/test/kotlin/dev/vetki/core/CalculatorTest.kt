package dev.vetki.core

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CalculatorTest {
    private fun equal(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    private fun invalid(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    @Test
    fun arithmeticPreservesDecimalPrecision() {
        equal("0.3", Calculator.expression("0,1 + 0.2"))
        equal("9007199254740994", Calculator.expression("9007199254740993 + 1"))
    }

    @Test
    fun precedenceParenthesesAndUnarySigns() {
        equal("14", Calculator.expression("2 + 3 × 4"))
        equal("-10", Calculator.expression("-(2 + 3) * 2"))
        equal("5", Calculator.expression("12 ÷ 4 + --2"))
        equal(".5", Calculator.expression(".25 + ,25"))
    }

    @Test
    fun percentageOperatorIsAlwaysOneHundredth() {
        equal("20", Calculator.expression("100 × 20%"))
        equal("100.2", Calculator.expression("100 + 20%"))
        equal(".3", Calculator.expression("(10+20)%"))
    }

    @Test
    fun rejectsMalformedAndUnsafeExpressions() {
        listOf("", "2+", "2(3)", "1..2", "2%3", "2**3", "hello", "2e99", "2%%", "(2", "2)")
            .forEach { invalid { Calculator.expression(it) } }
        invalid { Calculator.expression("(".repeat(40) + "1" + ")".repeat(40)) }
        invalid { Calculator.expression("1".repeat(513)) }
        invalid { Calculator.expression("-".repeat(100) + "1") }
    }

    @Test
    fun divisionByZeroDoesNotProduceInfinity() {
        invalid { Calculator.expression("5 / (2-2)") }
        invalid { Calculator.ratio(BigDecimal.ONE, BigDecimal.ZERO) }
    }

    @Test
    fun discountsAndRatios() {
        equal("1700", Calculator.discount(BigDecimal("2000"), BigDecimal("15")))
        equal("0", Calculator.discount(BigDecimal("2000"), BigDecimal("100")))
        equal("25", Calculator.ratio(BigDecimal("50"), BigDecimal("200")))
        equal("300", Calculator.percent(BigDecimal("2000"), BigDecimal("15")))
        invalid { Calculator.discount(BigDecimal("2000"), BigDecimal("101")) }
        invalid { Calculator.discount(BigDecimal("-1"), BigDecimal("15")) }
    }

    @Test
    fun hourlyPayIncludesBonusBeforeDeductions() {
        val pay =
            Calculator.salary(
                BigDecimal("500"),
                BigDecimal("160"),
                null,
                BigDecimal("5000"),
                BigDecimal("13"),
            )
        equal("85000", pay.gross)
        equal("11050", pay.deduction)
        equal("73950", pay.net)
    }

    @Test
    fun monthlyPayIsProrated() {
        val pay =
            Calculator.salary(
                BigDecimal("60000"),
                BigDecimal("10"),
                BigDecimal("20"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        equal("30000", pay.net)
        invalid {
            Calculator.salary(
                BigDecimal.ONE,
                BigDecimal("21"),
                BigDecimal("20"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        }
        invalid {
            Calculator.salary(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        }
    }

    @Test
    fun salaryRejectsNegativeAmountsAndInvalidDeductions() {
        invalid {
            Calculator.salary(
                BigDecimal("-1"),
                BigDecimal.ONE,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        }
        invalid {
            Calculator.salary(
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                BigDecimal.ZERO,
                BigDecimal("101"),
            )
        }
        equal(
            "0",
            Calculator.salary(
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal("100"),
                )
                .net,
        )
    }
}
