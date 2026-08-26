package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import java.util.function.Consumer
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent

/** Maintains the exact player/entity pairs for which nametag packets are useful. */
class EntityTrackingListener : Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onTrack(event: PlayerTrackEntityEvent) {
        val entity = event.entity
        if (entity !is LivingEntity || entity is Player) return

        val main = LevelledMobs.instance
        main.nametagQueueManager.track(entity, event.player)
        entity.scheduler.run(
            main,
            Consumer {
                main.levelManager.handleTrackedEntity(entity, event.player)
            },
            null
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onUntrack(event: PlayerUntrackEntityEvent) {
        val entity = event.entity
        if (entity !is LivingEntity || entity is Player) return
        LevelledMobs.instance.nametagQueueManager.untrack(entity, event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRemove(event: EntityRemoveEvent) {
        val entity = event.entity
        if (entity !is LivingEntity || entity is Player) return
        LevelledMobs.instance.nametagQueueManager.clearEntity(entity)
    }
}
