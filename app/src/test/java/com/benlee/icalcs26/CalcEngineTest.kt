package com.benlee.icalcs26

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalcEngineTest {
    @Test fun precedence() = assertEquals(14.0, CalcEngine.evaluate("2+3×4"), 1e-10)
    @Test fun brackets() = assertEquals(20.0, CalcEngine.evaluate("(2+3)×4"), 1e-10)
    @Test fun scientific() = assertEquals(8.0, CalcEngine.evaluate("2^3"), 1e-10)
    @Test fun percentAddition() = assertEquals(115.0, CalcEngine.evaluate("100+15%"), 1e-10)
    @Test fun percentMultiplication() = assertEquals(20.0, CalcEngine.evaluate("200×10%"), 1e-10)
    @Test fun degreeTrigonometry() = assertEquals(.5, CalcEngine.evaluate("sin(30)"), 1e-10)
    @Test fun divideByZero() { assertThrows(IllegalArgumentException::class.java) { CalcEngine.evaluate("1÷0") } }
    @Test fun thousandGrouping() = assertEquals("1,234,567.89", CalcEngine.format(1234567.89))
}
