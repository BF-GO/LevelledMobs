package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.util.Log
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable

/**
 * Предоставляет логику того, когда игрок входит в портал.
 * Используется для различных заполнителей расстояния появления.
 *
 * @author stumper66
 * @since 3.3.0
 */
class PlayerPortalEventListener : Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlayerPortalEvent(event: PlayerPortalEvent) {
        if (event.cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)
            return

        if (event.to.world == null) return

        val isToNether = (event.to.world.environment == World.Environment.NETHER)
        val player = event.player
        val main = LevelledMobs.instance

        // хранить координаты портала игрока в Пустоте.  используется только для прокачки игрока
        main.mainCompanion.setPlayerNetherPortalLocation(player, event.to)
        val locationStr = "${event.to.world.name},${event.to.blockX},${event.to.blockY},${event.to.blockZ}"

        val runnable: BukkitRunnable = object : BukkitRunnable() {
            override fun run() {
                if (isToNether)
                    main.mainCompanion.setPlayerNetherPortalLocation(player, player.location)
                else
                    main.mainCompanion.setPlayerWorldPortalLocation(player, player.location)

                try {
                    if (isToNether) {
                        event.player.persistentDataContainer
                            .set(
                                NamespacedKeys.playerNetherCoords, PersistentDataType.STRING,
                                locationStr
                            )
                    } else {
                        event.player.persistentDataContainer
                            .set(
                                NamespacedKeys.playerNetherCoordsIntoWorld,
                                PersistentDataType.STRING, locationStr
                            )
                    }
                } catch (e: ConcurrentModificationException) {
                    Log.warKey("console.player.pdc-update-error", mapOf(
                        "player" to player.name,
                        "error" to (e.message ?: "-")
                    ))
                }
            }
        }

        // по какой-то причине событие # getTo имеет другие координаты, чем реальный портал Нижнего мира.
        // задержка на 1 билет и вместо этого получение местоположения игрока
        runnable.runTaskLater(main, 1L)
    }
}
