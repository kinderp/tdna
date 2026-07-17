package org.traveldna.location.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.traveldna.geo.contracts.GeoPoint

class LocationModelsTest {
    private val point = GeoPoint(37.5, 15.1)

    @Test
    fun validatesSensorMetadata() {
        assertFailsWith<IllegalArgumentException> {
            sample(0, 0, accuracy = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            sample(0, 0, speed = 201.0)
        }
        assertFailsWith<IllegalArgumentException> {
            sample(0, 0, bearing = 360.0)
        }
    }

    @Test
    fun gateAcceptsOnlyStrictlyIncreasingSequenceAndTime() {
        val gate = LocationSampleGate()
        assertIs<LocationSampleDecision.Accepted>(gate.evaluate(sample(0, 0)))
        assertIs<LocationSampleDecision.Accepted>(gate.evaluate(sample(1, 1_000)))

        val duplicate = assertIs<LocationSampleDecision.Rejected>(gate.evaluate(sample(1, 1_500)))
        assertEquals(LocationSampleRejectionReason.NonIncreasingSequence, duplicate.reason)

        val backwards = assertIs<LocationSampleDecision.Rejected>(gate.evaluate(sample(2, 500)))
        assertEquals(LocationSampleRejectionReason.NonIncreasingMonotonicTime, backwards.reason)

        assertIs<LocationSampleDecision.Accepted>(gate.evaluate(sample(2, 2_000)))
    }

    @Test
    fun rejectedSampleDoesNotAdvanceGateState() {
        val gate = LocationSampleGate()
        val first = sample(5, 5_000)
        gate.evaluate(first)
        gate.evaluate(sample(4, 6_000))
        assertEquals(first, gate.lastAccepted)
    }

    private fun sample(
        sequence: Long,
        time: Long,
        accuracy: Double = 5.0,
        speed: Double? = 20.0,
        bearing: Double? = 90.0,
    ): LocationSample = LocationSample(
        sequence = LocationSequence(sequence),
        monotonicTime = MonotonicInstant(time),
        position = point,
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        bearingDegrees = bearing,
        origin = LocationSampleOrigin.Replay,
    )
}
