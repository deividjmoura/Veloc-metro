package com.gpsspeedometer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolylineDecoderTest {
    @Test
    fun decodesGoogleEncodedPolylineSample() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.00001)
        assertEquals(-120.2, points[0].longitude, 0.00001)
        assertEquals(40.7, points[1].latitude, 0.00001)
        assertEquals(-120.95, points[1].longitude, 0.00001)
        assertEquals(43.252, points[2].latitude, 0.00001)
        assertEquals(-126.453, points[2].longitude, 0.00001)
    }

    @Test
    fun rejectsEmptyPolyline() {
        assertThrows(IllegalStateException::class.java) {
            decodePolyline("")
        }
    }

    @Test
    fun rejectsTruncatedPolyline() {
        assertThrows(IllegalArgumentException::class.java) {
            decodePolyline("a")
        }
    }

    @Test
    fun rejectsCharactersOutsideEncodedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            decodePolyline("\u0000")
        }
    }
    @Test
    fun rejectsInvalidLongitudeEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            decodePolyline("?\u0000")
        }
    }

    @Test
    fun rejectsOverlongCoordinateEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            decodePolyline("}".repeat(8))
        }
    }

}
