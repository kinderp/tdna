package org.traveldna.navigation.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.traveldna.location.contracts.LocationSequence
import org.traveldna.location.contracts.MonotonicInstant
import org.traveldna.routing.contracts.RouteId

class NavigationProgressModelsTest {
    @Test
    fun routeCoordinateCanonicalizesSignedZero() {
        assertEquals(RouteCoordinate(2, 0.0), RouteCoordinate(2, -0.0))
        assertEquals(RouteCoordinate(2, 0.0).hashCode(), RouteCoordinate(2, -0.0).hashCode())
        assertEquals(0, RouteCoordinate(2, 0.0).compareTo(RouteCoordinate(2, -0.0)))
    }

    @Test
    fun validatesCoordinateAndLateralDistance() {
        assertFailsWith<IllegalArgumentException> { RouteCoordinate(-1, 0.0) }
        assertFailsWith<IllegalArgumentException> { RouteCoordinate(0, Double.NaN) }
        assertFailsWith<IllegalArgumentException> { RouteCoordinate(0, 1.0) }
        assertFailsWith<IllegalArgumentException> {
            position(lateralDistanceMeters = -1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            position(lateralDistanceMeters = MatchedRoutePosition.MaxLateralDistanceMeters + 1.0)
        }
        assertEquals(position(0.0), position(-0.0))
    }

    private fun position(lateralDistanceMeters: Double): MatchedRoutePosition = MatchedRoutePosition(
        routeId = RouteId("model-test-route-v0"),
        sampleSequence = LocationSequence(0),
        monotonicTime = MonotonicInstant.Zero,
        coordinate = RouteCoordinate(0, 0.0),
        lateralDistanceMeters = lateralDistanceMeters,
        confidence = MatchConfidence.High,
    )
}
