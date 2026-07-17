package org.traveldna.location.replay

import org.traveldna.location.contracts.MonotonicInstant

/** Deterministic clock advanced explicitly by accepted replay samples. */
class VirtualReplayClock(
    initial: MonotonicInstant = MonotonicInstant.Zero,
) {
    var now: MonotonicInstant = initial
        private set

    fun advanceTo(target: MonotonicInstant): Long {
        val delta = target.elapsedSince(now)
        now = target
        return delta
    }

    fun reset(target: MonotonicInstant = MonotonicInstant.Zero) {
        now = target
    }
}

/**
 * Converts source-time deltas to playback delays while preserving fractional
 * remainder across samples and avoiding cumulative integer-rounding drift.
 */
internal class ReplayDelayScaler(
    private val rate: PlaybackRate,
) {
    private var remainder: Long = 0L

    fun scale(sourceDeltaMilliseconds: Long): Long {
        require(sourceDeltaMilliseconds >= 0L) { "source delta must be non-negative" }
        require(sourceDeltaMilliseconds <= (Long.MAX_VALUE - remainder) / rate.denominator) {
            "playback delay scaling overflows Long"
        }
        val scaledNumerator = sourceDeltaMilliseconds * rate.denominator + remainder
        val delay = scaledNumerator / rate.numerator
        remainder = scaledNumerator % rate.numerator
        return delay
    }
}
