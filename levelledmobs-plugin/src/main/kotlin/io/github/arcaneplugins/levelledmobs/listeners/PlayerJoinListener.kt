package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.MainCompanion
import io.github.arcaneplugins.levelledmobs.commands.CommandHandler
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.MessageUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType

/** Handles player lifecycle state which is independent of entity tracking. */
class PlayerJoinListener : Listener {
    private val main = LevelledMobs.instance

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (event.player.isOp && main.debugManager.playerThatEnabledDebug == null)
            main.debugManager.playerThatEnabledDebug = event.player

        main.maxPlayersRecorded = maxOf(main.maxPlayersRecorded, Bukkit.getOnlinePlayers().size)
        main.mainCompanion.addRecentlyJoinedPlayer(event.player)
        checkForNetherPortalCoords(event.player)
        parseUpdateChecker(event.player)

        if (event.player.isOp)
            processOpOnlyStuff(event.player)
    }

    private fun processOpOnlyStuff(player: Player) {
        val notifyAdmins = main.helperSettings.getBoolean(
            "notify-admins-of-errors-upon-join", true
        )
        if (notifyAdmins && MainCompanion.instance.errorMessages.isNotEmpty())
            LocalizedMessages.send(player, "other.join-errors-reported")

        if (CommandHandler.hadErrorLoading) {
            if (!main.ver.isRunningPaper)
                LocalizedMessages.send(player, "other.command-framework-spigot")
            else
                LocalizedMessages.send(player, "other.command-framework-error")
        }
    }

    private fun checkForNetherPortalCoords(player: Player) {
        val keys = listOf(
            NamespacedKeys.playerNetherCoords,
            NamespacedKeys.playerNetherCoordsIntoWorld
        )
        try {
            for ((index, key) in keys.withIndex()) {
                val netherCoords = player.persistentDataContainer
                    .get(key, PersistentDataType.STRING) ?: continue
                val coords = netherCoords.split(",")
                if (coords.size != 4) continue
                val world = Bukkit.getWorld(coords[0]) ?: continue
                val location = Location(
                    world,
                    coords[1].toDouble(),
                    coords[2].toDouble(),
                    coords[3].toDouble()
                )
                if (index == 0)
                    main.mainCompanion.setPlayerNetherPortalLocation(player, location)
                else
                    main.mainCompanion.setPlayerWorldPortalLocation(player, location)
            }
        } catch (ex: Exception) {
            Log.warKey(
                "console.player.invalid-nether-coordinates",
                mapOf("player" to player.name, "error" to (ex.message ?: "-"))
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        main.placeholderApiIntegration?.playedLoggedOut(event.player)
        main.mainCompanion.spawnerCopyIds.remove(event.player.uniqueId)
        main.mainCompanion.spawnerInfoIds.remove(event.player.uniqueId)
        main.mainCompanion.clearPlayerState(event.player)
        main.nametagQueueManager.clearPlayer(event.player)
        main.placeholderApiIntegration?.removePlayer(event.player)
    }

    private fun parseUpdateChecker(player: Player) {
        if (main.messagesCfg.getBoolean("other.update-notice.send-on-join", true) &&
            player.hasPermission("levelledmobs.receive-update-notifications")
        ) {
            main.mainCompanion.updateResult.forEach { player.sendMessage(MessageUtils.colorizeAll(it)) }
        }
    }
}
