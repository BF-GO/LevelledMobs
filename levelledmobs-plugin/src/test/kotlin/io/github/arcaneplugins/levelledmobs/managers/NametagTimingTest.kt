package io.github.arcaneplugins.levelledmobs.managers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NametagTimingTest {
    @Test
    fun `five second visibility expires after one hundred ticks`() {
        assertEquals(100L, NametagTiming.cooldownTicks(5_000L))
    }

    @Test
    fun `partial ticks round up and never become zero`() {
        assertEquals(1L, NametagTiming.cooldownTicks(0L))
        assertEquals(2L, NametagTiming.cooldownTicks(51L))
    }
}
