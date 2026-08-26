package io.github.arcaneplugins.levelledmobs.managers

/** Compatibility settings no longer disable event processing at a player-count boundary. */
internal object EventDrivenProcessingPolicy {
    fun processSpawns(@Suppress("UNUSED_PARAMETER") onlinePlayers: Int): Boolean = true

    fun processNonPlayerDamage(@Suppress("UNUSED_PARAMETER") onlinePlayers: Int): Boolean = true
}
