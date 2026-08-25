package io.github.arcaneplugins.levelledmobs.rules.strategies

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.debug.DebugType
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.result.MinAndMaxHolder
import io.github.arcaneplugins.levelledmobs.result.PlayerLevelSourceResult
import io.github.arcaneplugins.levelledmobs.rules.LevelTierMatching
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.persistence.PersistentDataType
import kotlin.math.roundToInt

/**
 * Соблюдает любые правила, касающиеся повышения уровня игрока.
 *
 * @author stumper66
 * @since 3.1.0
 */
class PlayerLevellingStrategy : LevellingStrategy, Cloneable {
    val levelTiers = mutableListOf<LevelTierMatching>()
    var defaultLevelTier: LevelTierMatching? = null
    var matchVariable: Boolean? = null
    var enabled: Boolean? = null
    var usevariableAsMax: Boolean? = null
    var recheckPlayers: Boolean? = null
    var outputCap: Float? = null
    var preserveEntityTime: Long? = null
    var playerVariableScale: Float? = null
    var variable: String? = null
    var decreaseOutput = true

    override val strategyType = StrategyType.PLAYER_VARIABLE
    override var shouldMerge: Boolean = false

    override fun generateNumber(
        lmEntity: LivingEntityWrapper,
        minLevel: Int,
        maxLevel: Int
    ): Float{
        val options = lmEntity.main.rulesManager.getRulePlayerLevellingOptions(
            lmEntity
        )

        if (options == null || !options.getEnabled)
            return 0f

        val player = lmEntity.associatedPlayer ?: return 0f

        val variableToUse =
            if (options.variable.isNullOrEmpty()) "%level%" else options.variable!!
        val scale = if (options.playerVariableScale != null) options.playerVariableScale!! else 1f
        val playerLevelSourceResult = lmEntity.main.levelManager.getPlayerLevelSourceNumber(
            lmEntity.associatedPlayer, lmEntity, variableToUse
        )

        val origLevelSource = if (playerLevelSourceResult.isNumericResult)
                playerLevelSourceResult.numericResult
            else
                1f

        applyValueToPdc(lmEntity, playerLevelSourceResult)
        val levelSource = (origLevelSource * scale).coerceAtLeast(0f)

        val results = MinAndMaxHolder(0f, 0f)
        var tierMatched: String? = null
        val capDisplay = if (options.outputCap == null) "" else
            LocalizedMessages.text(
                "command.levelledmobs.debug.runtime.d207",
                mapOf("value" to options.outputCap),
                false
            )

        if (options.getMatchVariable) {
            results.min = levelSource
            results.max = results.min
        }
        else if (options.getVariableAsMax)
            results.max = levelSource
        else {
            var foundMatch = false
            for (tier in options.levelTiers) {
                var meetsMin = false
                var meetsMax = false
                var hasStringMatch = false

                if (tier.sourceTierName != null) {
                    hasStringMatch = playerLevelSourceResult.stringResult.equals(
                        tier.sourceTierName, ignoreCase = true
                    )
                } else if (playerLevelSourceResult.isNumericResult) {
                    meetsMin = (tier.minLevel == null || levelSource >= tier.minLevel!!)
                    meetsMax = (tier.maxLevel == null || levelSource <= tier.maxLevel!!)
                }

                if (meetsMin && meetsMax || hasStringMatch) {
                    if (tier.valueRanges!!.min> 0f)
                        results.min = tier.valueRanges!!.min

                    if (tier.valueRanges!!.max > 0f)
                        results.max = tier.valueRanges!!.max

                    tierMatched = tier.toString()
                    foundMatch = true
                    break
                }
            }

            if (!foundMatch && options.defaultLevelTier != null){
                foundMatch = true
                tierMatched = options.defaultLevelTier.toString()
                results.min = options.defaultLevelTier!!.valueRanges!!.min
                results.max = options.defaultLevelTier!!.valueRanges!!.max
            }

            if (!foundMatch) {
                if (playerLevelSourceResult.isNumericResult) {
                    DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d140", mapOf("value-1" to (player.name), "value-2" to (origLevelSource), "value-3" to (levelSource), "value-4" to (capDisplay)), false)
                    }
                } else {
                    DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d141", mapOf("value-1" to (player.name), "value-2" to (playerLevelSourceResult.stringResult), "value-3" to (capDisplay)), false)
                    }
                }
                if (options.outputCap != null) {
                    results.max = results.max.coerceAtMost(options.outputCap!!)
                    return calculateResult(results)
                } else
                    return 0f
            }
        }

        val varianceDebug: String
        if (playerLevelSourceResult.randomVarianceResult != null) {
            playerLevelSourceResult.randomVarianceResult =
                playerLevelSourceResult.randomVarianceResult!! + playerLevelSourceResult.randomVarianceResult!!
            // убедитесь, что минимальное значение составляет не менее 1
            results.min = results.min.coerceAtLeast(1f)
            // убедитесь, что минимальное значение не превышает максимальное значение
            results.min = results.min.coerceAtMost(results.max)

            varianceDebug = LocalizedMessages.text(
                "command.levelledmobs.debug.runtime.d208",
                mapOf("value" to playerLevelSourceResult.randomVarianceResult),
                false
            )
        } else
            varianceDebug = ""

        if (options.outputCap != null) {
            results.max = results.max.coerceAtMost(options.outputCap!!)
            results.min = results.min.coerceAtMost(options.outputCap!!)
        }

        val homeName = if (playerLevelSourceResult.homeNameUsed != null)
            " (${playerLevelSourceResult.homeNameUsed})"
        else ""

        if (tierMatched == null) {
            DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d142", mapOf("value-1" to (player.name), "value-2" to (origLevelSource), "value-3" to (homeName), "value-4" to (varianceDebug), "value-5" to (levelSource), "value-6" to (capDisplay), "value-7" to (results)), false)
            }
        } else {
            val tierMatchedFinal: String = tierMatched
            if (playerLevelSourceResult.isNumericResult) {
                DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d143", mapOf("value-1" to (player.name), "value-2" to (origLevelSource), "value-3" to (homeName), "value-4" to (varianceDebug), "value-5" to (levelSource), "value-6" to (tierMatchedFinal), "value-7" to (capDisplay), "value-8" to (results)), false)
                }
            } else {
                DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d144", mapOf("value-1" to (player.name), "value-2" to (playerLevelSourceResult.stringResult), "value-3" to (varianceDebug), "value-4" to (tierMatchedFinal), "value-5" to (capDisplay), "value-6" to (results)), false)
                }
            }
        }

        if (options.getRecheckPlayers) {
            val numberOrString =
                if (playerLevelSourceResult.isNumericResult)
                    playerLevelSourceResult.numericResult.toString()
                else
                    playerLevelSourceResult.stringResult

            if (numberOrString != null) lmEntity.pdc.set(
                NamespacedKeys.playerLevellingSourceNumber,
                PersistentDataType.STRING,
                numberOrString
            )
        }
        lmEntity.playerLevellingAllowDecrease = options.decreaseOutput

        return calculateResult(results)
    }

    private fun calculateResult(minAndMax: MinAndMaxHolder): Float{
        if (minAndMax.min == minAndMax.max) return minAndMax.min

        val useMin = minAndMax.min.roundToInt()
        val useMax = minAndMax.max.roundToInt().coerceAtLeast(useMin)
        if (useMin == useMax) return useMin.toFloat()

        return ThreadLocalRandom.current().nextInt(useMin, useMax + 1).toFloat()
    }

    override fun mergeRule(levellingStrategy: LevellingStrategy?) {
        if (levellingStrategy == null || levellingStrategy !is PlayerLevellingStrategy)
            return

        levelTiers.addAll(levellingStrategy.levelTiers)
        if (levellingStrategy.matchVariable != null)
            this.matchVariable = levellingStrategy.matchVariable

        if (levellingStrategy.usevariableAsMax != null)
            this.usevariableAsMax = levellingStrategy.usevariableAsMax

        if (levellingStrategy.playerVariableScale != null)
            this.playerVariableScale = levellingStrategy.playerVariableScale

        if (levellingStrategy.outputCap != null)
            this.outputCap = levellingStrategy.outputCap

        if (variable != null)
            this.variable = levellingStrategy.variable

        if (levellingStrategy.enabled != null)
            this.enabled = levellingStrategy.enabled

        if (levellingStrategy.recheckPlayers != null)
            this.recheckPlayers = levellingStrategy.recheckPlayers
    }

    override fun cloneItem(): LevellingStrategy {
        var copy: PlayerLevellingStrategy? = null
        try {
            copy = super.clone() as PlayerLevellingStrategy
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return copy as LevellingStrategy
    }

    val getMatchVariable: Boolean
        get() = this.matchVariable != null && matchVariable!!

    val getEnabled: Boolean
        // «Включено» по умолчанию истинно, если не отключено специально
        get() = this.enabled == null || enabled!!

    val getVariableAsMax: Boolean
        get() = this.usevariableAsMax != null && usevariableAsMax!!

    val getRecheckPlayers: Boolean
        get() = this.recheckPlayers != null && recheckPlayers!!

    private fun applyValueToPdc(
        lmEntity: LivingEntityWrapper,
        playerLevel: PlayerLevelSourceResult
    ) {
        val value =
            if (playerLevel.isNumericResult)
                playerLevel.numericResult.toString()
            else
                playerLevel.stringResult?: "(null)"

        try {
            lmEntity.pdc.set(
                NamespacedKeys.playerLevellingValue,
                PersistentDataType.STRING,
                value
            )
        } catch (_: Exception) { }
    }

    override fun toString(): String {
        val sb = StringBuilder()

        if (!getEnabled)
            sb.append(LocalizedMessages.text("display.player-level-source.disabled", colorize = false))

        if (variable != null) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append(
                LocalizedMessages.text("display.player-level-source.variable", colorize = false)
            ).append(variable)
        }

        if (getMatchVariable) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append("match-plr-lvl")
        }

        if (getVariableAsMax) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append(
                LocalizedMessages.text(
                    "display.player-level-source.use-player-max-level",
                    colorize = false
                )
            )
        }

        if (playerVariableScale != null) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append(
                LocalizedMessages.text("display.player-level-source.scale", colorize = false)
            ).append(playerVariableScale)
        }

        if (outputCap != null) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append(
                LocalizedMessages.text("display.player-level-source.cap", colorize = false)
            ).append(outputCap)
        }

        if (levelTiers.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append(levelTiers)
        }

        if (decreaseOutput) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append("decrease-lvl")
        }

        if (getRecheckPlayers) {
            if (sb.isNotEmpty()) sb.append(", ")

            sb.append("rechk-plr")
        }

        return if (sb.isEmpty())
            super.toString()
        else
            sb.toString()
    }
}
