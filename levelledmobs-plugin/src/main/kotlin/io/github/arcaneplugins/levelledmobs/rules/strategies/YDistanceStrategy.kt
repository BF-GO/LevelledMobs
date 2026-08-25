package io.github.arcaneplugins.levelledmobs.rules.strategies

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages

import java.util.concurrent.ThreadLocalRandom
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import kotlin.math.floor

/**
 * Содержит конфигурацию и логику применения стратегии назначения уровня, основанной на расстоянии.
 * высота уровня y
 *
 * @author stumper66
 * @since 3.0.0
 */
class YDistanceStrategy : LevellingStrategy, Cloneable {
    var startingYLevel: Int? = null
    var endingYLevel: Int? = null
    var yPeriod: Int? = null
    var increasePerLevel: Float? = null

    override val strategyType = StrategyType.Y_COORDINATE
    override var shouldMerge: Boolean = false

    override fun mergeRule(levellingStrategy: LevellingStrategy?) {
        if (levellingStrategy is YDistanceStrategy)
            mergeYDistanceStrategy(levellingStrategy as YDistanceStrategy?)
    }

    private fun mergeYDistanceStrategy(yds: YDistanceStrategy?) {
        if (yds == null) return

        if (yds.startingYLevel != null)
            this.startingYLevel = yds.startingYLevel

        if (yds.endingYLevel != null)
            this.endingYLevel = yds.endingYLevel

        if (yds.yPeriod != null)
            this.yPeriod = yds.yPeriod

        if (yds.increasePerLevel != null)
            this.increasePerLevel = yds.increasePerLevel
    }

    override fun toString(): String {
        return LocalizedMessages.text(
            "display.strategies.y-distance-details",
            mapOf(
                "start" to (startingYLevel ?: 0),
                "end" to (endingYLevel ?: 0),
                "period" to (yPeriod ?: 0),
                "increase" to (increasePerLevel ?: 0)
            ),
            false
        )
    }

    override fun generateNumber(
        lmEntity: LivingEntityWrapper,
        minLevel: Int,
        maxLevel: Int
    ): Float {
        var mobYLocation = lmEntity.livingEntity.location.blockY.toFloat()
        val yStart = if (this.startingYLevel == null) 0f else startingYLevel!!.toFloat()
        val yEnd = if (this.endingYLevel == null) 0f else endingYLevel!!.toFloat()
        val yPeriod = if (this.yPeriod == null) 0f else yPeriod!!.toFloat()
        val useLevel: Float
        val isDescending = (yStart > yEnd)

        val highest = if (isDescending) yStart else yEnd
        val lowest = if (isDescending) yEnd else yStart
        val diff = if (isDescending) yStart - yEnd else yEnd - yStart

        // убедитесь, что местоположение моба не зашло за конец или начало
        if (isDescending && yPeriod == 0f && increasePerLevel == null)
            mobYLocation = mobYLocation.coerceAtMost(highest)

        if (yPeriod == 0f && increasePerLevel == null)
            mobYLocation = mobYLocation.coerceAtLeast(lowest)

        val distanceBelow = if (isDescending)
            highest - mobYLocation
        else
            mobYLocation - lowest

        if (increasePerLevel != null){
            val firstStep = if (isDescending)
                yStart - mobYLocation
            else
                mobYLocation - yStart

            useLevel = floor(firstStep / increasePerLevel!!)
        }
        else if (yPeriod != 0f) {
            val lvlPerPeriod = (maxLevel - minLevel) / yPeriod
            val periodBelow = distanceBelow / yPeriod
            useLevel = minLevel + (lvlPerPeriod * periodBelow)
        } else {
            val useMobYLocation = distanceBelow.toDouble()
            val percent = (useMobYLocation / diff).toFloat()
            useLevel = minLevel + ((maxLevel - minLevel) * percent)
        }

        return useLevel
    }

    private fun getVariance(
        lmEntity: LivingEntityWrapper,
        isAtMaxLevel: Boolean
    ): Int {
        val variance = LevelledMobs.instance.rulesManager.getRuleMaxRandomVariance(
            lmEntity
        )
        if (variance == null || variance == 0)
            return 0

        val change = ThreadLocalRandom.current().nextInt(0, variance + 1)

        // Начать вариацию. Сначала проверьте, является ли отклонение положительным или отрицательным по отношению к исходной сумме уровня.
        return if (!isAtMaxLevel || ThreadLocalRandom.current().nextBoolean()) {
            // Позитивный. Добавьте вариацию на финальный уровень
            change
        } else {
            // Отрицательный. Вычтите вариацию из конечного уровня
            -change
        }
    }

    override fun cloneItem(): YDistanceStrategy {
        var copy: YDistanceStrategy? = null
        try {
            copy = super.clone() as YDistanceStrategy
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return copy!!
    }
}
