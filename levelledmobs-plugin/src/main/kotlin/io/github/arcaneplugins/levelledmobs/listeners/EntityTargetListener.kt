package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.NametagTimerChecker
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.enums.NametagVisibilityEnum
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent

/**
 * Используется в качестве обходного пути для обеспечения правильного обновления тегов имен мобов.
 *
 * @author stumper66
 * @since 2.4.0
 */
class EntityTargetListener : Listener {
    /**
     * Это событие прослушивается для обновления именной метки моба, когда он начинает нацеливаться на игрока.
     * Следует предоставить ещё одно временное исправление для пакетов, которые иногда не появляются в тегах имен мобов.
     *
     * @param event EntityTargetEvent
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onTarget(event: EntityTargetEvent) {
        if (event.entity !is LivingEntity)
            return

        val main = LevelledMobs.instance
        if (event.target == null) {
            synchronized(NametagTimerChecker.entityTarget_Lock) {
                main.nametagTimerChecker.entityTargetMap.remove(event.entity as LivingEntity)
            }
            return
        }

        // Должен быть нацелен на игрока и должен быть живым существом.
        if (event.target !is Player)
            return

        val lmEntity = LivingEntityWrapper.getInstance(event.entity as LivingEntity)

        // Должен быть уровневой сущностью
        if (!lmEntity.isLevelled) {
            if (EntitySpawnListener.instance.processMobSpawns) {
                lmEntity.free()
                return
            }

            if (lmEntity.getMobLevel < 0)
                lmEntity.reEvaluateLevel = true

            main.mobsQueueManager.addToQueue(QueueItem(lmEntity, event))
            return
        }

        if (lmEntity.nametagVisibilityEnum.contains(NametagVisibilityEnum.TRACKING)) {
            synchronized(NametagTimerChecker.entityTarget_Lock) {
                main.nametagTimerChecker.entityTargetMap.put(
                    lmEntity.livingEntity,
                    event.target as Player?
                )
            }
        }

        // Обновите бейдж.
        main.levelManager.updateNametag(lmEntity)
        lmEntity.free()
    }
}