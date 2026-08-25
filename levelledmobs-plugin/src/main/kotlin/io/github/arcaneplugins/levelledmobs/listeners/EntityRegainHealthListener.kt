package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.managers.MobDataManager
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent

/**
 * Прослушивает, когда объект восстанавливает здоровье, чтобы можно было соответствующим образом обновить бейдж.
 *
 * @author konsolas, lokka30
 * @since 2.4.0
 */
class EntityRegainHealthListener : Listener {
    // Когда моб восстановит здоровье, попробуйте обновить его бейдж.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (event.entity !is LivingEntity) return

        // Убедитесь, что моб имеет уровень
        if (!LevelledMobs.instance.levelManager.isLevelled(event.entity as LivingEntity))
            return

        val lmEntity = LivingEntityWrapper.getInstance((event.entity as LivingEntity))
        MobDataManager.populateAttributeCache(lmEntity)

        LevelledMobs.instance.levelManager.updateNametagWithDelay(lmEntity)
        lmEntity.free()
    }
}