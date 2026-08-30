package io.github.arcaneplugins.levelledmobs.summoning

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.RequestedLevel

/** Prevents command-created mobs from bypassing Judgement Week progression. */
object JudgementSummonPolicy {
    private const val CONFIG_ROOT = "judgement-week-summoning"
    private const val DEFAULT_HIGH_LEVEL_START = 300

    fun configuredMaximumLevel(isCommandSpawn: Boolean = true): Int? {
        val root = LevelledMobs.instance.helperSettings.cs.getConfigurationSection(CONFIG_ROOT)
        val allowHighLevels = root?.getBoolean("allow-high-level-summons", false) ?: false
        val highLevelStart = root?.getInt("high-level-start", DEFAULT_HIGH_LEVEL_START)
            ?: DEFAULT_HIGH_LEVEL_START
        return maximumLevel(isCommandSpawn, allowHighLevels, highLevelStart)
    }

    fun maximumLevel(
        isCommandSpawn: Boolean,
        allowHighLevels: Boolean,
        highLevelStart: Int
    ): Int? {
        if (!isCommandSpawn || allowHighLevels) return null
        return highLevelStart.coerceAtLeast(1) - 1
    }

    fun limit(requestedLevel: RequestedLevel, maximumLevel: Int): Boolean {
        val maximum = maximumLevel.coerceAtLeast(0)
        if (!requestedLevel.hasLevelRange) {
            if (requestedLevel.level <= maximum) return false
            requestedLevel.level = maximum
            return true
        }

        val oldMin = requestedLevel.levelRangeMin
        val oldMax = requestedLevel.levelRangeMax
        requestedLevel.levelRangeMin = oldMin.coerceAtMost(maximum)
        requestedLevel.levelRangeMax = oldMax.coerceAtMost(maximum)
            .coerceAtLeast(requestedLevel.levelRangeMin)
        return oldMin != requestedLevel.levelRangeMin || oldMax != requestedLevel.levelRangeMax
    }
}
