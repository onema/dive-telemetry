package io.onema.divetelemetry.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {
    // --- formatTwoDecimals ---

    @Test
    fun `formatTwoDecimals whole number drops decimal`() {
        // Arrange / Act
        val result = formatTwoDecimals(10.0)

        // Assert
        assertEquals("10", result)
    }

    @Test
    fun `formatTwoDecimals zero`() {
        // Arrange / Act
        val result = formatTwoDecimals(0.0)

        // Assert
        assertEquals("0", result)
    }

    @Test
    fun `formatTwoDecimals strips trailing zero`() {
        // Arrange / Act
        val result = formatTwoDecimals(1.50)

        // Assert
        assertEquals("1.5", result)
    }

    @Test
    fun `formatTwoDecimals keeps two digits when both nonzero`() {
        // Arrange / Act
        val result = formatTwoDecimals(3.14)

        // Assert
        assertEquals("3.14", result)
    }

    @Test
    fun `formatTwoDecimals rounds up`() {
        // Arrange / Act
        val result = formatTwoDecimals(1.555)

        // Assert
        assertEquals("1.56", result)
    }

    @Test
    fun `formatTwoDecimals rounds down`() {
        // Arrange / Act
        val result = formatTwoDecimals(1.554)

        // Assert
        assertEquals("1.55", result)
    }

    @Test
    fun `formatTwoDecimals negative value`() {
        // Arrange / Act
        val result = formatTwoDecimals(-3.14)

        // Assert
        assertEquals("-3.14", result)
    }

    @Test
    fun `formatTwoDecimals small fraction`() {
        // Arrange / Act
        val result = formatTwoDecimals(0.01)

        // Assert
        assertEquals("0.01", result)
    }

    @Test
    fun `formatTwoDecimals large value`() {
        // Arrange / Act
        val result = formatTwoDecimals(12345.67)

        // Assert
        assertEquals("12345.67", result)
    }

    @Test
    fun `formatTwoDecimals single decimal digit`() {
        // Arrange / Act
        val result = formatTwoDecimals(6.4)

        // Assert
        assertEquals("6.4", result)
    }

    // --- formatNegatedDepth ---

    @Test
    fun `formatNegatedDepth negates positive value`() {
        // Arrange / Act
        val result = formatNegatedDepth(6.4)

        // Assert
        assertEquals("-6.4", result)
    }

    @Test
    fun `formatNegatedDepth zero returns zero`() {
        // Arrange / Act
        val result = formatNegatedDepth(0.0)

        // Assert
        assertEquals("0", result)
    }

    @Test
    fun `formatNegatedDepth already negative becomes positive`() {
        // Arrange / Act
        val result = formatNegatedDepth(-5.0)

        // Assert
        assertEquals("5", result)
    }

    @Test
    fun `formatNegatedDepth negates whole number`() {
        // Arrange / Act
        val result = formatNegatedDepth(100.0)

        // Assert
        assertEquals("-100", result)
    }

    // --- fractionToPercentStr ---

    @Test
    fun `fractionToPercentStr standard oxygen`() {
        // Arrange / Act
        val result = fractionToPercentStr(0.21)

        // Assert
        assertEquals("21", result)
    }

    @Test
    fun `fractionToPercentStr nitrox 32`() {
        // Arrange / Act
        val result = fractionToPercentStr(0.32)

        // Assert
        assertEquals("32", result)
    }

    @Test
    fun `fractionToPercentStr zero`() {
        // Arrange / Act
        val result = fractionToPercentStr(0.0)

        // Assert
        assertEquals("0", result)
    }

    @Test
    fun `fractionToPercentStr full oxygen`() {
        // Arrange / Act
        val result = fractionToPercentStr(1.0)

        // Assert
        assertEquals("100", result)
    }

    // --- formatBoolean ---

    @Test
    fun `formatBoolean true returns TRUE`() {
        // Arrange / Act
        val result = formatBoolean(true)

        // Assert
        assertEquals("TRUE", result)
    }

    @Test
    fun `formatBoolean false returns FALSE`() {
        // Arrange / Act
        val result = formatBoolean(false)

        // Assert
        assertEquals("FALSE", result)
    }

    // --- formatWaterTemp ---

    @Test
    fun `formatWaterTemp rounds to integer`() {
        // Arrange / Act
        val result = formatWaterTemp(77.6)

        // Assert
        assertEquals("78", result)
    }

    @Test
    fun `formatWaterTemp rounds down`() {
        // Arrange / Act
        val result = formatWaterTemp(77.4)

        // Assert
        assertEquals("77", result)
    }

    @Test
    fun `formatWaterTemp whole number`() {
        // Arrange / Act
        val result = formatWaterTemp(25.0)

        // Assert
        assertEquals("25", result)
    }

    @Test
    fun `formatWaterTemp zero`() {
        // Arrange / Act
        val result = formatWaterTemp(0.0)

        // Assert
        assertEquals("0", result)
    }

    // --- formatMinSec ---

    @Test
    fun `formatMinSec zero seconds`() {
        // Arrange / Act
        val result = formatMinSec(0L)

        // Assert
        assertEquals("0:00", result)
    }

    @Test
    fun `formatMinSec single digit seconds padded`() {
        // Arrange / Act
        val result = formatMinSec(5L)

        // Assert
        assertEquals("0:05", result)
    }

    @Test
    fun `formatMinSec minutes and seconds`() {
        // Arrange / Act
        val result = formatMinSec(185L)

        // Assert
        assertEquals("3:05", result)
    }

    @Test
    fun `formatMinSec exact minute boundary`() {
        // Arrange / Act
        val result = formatMinSec(180L)

        // Assert
        assertEquals("3:00", result)
    }

    @Test
    fun `formatMinSec large value`() {
        // Arrange / Act
        val result = formatMinSec(3661L)

        // Assert
        assertEquals("61:01", result)
    }

    // --- gasMixture ---

    @Test
    fun `gasMixture returns Air for 21 percent O2 and 0 He`() {
        // Arrange / Act
        val result = gasMixture(0.21, 0.0)

        // Assert
        assertEquals("Air", result)
    }

    @Test
    fun `gasMixture returns Nx for nitrox`() {
        // Arrange / Act
        val result = gasMixture(0.32, 0.0)

        // Assert
        assertEquals("Nx32", result)
    }

    @Test
    fun `gasMixture returns Nx for pure oxygen`() {
        // Arrange / Act
        val result = gasMixture(1.0, 0.0)

        // Assert
        assertEquals("Nx100", result)
    }

    @Test
    fun `gasMixture returns empty for trimix`() {
        // Arrange / Act
        val result = gasMixture(0.18, 0.45)

        // Assert
        assertEquals("", result)
    }

    @Test
    fun `gasMixture returns empty when helium present`() {
        // Arrange / Act
        val result = gasMixture(0.21, 0.10)

        // Assert
        assertEquals("", result)
    }
}
