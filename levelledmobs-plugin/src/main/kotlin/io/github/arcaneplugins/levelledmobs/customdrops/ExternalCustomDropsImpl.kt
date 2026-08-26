package io.github.arcaneplugins.levelledmobs.customdrops

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import org.bukkit.entity.EntityType

/**
 * Этот класс позволяет сторонним организациям добавлять собственный дроп непосредственно в LevelledMobs.
 *
 * @author stumper66
 * @since 3.7.0
 */
class ExternalCustomDropsImpl : ExternalCustomDrops {
    val customDropsitems = ConcurrentHashMap<EntityType, CustomDropInstance>()
    val customDropIDs: MutableMap<String, CustomDropInstance> =
        ConcurrentSkipListMap(String.CASE_INSENSITIVE_ORDER)

    override fun addCustomDrop(customDropInstance: CustomDropInstance) {
        customDropsitems[customDropInstance.associatedMob!!] = customDropInstance
    }

    override fun addCustomDropTable(dropName: String, customDropInstance: CustomDropInstance) {
        customDropIDs[dropName] = customDropInstance
    }

    override fun getCustomDrops(): Map<EntityType, CustomDropInstance?> {
        return this.customDropsitems
    }

    override fun getCustomDropTables(): Map<String, CustomDropInstance?> {
        return this.customDropIDs
    }

    override fun clearAllExternalCustomDrops() {
        customDropsitems.clear()
        customDropIDs.clear()
    }
}
