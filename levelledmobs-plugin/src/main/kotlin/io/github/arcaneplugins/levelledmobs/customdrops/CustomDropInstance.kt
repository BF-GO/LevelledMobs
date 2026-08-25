package io.github.arcaneplugins.levelledmobs.customdrops

import io.github.arcaneplugins.levelledmobs.misc.CustomUniversalGroups
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import org.bukkit.entity.EntityType

/**
 * Содержит экземпляр моба или группы и связывает его со списком пользовательских выпадающих предметов. Вот где
 * установлено переопределение для моба/группы
 *
 * @author stumper66
 * @since 2.4.0
 */
class CustomDropInstance {
    var associatedMob: EntityType? = null
        private set
    private var entityGroup: CustomUniversalGroups? = null
    var customItems = mutableListOf<CustomDropBase>()
    var overallChance: SlidingChance? = null
    var overallPermissions = mutableListOf<String>()
    var overrideStockDrops: Boolean? = null
    var utilizesGroupIds: Boolean = false
    var isBabyMob: Boolean = false

    constructor(associatedMob: EntityType?){
        this.associatedMob = associatedMob
    }

    constructor(associatedMob: EntityType?, isBabyMob: Boolean){
        this.associatedMob = associatedMob
        this.isBabyMob = isBabyMob
    }

    constructor(entityGroup: CustomUniversalGroups){
        this.entityGroup = entityGroup
    }

    fun combineDrop(dropInstance: CustomDropInstance?) {
        if (dropInstance == null)
            throw NullPointerException("dropInstance")

        this.overrideStockDrops = dropInstance.overrideStockDrops

        if (dropInstance.utilizesGroupIds)
            this.utilizesGroupIds = true

        customItems.addAll(dropInstance.customItems)
    }

    fun getMobOrGroupName(): String {
        return if (this.associatedMob != null)
            associatedMob!!.name
        else if (this.entityGroup != null)
            entityGroup!!.name
        else
            "" // это возвращение никогда не должно произойти
    }

    val getOverrideStockDrops: Boolean
        get() = this.overrideStockDrops != null && this.overrideStockDrops!!

    override fun toString(): String {
        return if (this.associatedMob != null)
            if (getOverrideStockDrops) associatedMob!!.name + LocalizedMessages.text(
                "display.customdrops.override-suffix",
                colorize = false
            ) else associatedMob!!.name
        else if (this.entityGroup != null)
            if (getOverrideStockDrops) entityGroup.toString() + LocalizedMessages.text(
                "display.customdrops.override-suffix",
                colorize = false
            ) else entityGroup.toString()
        else
            "CustomDropInstance"
    }
}
