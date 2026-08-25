package io.github.arcaneplugins.levelledmobs.customdrops

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages

import org.bukkit.enchantments.Enchantment

/**
 * Используется в сочетании с пользовательским дропом для обработки.
 * шансы на уровень зачарования
 *
 * @author stumper66
 * @since 3.7.0
 */
class EnchantmentChances {
    val items = mutableMapOf<Enchantment, MutableMap<Int, Float>>()
    val options = mutableMapOf<Enchantment, ChanceOptions>()

    val isEmpty: Boolean
        get() = items.isEmpty()

    class ChanceOptions {
        var defaultLevel: Int? = null
        var doShuffle: Boolean = true
    }

    override fun toString(): String {
        return LocalizedMessages.text(
            "display.customdrops.enchantment-chances",
            mapOf("count" to items.size),
            false
        )
    }
}
