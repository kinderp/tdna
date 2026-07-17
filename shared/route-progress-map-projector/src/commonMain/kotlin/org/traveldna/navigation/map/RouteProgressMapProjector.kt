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
        return MapSceneDelta.UpdateRouteProgress(
            sceneId = sceneId,
            routeOverlayId = overlay.id,
            progress = RouteOverlayProgress(
                completedGeometryIndex = snapshot.position.coordinate.completedGeometryIndex,
                fractionToNext = snapshot.position.coordinate.fractionToNext,
            ),
        )
    }
}
