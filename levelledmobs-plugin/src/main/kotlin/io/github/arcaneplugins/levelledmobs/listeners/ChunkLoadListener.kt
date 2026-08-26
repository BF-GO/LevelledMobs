package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

/**
 * Прослушивает загрузку чанков и соответствующим образом обрабатывает любых мобов. Требуется для запуска сервера.
 * и в основном для пассивных мобов, когда игроки перемещаются
 *
 * @author stumper66
 * @since 2.4.0
 */
class ChunkLoadListener : Listener {
    private var ensureMobsAreLevelledOnChunkLoad = true

    fun load(){
        ensureMobsAreLevelledOnChunkLoad = LevelledMobs.instance.helperSettings.getBoolean(
            "ensure-mobs-are-levelled-on-chunk-load", true
        )
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!ensureMobsAreLevelledOnChunkLoad) return

        // Проверьте каждую сущность в чанке
        for (entity in event.chunk.entities) {
            // Должно быть *живое* существо
            if (entity !is LivingEntity) continue

            checkEntity(entity, event)
        }
    }

    private fun checkEntity(livingEntity: LivingEntity, event: ChunkLoadEvent) {
        livingEntity.scheduler.run(LevelledMobs.instance, {
            val lmEntity = LivingEntityWrapper.getInstance(livingEntity)
            lmEntity.buildCacheIfNeeded()
            if (LevelledMobs.instance.levelManager.doCheckMobHash &&
                Utils.checkIfMobHashChanged(lmEntity)
            ) {
                lmEntity.reEvaluateLevel = true
                lmEntity.isRulesForceAll = true
                lmEntity.wasPreviouslyLevelled = lmEntity.isLevelled
            } else if (lmEntity.isLevelled) {
                lmEntity.free()
                return@run
            }

            LevelledMobs.instance.mobsQueueManager.addToQueue(QueueItem(lmEntity, event))
            lmEntity.free()
        }, null)
    }
}
