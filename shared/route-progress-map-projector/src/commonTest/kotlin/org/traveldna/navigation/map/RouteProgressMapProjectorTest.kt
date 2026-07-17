package org.traveldna.navigation.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.traveldna.geo.contracts.GeoPoint
import org.traveldna.location.contracts.LocationSequence
import org.traveldna.location.contracts.MonotonicInstant
import org.traveldna.map.contracts.MapItemId
import org.traveldna.map.contracts.MapSceneId
import org.traveldna.map.contracts.RouteOverlay
import org.traveldna.map.contracts.RouteOverlayRole
import org.traveldna.navigation.contracts.MatchConfidence
import org.traveldna.navigation.contracts.MatchedRoutePosition
import org.traveldna.navigation.contracts.RouteCoordinate
import org.traveldna.navigation.contracts.RouteProgressSnapshot
import org.traveldna.routing.contracts.RouteId

class RouteProgressMapProjectorTest {
    @Test
    fun emitsCompactProgressForTheMatchingOverlay() {
        val routeId = RouteId("projected-progress-route-v0")
        val overlay = overlay(routeId)
        val snapshot = snapshot(routeId)

        val delta = RouteProgressMapProjector.project(
            sceneId = MapSceneId("scene.progress-v0"),
            overlay = overlay,
            snapshot = snapshot,
        )

        assertEquals(overlay.id, delta.routeOverlayId)
        assertEquals(1, delta.progress.completedGeometryIndex)
        assertEquals(0.25, delta.progress.fractionToNext)
    }

    @Test
    fun rejectsOverlayForAnotherRoute() {
        assertFailsWith<IllegalArgumentException> {
            RouteProgressMapProjector.project(
                sceneId = MapSceneId("scene.progress-v0"),
                overlay = overlay(RouteId("overlay-route-v0")),
                snapshot = snapshot(RouteId("snapshot-route-v0")),
            )
        }
    }

    private fun overlay(routeId: RouteId): RouteOverlay = RouteOverlay(
        id = MapItemId("route.progress"),
        routeId = routeId,
        geometry = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.01, 0.01)),
        role = RouteOverlayRole.Primary,
    )

    private fun snapshot(routeId: RouteId): RouteProgressSnapshot = RouteProgressSnapshot(
        position = MatchedRoutePosition(
            routeId = routeId,
            sampleSequence = LocationSequence(1),
            monotonicTime = MonotonicInstant(1_000L),
            coordinate = RouteCoordinate(1, 0.25),
            lateralDistanceMeters = 1.0,
            confidence = MatchConfidence.High,
        ),
        activeLegIndex = 0,
        upcomingManeuver = null,
        arrived = false,
    )
}
