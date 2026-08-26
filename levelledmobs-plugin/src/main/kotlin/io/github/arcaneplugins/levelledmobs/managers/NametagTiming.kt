package io.github.arcaneplugins.levelledmobs.managers

internal object NametagTiming {
    fun cooldownTicks(cooldownMillis: Long): Long =
        ((cooldownMillis + 49L) / 50L).coerceAtLeast(1L)
}
