package io.github.arcaneplugins.levelledmobs.customdrops

import java.util.random.RandomGenerator
import java.util.concurrent.ThreadLocalRandom

internal object RandomSingleGroupSelector {
    fun passesChance(chance: Double, roll: Double): Boolean {
        if (chance <= 0.0) return false
        if (chance >= 1.0) return true
        return roll < chance
    }

    fun selectIndex(candidateCount: Int, chance: Double): Int? {
        return selectIndex(candidateCount, chance, ThreadLocalRandom.current())
    }

    fun selectIndex(
        candidateCount: Int,
        chance: Double,
        random: RandomGenerator
    ): Int? {
        if (candidateCount <= 0) return null

        if (!passesChance(chance, random.nextDouble())) return null
        return random.nextInt(candidateCount)
    }
}
