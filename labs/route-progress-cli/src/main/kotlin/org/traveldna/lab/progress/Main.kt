package org.traveldna.lab.progress

import java.util.Locale
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
import org.traveldna.navigation.contracts.RouteProgressDecision
import org.traveldna.navigation.map.RouteProgressMapProjector
import org.traveldna.navigation.progress.RouteProgressTracker
import org.traveldna.plugin.sdk.PluginId
import org.traveldna.routing.contracts.ManeuverType
import org.traveldna.routing.contracts.RouteId
import org.traveldna.routing.contracts.RouteLeg
import org.traveldna.routing.contracts.RouteManeuver
import org.traveldna.routing.contracts.RoutePlan
import org.traveldna.routing.contracts.RouteProvenance

fun main(args: Array<String>) {
    when {
        args.isEmpty() -> runLab()
        args.size in 2..3 && args[0] == "--benchmark" -> {
            val samples = args[1].toInt()
            val iterations = args.getOrNull(2)?.toInt() ?: DefaultBenchmarkIterations
            runBenchmark(samples, iterations)
        }
        else -> {
            System.err.println("usage: route-progress-cli | --benchmark SAMPLE_COUNT [ODD_ITERATIONS]")
            kotlin.system.exitProcess(2)
        }
    }
}

private fun runLab() {
    val route = referenceRoute()
    val tracker = RouteProgressTracker(route)
    val positions = listOf(
        position(route.id, 0, 0, 0, 0.0),
        position(route.id, 1, 1_000, 0, 0.5),
        position(route.id, 2, 2_000, 2, 0.0),
        position(route.id, 3, 3_000, 1, 0.75),
        position(route.id, 4, 4_000, 2, 0.5),
        position(route.id, 5, 5_000, 3, 0.0),
    )

    var accepted = 0
    var rejected = 0
    var regressionReason = "none"
    positions.forEach { candidate ->
        when (val decision = tracker.accept(candidate)) {
            is RouteProgressDecision.Accepted -> accepted += 1
            is RouteProgressDecision.Rejected -> {
                rejected += 1
                regressionReason = decision.reason.name
            }
        }
    }

    val finalSnapshot = checkNotNull(tracker.lastSnapshot)
    val overlay = RouteOverlay(
        id = MapItemId("route.reference-progress"),
        routeId = route.id,
        geometry = route.geometry,
        role = RouteOverlayRole.Primary,
    )
    val delta = RouteProgressMapProjector.project(
        sceneId = MapSceneId("scene.reference-progress-v0"),
        overlay = overlay,
        snapshot = finalSnapshot,
    )
    val maneuver = finalSnapshot.upcomingManeuver?.maneuver?.type?.name ?: "none"

    println(
        "{\"scenario\":\"reference-route-progress-v0\"," +
            "\"accepted\":$accepted," +
            "\"rejected\":$rejected," +
            "\"rejection\":\"$regressionReason\"," +
            "\"completed_index\":${finalSnapshot.position.coordinate.completedGeometryIndex}," +
            "\"fraction\":${finalSnapshot.position.coordinate.fractionToNext}," +
            "\"active_leg\":${finalSnapshot.activeLegIndex}," +
            "\"upcoming_maneuver\":\"$maneuver\"," +
            "\"arrived\":${finalSnapshot.arrived}," +
            "\"delta_index\":${delta.progress.completedGeometryIndex}}",
    )
}

private fun runBenchmark(sampleCount: Int, iterations: Int) {
    require(sampleCount in 2..MaxBenchmarkSamples) {
        "benchmark sample count must be within [2, $MaxBenchmarkSamples]"
    }
    require(iterations in 1..MaxBenchmarkIterations && iterations % 2 == 1) {
        "benchmark iterations must be odd and within [1, $MaxBenchmarkIterations]"
    }
    val route = benchmarkRoute(sampleCount)
    val positions = List(sampleCount) { index ->
        position(route.id, index.toLong(), index.toLong() * 100L, index, 0.0)
    }

    repeat(BenchmarkWarmups) {
        runBenchmarkIteration(route, positions)
    }
    val elapsed = LongArray(iterations) {
        val started = System.nanoTime()
        runBenchmarkIteration(route, positions)
        System.nanoTime() - started
    }.sorted()
    val median = elapsed[elapsed.size / 2]
    println(
        "{\"benchmark\":\"route-progress-v0\"," +
            "\"samples\":$sampleCount," +
            "\"warmups\":$BenchmarkWarmups," +
            "\"iterations\":$iterations," +
            "\"min_elapsed_ns\":${elapsed.first()}," +
            "\"median_elapsed_ns\":$median," +
            "\"max_elapsed_ns\":${elapsed.last()}," +
            "\"median_ns_per_sample\":${"%.2f".format(Locale.ROOT, median.toDouble() / sampleCount)}}",
    )
}

private fun runBenchmarkIteration(route: RoutePlan, positions: List<MatchedRoutePosition>) {
    val tracker = RouteProgressTracker(route)
    positions.forEach { position ->
        check(tracker.accept(position) is RouteProgressDecision.Accepted)
    }
    check(tracker.lastSnapshot?.arrived == true)
}

private fun referenceRoute(): RoutePlan {
    val points = listOf(
        GeoPoint(0.0, 0.0),
        GeoPoint(0.0, 0.01),
        GeoPoint(0.01, 0.01),
        GeoPoint(0.01, 0.02),
    )
    return RoutePlan(
        id = RouteId("reference-progress-route-v0"),
        geometry = points,
        legs = listOf(
            RouteLeg(
                0,
                2,
                points[0],
                points[2],
                2_400L,
                160L,
                listOf(
                    RouteManeuver(0, ManeuverType.Depart, points[0], "Depart"),
                    RouteManeuver(1, ManeuverType.TurnRight, points[1], "Turn right"),
                ),
            ),
            RouteLeg(
                2,
                3,
                points[2],
                points[3],
                1_200L,
                80L,
                listOf(
                    RouteManeuver(2, ManeuverType.Continue, points[2], "Continue"),
                    RouteManeuver(3, ManeuverType.Arrive, points[3], "Arrive"),
                ),
            ),
        ),
        distanceMeters = 3_600L,
        durationSeconds = 240L,
        provenance = RouteProvenance(PluginId("org.traveldna.progress-lab")),
    )
}

private fun benchmarkRoute(sampleCount: Int): RoutePlan {
    val points = List(sampleCount) { index -> GeoPoint(0.0, index * 0.000001) }
    val distance = sampleCount.toLong()
    return RoutePlan(
        id = RouteId("benchmark-progress-route-v0"),
        geometry = points,
        legs = listOf(
            RouteLeg(0, points.lastIndex, points.first(), points.last(), distance, distance, emptyList()),
        ),
        distanceMeters = distance,
        durationSeconds = distance,
        provenance = RouteProvenance(PluginId("org.traveldna.progress-benchmark")),
    )
}

private fun position(
    routeId: RouteId,
    sequence: Long,
    time: Long,
    index: Int,
    fraction: Double,
): MatchedRoutePosition = MatchedRoutePosition(
    routeId = routeId,
    sampleSequence = LocationSequence(sequence),
    monotonicTime = MonotonicInstant(time),
    coordinate = RouteCoordinate(index, fraction),
    lateralDistanceMeters = 1.5,
    confidence = MatchConfidence.High,
)

private const val BenchmarkWarmups: Int = 3
private const val DefaultBenchmarkIterations: Int = 7
private const val MaxBenchmarkIterations: Int = 25
private const val MaxBenchmarkSamples: Int = 100_000
