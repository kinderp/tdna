package org.traveldna.navigation.progress

import org.traveldna.navigation.contracts.MatchedRoutePosition
import org.traveldna.navigation.contracts.RouteManeuverCursor
import org.traveldna.navigation.contracts.RouteProgressDecision
import org.traveldna.navigation.contracts.RouteProgressRejectionReason
import org.traveldna.navigation.contracts.RouteProgressSnapshot
import org.traveldna.routing.contracts.RoutePlan

/**
 * Single-owner progress tracker bound to one immutable canonical route.
 *
 * It receives positions already matched by an upstream component. Equal route
 * coordinates are allowed for a stationary vehicle; backwards coordinates are
 * explicit rejections and never mutate accepted state.
 */
class RouteProgressTracker(
    val route: RoutePlan,
) {
    private val maneuverCursors: List<RouteManeuverCursor> = route.legs.flatMapIndexed { legIndex, leg ->
        leg.maneuvers.mapIndexed { maneuverIndex, maneuver ->
            RouteManeuverCursor(legIndex, maneuverIndex, maneuver)
        }
    }

    var lastSnapshot: RouteProgressSnapshot? = null
        private set

    fun inspect(position: MatchedRoutePosition): RouteProgressDecision {
        val previous = lastSnapshot?.position
        rejection(position, previous)?.let { return it }
        return RouteProgressDecision.Accepted(buildSnapshot(position))
    }

    fun accept(position: MatchedRoutePosition): RouteProgressDecision {
        val decision = inspect(position)
        if (decision is RouteProgressDecision.Accepted) {
            lastSnapshot = decision.snapshot
        }
        return decision
    }

    fun reset() {
        lastSnapshot = null
    }

    private fun rejection(
        position: MatchedRoutePosition,
        previous: MatchedRoutePosition?,
    ): RouteProgressDecision.Rejected? {
        if (position.routeId != route.id) {
            return rejected(position, RouteProgressRejectionReason.RouteMismatch, previous)
        }
        val coordinate = position.coordinate
        if (coordinate.completedGeometryIndex !in route.geometry.indices) {
            return rejected(position, RouteProgressRejectionReason.GeometryIndexOutOfBounds, previous)
        }
        if (
            coordinate.completedGeometryIndex == route.geometry.lastIndex &&
            coordinate.fractionToNext != 0.0
        ) {
            return rejected(position, RouteProgressRejectionReason.FinalPointHasFraction, previous)
        }
        if (previous != null) {
            if (position.sampleSequence <= previous.sampleSequence) {
                return rejected(position, RouteProgressRejectionReason.NonIncreasingSequence, previous)
            }
            if (position.monotonicTime <= previous.monotonicTime) {
                return rejected(position, RouteProgressRejectionReason.NonIncreasingMonotonicTime, previous)
            }
            if (position.coordinate < previous.coordinate) {
                return rejected(position, RouteProgressRejectionReason.RegressedAlongRoute, previous)
            }
        }
        return null
    }

    private fun buildSnapshot(position: MatchedRoutePosition): RouteProgressSnapshot {
        val coordinate = position.coordinate
        val arrived = coordinate.completedGeometryIndex == route.geometry.lastIndex
        val activeLegIndex = if (arrived) {
            route.legs.lastIndex
        } else {
            route.legs.indexOfFirst { coordinate.completedGeometryIndex < it.geometryEndIndex }
                .also { check(it >= 0) { "route coordinate is not covered by any leg" } }
        }
        val upcomingManeuver = maneuverCursors.firstOrNull { cursor ->
            val maneuverIndex = cursor.maneuver.geometryIndex
            maneuverIndex > coordinate.completedGeometryIndex ||
                (maneuverIndex == coordinate.completedGeometryIndex && coordinate.fractionToNext == 0.0)
        }
        return RouteProgressSnapshot(
            position = position,
            activeLegIndex = activeLegIndex,
            upcomingManeuver = upcomingManeuver,
            arrived = arrived,
        )
    }

    private fun rejected(
        position: MatchedRoutePosition,
        reason: RouteProgressRejectionReason,
        previous: MatchedRoutePosition?,
    ): RouteProgressDecision.Rejected = RouteProgressDecision.Rejected(
        position = position,
        reason = reason,
        previousAccepted = previous,
    )
}
