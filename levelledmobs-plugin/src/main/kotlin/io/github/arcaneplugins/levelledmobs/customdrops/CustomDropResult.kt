package io.github.arcaneplugins.levelledmobs.customdrops

import org.bukkit.inventory.ItemStack

/**
 * Используется внутри, чтобы определить, следует ли удалять ванильные предметы моба или нет.
 *
 * @author stumper66
 * @since 2.6.0
 */
class CustomDropResult(
    val stackToItem: MutableList<Map.Entry<ItemStack, CustomDropItem>>,
    val hasOverride: Boolean
)