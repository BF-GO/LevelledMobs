package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.enums.NametagVisibilityEnum
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent

/** Updates event-driven nametag visibility when a mob changes target. */
class EntityTargetListener : Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onTarget(event: EntityTargetEvent) {
        val entity = event.entity as? LivingEntity ?: return
        val main = LevelledMobs.instance
        val targetPlayer = event.target as? Player
        val lmEntity = LivingEntityWrapper.getInstance(entity)

        if (!lmEntity.isLevelled) {
            main.nametagQueueManager.setTarget(entity, null)
            if (EntitySpawnListener.instance.processMobSpawns) {
                lmEntity.free()
                return
            }

            if (lmEntity.getMobLevel < 0)
                lmEntity.reEvaluateLevel = true

            main.mobsQueueManager.addToQueue(QueueItem(lmEntity, event))
            lmEntity.free()
            return
        }

        main.nametagQueueManager.setTarget(
            entity,
            targetPlayer?.takeIf {
                lmEntity.nametagVisibilityEnum.contains(NametagVisibilityEnum.TRACKING)
            }
        )
        main.levelManager.updateNametag(lmEntity)
        lmEntity.free()
    }
}
