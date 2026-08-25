package io.github.arcaneplugins.levelledmobs.compatibility

import java.util.TreeSet
import org.bukkit.entity.EntityType

/**
 * Содержит списки типов объектов, которые присутствуют только в Minecraft 1.19 и новее.  Должно быть
 * отдельный класс для обеспечения совместимости со старыми версиями
 *
 * @author stumper66
 * @since 3.6.0
 */
object Compat119 {
    fun getPassiveMobs(): MutableSet<EntityType> {
        return mutableSetOf(
            EntityType.ALLAY,
            EntityType.TADPOLE,
            EntityType.FROG
        )
    }

    fun getHostileMobs(): MutableSet<EntityType> {
        return mutableSetOf(
            EntityType.WARDEN
        )
    }

    fun getAquaticMobs(): MutableSet<EntityType> {
        return mutableSetOf(
            EntityType.TADPOLE
        )
    }

    fun all19Mobs(): MutableSet<String> {
        val names: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
        names.addAll(listOf("ALLAY", "TADPOLE", "FROG", "WARDEN"))
        return names
    }
}