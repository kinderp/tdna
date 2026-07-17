package org.traveldna.location.contracts

import kotlin.jvm.JvmInline
import org.traveldna.geo.contracts.GeoPoint

@JvmInline
value class MonotonicInstant(val milliseconds: Long) : Comparable<MonotonicInstant> {
    init {
        require(milliseconds >= 0L) { "monotonic instant must be non-negative" }
    }

    override fun compareTo(other: MonotonicInstant): Int = milliseconds.compareTo(other.milliseconds)

    fun elapsedSince(earlier: MonotonicInstant): Long {
        require(this >= earlier) { "monotonic time cannot move backwards" }
        return milliseconds - earlier.milliseconds
    }

    override fun toString(): String = "${milliseconds}ms"

    companion object {
        val Zero = MonotonicInstant(0L)
    }
}

@JvmInline
value class LocationSequence(val value: Long) : Comparable<LocationSequence> {
    init {
        require(value >= 0L) { "location sequence must be non-negative" }
    }

    override fun compareTo(other: LocationSequence): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
}

enum class LocationSampleOrigin {
    Platform,
    Replay,
    Simulator,
}

/**
 * Provider-neutral, immutable location observation.
 *
 * Platform-specific objects such as Android `Location` and iOS `CLLocation`
 * are converted at their adapters before entering this contract.
 */
data class LocationSample(
    val sequence: LocationSequence,
    val monotonicTime: MonotonicInstant,
    val position: GeoPoint,
    val horizontalAccuracyMeters: Double,
    val speedMetersPerSecond: Double? = null,
    val bearingDegrees: Double? = null,
    val origin: LocationSampleOrigin,
) {
    init {
        require(
            horizontalAccuracyMeters.isFinite() &&
                horizontalAccuracyMeters > 0.0 &&
                horizontalAccuracyMeters <= MaxHorizontalAccuracyMeters,
        ) {
            "horizontal accuracy must be finite and within (0, $MaxHorizontalAccuracyMeters] metres"
        }
        require(
            speedMetersPerSecond == null ||
                (speedMetersPerSecond.isFinite() &&
                    speedMetersPerSecond >= 0.0 &&
                    speedMetersPerSecond <= MaxSpeedMetersPerSecond),
        ) {
            "speed must be null or finite and within [0, $MaxSpeedMetersPerSecond] m/s"
        }
        require(
            bearingDegrees == null ||
                (bearingDegrees.isFinite() && bearingDegrees >= 0.0 && bearingDegrees < 360.0),
        ) {
            "bearing must be null or finite and within [0, 360) degrees"
        }
    }

    companion object {
        const val MaxHorizontalAccuracyMeters: Double = 100_000.0
        const val MaxSpeedMetersPerSecond: Double = 200.0
    }
}

enum class LocationSampleRejectionReason {
    NonIncreasingSequence,
    NonIncreasingMonotonicTime,
}

sealed interface LocationSampleDecision {
    val sample: LocationSample

    data class Accepted(
        override val sample: LocationSample,
    ) : LocationSampleDecision

    data class Rejected(
        override val sample: LocationSample,
        val reason: LocationSampleRejectionReason,
        val lastAcceptedSequence: LocationSequence,
        val lastAcceptedTime: MonotonicInstant,
    ) : LocationSampleDecision
}

/**
 * Bounded-state stream gate that accepts only strictly increasing sequence and
 * monotonic time. It never silently sorts or rewrites samples.
 */
class LocationSampleGate {
    var lastAccepted: LocationSample? = null
        private set

    fun evaluate(sample: LocationSample): LocationSampleDecision {
        val previous = lastAccepted
        if (previous != null) {
            if (sample.sequence <= previous.sequence) {
                return LocationSampleDecision.Rejected(
                    sample = sample,
                    reason = LocationSampleRejectionReason.NonIncreasingSequence,
                    lastAcceptedSequence = previous.sequence,
                    lastAcceptedTime = previous.monotonicTime,
                )
            }
            if (sample.monotonicTime <= previous.monotonicTime) {
                return LocationSampleDecision.Rejected(
                    sample = sample,
                    reason = LocationSampleRejectionReason.NonIncreasingMonotonicTime,
                    lastAcceptedSequence = previous.sequence,
                    lastAcceptedTime = previous.monotonicTime,
                )
            }
        }
        lastAccepted = sample
        return LocationSampleDecision.Accepted(sample)
    }

    fun reset() {
        lastAccepted = null
    }
}
