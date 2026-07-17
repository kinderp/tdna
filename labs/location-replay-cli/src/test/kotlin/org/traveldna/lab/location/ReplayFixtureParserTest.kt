package org.traveldna.lab.location

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.traveldna.location.contracts.LocationSampleRejectionReason
import org.traveldna.location.replay.DeterministicReplayRunner

class ReplayFixtureParserTest {
    @Test
    fun parsesAndValidatesSyntheticFixture() {
        val path = writeFixture(validFixture())
        try {
            val parsed = ReplayFixtureParser.parse(path)
            val summary = DeterministicReplayRunner(parsed.scenario).runToEnd()
            parsed.expectations.requireMatches(summary)
            assertEquals("fixture-test-v0", parsed.scenario.id)
            assertEquals(2, summary.acceptedSamples)
            assertEquals(1, summary.rejectedSamples)
            assertEquals(
                1,
                summary.rejectionCounts[LocationSampleRejectionReason.NonIncreasingSequence],
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun rejectsUnknownDirective() {
        val path = writeFixture(
            "TDNA_LOCATION_REPLAY_V0\nscenario broken-v0\nrate 1 1\nunknown value\n",
        )
        try {
            assertFailsWith<IllegalArgumentException> { ReplayFixtureParser.parse(path) }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun rejectsExpectationMismatch() {
        val path = writeFixture(validFixture().replace("expect accepted 2", "expect accepted 3"))
        try {
            val parsed = ReplayFixtureParser.parse(path)
            val summary = DeterministicReplayRunner(parsed.scenario).runToEnd()
            assertFailsWith<IllegalArgumentException> {
                parsed.expectations.requireMatches(summary)
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun writeFixture(content: String) =
        Files.createTempFile("tdna-location-replay", ".tdna").also { Files.writeString(it, content) }

    private fun validFixture(): String = """
        TDNA_LOCATION_REPLAY_V0
        scenario fixture-test-v0
        rate 2 1
        sample 0 0 37.5 15.1 5.0 20.0 90.0 replay
        sample 1 1000 37.501 15.1 5.0 20.0 90.0 replay
        sample 1 1500 37.502 15.1 5.0 20.0 90.0 replay
        expect accepted 2
        expect rejected 1
        expect final_time_ms 1000
        expect playback_delay_ms 500
        expect last_sequence 1
        expect reason non_increasing_sequence 1
    """.trimIndent() + "\n"
}
