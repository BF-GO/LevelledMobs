package io.github.arcaneplugins.levelledmobs.customdrops

import org.bukkit.entity.EntityType

/**
 * Предоставляет интерфейс для сторонних плагинов для
 * добавить собственный дроп
 *
 * @author stumper66
 * @since 3.7.0
 */
interface ExternalCustomDrops {
    fun addCustomDrop(customDropInstance: CustomDropInstance)

    fun addCustomDropTable(dropName: String, customDropInstance: CustomDropInstance)

    fun getCustomDrops(): Map<EntityType, CustomDropInstance?>

    fun getCustomDropTables(): Map<String, CustomDropInstance?>

    fun clearAllExternalCustomDrops()
}