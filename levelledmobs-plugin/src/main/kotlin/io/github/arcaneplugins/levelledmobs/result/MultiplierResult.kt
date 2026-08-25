package io.github.arcaneplugins.levelledmobs.result

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages

/**
 * Содержит значения, используемые для применения баффов множителя.
 *
 * @author stumper66
 * @since 4.0
 */
data class MultiplierResult(
    val multiplierAmount: Float,
    val baseModAmount: Float?,
    val isAddition: Boolean
){
    override fun toString(): String {
        return LocalizedMessages.text(
            "display.result.multiplier",
            mapOf(
                "multiplier" to multiplierAmount,
                "base" to baseModAmount,
                "addition" to isAddition
            ),
            false
        )
    }
}
