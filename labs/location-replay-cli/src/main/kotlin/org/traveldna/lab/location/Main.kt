package org.traveldna.lab.location

import java.nio.file.Path
import org.traveldna.geo.contracts.GeoPoint
import org.traveldna.location.contracts.LocationSample
import org.traveldna.location.contracts.LocationSampleOrigin
import org.traveldna.location.contracts.LocationSampleRejectionReason
import org.traveldna.location.contracts.LocationSequence
import org.traveldna.location.contracts.MonotonicInstant
import org.traveldna.location.replay.DeterministicReplayRunner
import org.traveldna.location.replay.LocationReplayScenario
import org.traveldna.location.replay.PlaybackRate
import org.traveldna.location.replay.ReplayState
import org.traveldna.location.replay.ReplaySummary

fun main(args: Array<String>) {
    when {
        args.size == 1 -> runFixture(Path.of(args[0]))
        args.size == 2 && args[0] == "--benchmark" -> runBenchmark(args[1].toInt())
        else -> {
            System.err.println("usage: location-replay-cli FIXTURE | --benchmark SAMPLE_COUNT")
            kotlin.system.exitProcess(2)
        }
    }
}

private fun runFixture(path: Path) {
    val fixture = ReplayFixtureParser.parse(path)
    val summary = DeterministicReplayRunner(fixture.scenario).runToEnd()
    fixture.expectations.requireMatches(summary)
    println(canonicalReplayReport(fixture.scenario, summary))
}

private fun runBenchmark(sampleCount: Int) {
    require(sampleCount in 1..LocationReplayScenario.MaxSamples) {
        "benchmark sample count must be within [1, ${LocationReplayScenario.MaxSamples}]"
    }
    val samples = List(sampleCount) { index ->
        LocationSample(
            sequence = LocationSequence(index.toLong()),
            monotonicTime = MonotonicInstant(index.toLong() * 100L),
            position = GeoPoint(37.5 + index * 0.0000001, 15.1),
            horizontalAccuracyMeters = 5.0,
            speedMetersPerSecond = 20.0,
            bearingDegrees = 90.0,
            origin = LocationSampleOrigin.Replay,
        )
    }
    val scenario = LocationReplayScenario(
        id = "benchmark-location-replay-v0",
        samples = samples,
        playbackRate = PlaybackRate.RealTime,
    )

    // One unmeasured pass warms JVM code paths. This is a diagnostic
    // microbenchmark, not a stable CI performance threshold.
    DeterministicReplayRunner(scenario).runToEnd()
    val started = System.nanoTime()
    val summary = DeterministicReplayRunner(scenario).runToEnd()
    val elapsed = System.nanoTime() - started
    val nanosPerSample = elapsed.toDouble() / sampleCount
    println(
        "{\"benchmark\":\"location-replay-v0\"," +
            "\"samples\":$sampleCount," +
            "\"accepted\":${summary.acceptedSamples}," +
            "\"elapsed_ns\":$elapsed," +
            "\"ns_per_sample\":${"%.2f".format(java.util.Locale.ROOT, nanosPerSample)}}",
    )
}

fun canonicalReplayReport(
    scenario: LocationReplayScenario,
    summary: ReplaySummary,
): String {
    require(summary.state == ReplayState.Completed) { "canonical report requires completed replay" }
    val sequenceCount = summary.rejectionCounts[
        LocationSampleRejectionReason.NonIncreasingSequence,
    ] ?: 0
    val timeCount = summary.rejectionCounts[
        LocationSampleRejectionReason.NonIncreasingMonotonicTime,
    ] ?: 0
    return "{\"scenario\":\"${scenario.id}\"," +
        "\"rate\":\"${scenario.playbackRate}\"," +
        "\"state\":\"completed\"," +
        "\"processed\":${summary.processedSamples}," +
        "\"accepted\":${summary.acceptedSamples}," +
        "\"rejected\":${summary.rejectedSamples}," +
        "\"rejection_counts\":{" +
        "\"non_increasing_monotonic_time\":$timeCount," +
        "\"non_increasing_sequence\":$sequenceCount}," +
        "\"final_time_ms\":${summary.finalClock.milliseconds}," +
        "\"playback_delay_ms\":${summary.totalPlaybackDelayMilliseconds}," +
        "\"last_sequence\":${summary.lastAcceptedSequence?.value}}"
}
