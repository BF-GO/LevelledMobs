package io.github.arcaneplugins.levelledmobs.compatibility

import java.util.TreeSet
import org.bukkit.entity.EntityType

/**
 * Содержит списки типов объектов, которые присутствуют только в Minecraft 1.21 и новее.  Должно быть
 * отдельный класс для обеспечения совместимости со старыми версиями
 *
 * @author stumper66
 * @since 3.14.0
 */
object Compat121 {
    fun getPassiveMobs(): MutableSet<EntityType> {
        return mutableSetOf(
            EntityType.valueOf("ARMADILLO")
        )
    }

    fun all21Mobs(): MutableSet<String> {
        val names: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
        names.add("ARMADILLO")
        return names
    }
}