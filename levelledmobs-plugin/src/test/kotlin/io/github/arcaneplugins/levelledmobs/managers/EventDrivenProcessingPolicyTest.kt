package io.github.arcaneplugins.levelledmobs.managers

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EventDrivenProcessingPolicyTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 10, 11, 12])
    fun `spawn and damage processing do not have a player threshold`(onlinePlayers: Int) {
        assertTrue(EventDrivenProcessingPolicy.processSpawns(onlinePlayers))
        assertTrue(EventDrivenProcessingPolicy.processNonPlayerDamage(onlinePlayers))
    }
}
