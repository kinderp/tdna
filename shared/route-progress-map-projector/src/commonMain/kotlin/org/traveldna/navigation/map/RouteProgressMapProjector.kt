package org.traveldna.navigation.map

import org.traveldna.map.contracts.MapSceneDelta
import org.traveldna.map.contracts.MapSceneId
import org.traveldna.map.contracts.RouteOverlay
import org.traveldna.map.contracts.RouteOverlayProgress
import org.traveldna.navigation.contracts.RouteProgressSnapshot

/** Converts canonical navigation progress into one compact map-scene delta. */
object RouteProgressMapProjector {
    fun project(
        sceneId: MapSceneId,
        overlay: RouteOverlay,
        snapshot: RouteProgressSnapshot,
    ): MapSceneDelta.UpdateRouteProgress {
        require(overlay.routeId == snapshot.position.routeId) {
            "route-progress snapshot and map overlay refer to different routes"
        }
        val coordinate = snapshot.position.coordinate
        require(coordinate.completedGeometryIndex in overlay.geometry.indices) {
            "route-progress index lies outside the installed overlay geometry"
        }
        require(
            coordinate.completedGeometryIndex != overlay.geometry.lastIndex ||
                coordinate.fractionToNext == 0.0,
        ) {
            "final overlay geometry point cannot have next-segment progress"
        }
        return MapSceneDelta.UpdateRouteProgress(
            sceneId = sceneId,
            routeOverlayId = overlay.id,
            progress = RouteOverlayProgress(
                completedGeometryIndex = coordinate.completedGeometryIndex,
                fractionToNext = coordinate.fractionToNext,
            ),
        )
    }
}
