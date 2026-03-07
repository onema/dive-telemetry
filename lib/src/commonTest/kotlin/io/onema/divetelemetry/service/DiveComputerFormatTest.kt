package io.onema.divetelemetry.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiveComputerFormatTest {

    @Test
    fun `ShearwaterFormat has correct id`() {
        // Arrange / Act / Assert
        assertEquals("shearwater", ShearwaterFormat.id)
    }

    @Test
    fun `ShearwaterFormat has correct name`() {
        // Arrange / Act / Assert
        assertEquals("Shearwater CSV", ShearwaterFormat.name)
    }

    @Test
    fun `ShearwaterFormat has csv extension`() {
        // Arrange / Act / Assert
        assertEquals(listOf(".csv"), ShearwaterFormat.extensions)
    }

    @Test
    fun `ShearwaterFormat creates ShearwaterDiveLogParser`() {
        // Arrange / Act
        val parser = ShearwaterFormat.createParser()

        // Assert
        assertIs<ShearwaterDiveLogParser>(parser)
    }

    @Test
    fun `GarminFormat has correct id`() {
        // Arrange / Act / Assert
        assertEquals("garmin", GarminFormat.id)
    }

    @Test
    fun `GarminFormat has correct name`() {
        // Arrange / Act / Assert
        assertEquals("Garmin FIT", GarminFormat.name)
    }

    @Test
    fun `GarminFormat has fit extension`() {
        // Arrange / Act / Assert
        assertEquals(listOf(".fit"), GarminFormat.extensions)
    }

    @Test
    fun `GarminFormat creates GarminDiveLogParser`() {
        // Arrange / Act
        val parser = GarminFormat.createParser()

        // Assert
        assertIs<GarminDiveLogParser>(parser)
    }

    @Test
    fun `Dl7Format has correct id`() {
        // Arrange / Act / Assert
        assertEquals("DL7", Dl7Format.id)
    }

    @Test
    fun `Dl7Format has correct name`() {
        // Arrange / Act / Assert
        assertEquals("DAN-DL7 ZXU", Dl7Format.name)
    }

    @Test
    fun `Dl7Format has zxu extension`() {
        // Arrange / Act / Assert
        assertEquals(listOf(".zxu"), Dl7Format.extensions)
    }

    @Test
    fun `Dl7Format creates Dl7DiveLogParser`() {
        // Arrange / Act
        val parser = Dl7Format.createParser()

        // Assert
        assertIs<Dl7DiveLogParser>(parser)
    }

    @Test
    fun `defaultComputerFormats contains all three formats in order`() {
        // Arrange / Act / Assert
        assertEquals(3, defaultComputerFormats.size)
        assertIs<ShearwaterFormat>(defaultComputerFormats[0])
        assertIs<GarminFormat>(defaultComputerFormats[1])
        assertIs<Dl7Format>(defaultComputerFormats[2])
    }

    @Test
    fun `defaultComputerFormats ids are unique`() {
        // Arrange / Act
        val ids = defaultComputerFormats.map { it.id }

        // Assert
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun `each format creates a fresh parser instance`() {
        // Arrange / Act
        val parser1 = ShearwaterFormat.createParser()
        val parser2 = ShearwaterFormat.createParser()

        // Assert
        assertTrue(parser1 !== parser2)
    }
}
