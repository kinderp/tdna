package org.traveldna.geo.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeoPointTest {
    @Test
    fun acceptsWgs84Boundaries() {
        assertEquals(GeoPoint(-90.0, -180.0), GeoPoint(-90.0, -180.0))
        assertEquals(GeoPoint(90.0, 180.0), GeoPoint(90.0, 180.0))
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, 181.0) }
    }
}
