package org.traveldna.location.replay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.traveldna.geo.contracts.GeoPoint
import org.traveldna.location.contracts.LocationSample
import org.traveldna.location.contracts.LocationSampleOrigin
import org.traveldna.location.contracts.LocationSampleRejectionReason
import org.traveldna.location.contracts.LocationSequence
import org.traveldna.location.contracts.MonotonicInstant

class DeterministicReplayRunnerTest {
    @Test
    fun playbackRateNormalizesAndPreservesRemainder() {
        assertEquals(PlaybackRate.RealTime, PlaybackRate.of(2, 2))
        val scaler = ReplayDelayScaler(PlaybackRate.of(3, 2))
        assertEquals(0L, scaler.scale(1L))
        assertEquals(1L, scaler.scale(1L))
        assertEquals(1L, scaler.scale(1L))
    }

    @Test
    fun replayReportsAcceptedAndRejectedSamplesWithoutSorting() {
        val runner = DeterministicReplayRunner(referenceScenario())
        val summary = runner.runToEnd()

        assertEquals(ReplayState.Completed, summary.state)
        assertEquals(6, summary.processedSamples)
        assertEquals(4, summary.acceptedSamples)
        assertEquals(2, summary.rejectedSamples)
        assertEquals(
            mapOf(
                LocationSampleRejectionReason.NonIncreasingSequence to 1,
                LocationSampleRejectionReason.NonIncreasingMonotonicTime to 1,
            ),
            summary.rejectionCounts,
        )
        assertEquals(MonotonicInstant(3_000L), summary.finalClock)
        assertEquals(1_500L, summary.totalPlaybackDelayMilliseconds)
        assertEquals(LocationSequence(4), summary.lastAcceptedSequence)
    }

    @Test
    fun rejectedSamplesDoNotMoveVirtualClock() {
        val runner = DeterministicReplayRunner(referenceScenario())
        runner.start()
        assertIs<ReplayEvent.Accepted>(runner.advance())
        assertIs<ReplayEvent.Accepted>(runner.advance())
        val duplicate = assertIs<ReplayEvent.Rejected>(runner.advance())
        assertEquals(MonotonicInstant(1_000L), duplicate.clock)
    }

    @Test
    fun pauseAndStepHaveExplicitStateTransitions() {
        val runner = DeterministicReplayRunner(referenceScenario())
        runner.start()
        runner.advance()
        runner.pause()
        assertEquals(ReplayState.Paused, runner.state)
        assertFailsWith<IllegalArgumentException> { runner.advance() }
        assertIs<ReplayEvent.Accepted>(runner.step())
        assertEquals(ReplayState.Paused, runner.state)
        runner.resume()
        assertEquals(ReplayState.Running, runner.state)
    }

    @Test
    fun cancellationStopsFurtherProcessing() {
        val runner = DeterministicReplayRunner(referenceScenario())
        runner.start()
        runner.advance()
        runner.cancel()
        assertEquals(ReplayState.Cancelled, runner.state)
        assertFailsWith<IllegalArgumentException> { runner.advance() }
        assertFailsWith<IllegalStateException> { runner.runToEnd() }
        assertEquals(1, runner.summary().processedSamples)
    }

    @Test
    fun scenarioSnapshotsInputAndBoundsSampleCount() {
        val mutableSamples = mutableListOf(sample(0, 0))
        val scenario = LocationReplayScenario("snapshot.v0", mutableSamples)
        mutableSamples.clear()
        assertEquals(1, scenario.samples.size)
        assertFailsWith<IllegalArgumentException> {
            LocationReplayScenario("empty.v0", emptyList())
        }
    }

    private fun referenceScenario(): LocationReplayScenario = LocationReplayScenario(
        id = "reference-location-replay-v0",
        playbackRate = PlaybackRate.DoubleSpeed,
        samples = listOf(
            sample(0, 0),
            sample(1, 1_000),
            sample(1, 1_500),
            sample(2, 2_000),
            sample(3, 1_500),
            sample(4, 3_000),
        ),
    )

    private fun sample(sequence: Long, time: Long): LocationSample = LocationSample(
        sequence = LocationSequence(sequence),
        monotonicTime = MonotonicInstant(time),
        position = GeoPoint(37.5 + sequence * 0.001, 15.1),
        horizontalAccuracyMeters = 5.0,
        speedMetersPerSecond = 20.0,
        bearingDegrees = 90.0,
        origin = LocationSampleOrigin.Replay,
    )
}
