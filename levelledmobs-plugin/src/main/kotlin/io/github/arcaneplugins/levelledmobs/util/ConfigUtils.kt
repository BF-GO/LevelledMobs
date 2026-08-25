package io.github.arcaneplugins.levelledmobs.util

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.util.Utils.colorizeAllInList
import io.github.arcaneplugins.levelledmobs.util.Utils.replaceAllInList
import org.bukkit.command.CommandSender

/**
 * Используется для управления данными конфигурации.
 *
 * @author lokka30, stumper66
 * @since 2.5.0
 */
class ConfigUtils{
    companion object{
        private var SETTINGS_CREEPER_MAX_RADIUS = 0
        private var SETTINGS_SPAWN_DISTANCE_FROM_PLAYER = 0
    }

    var chunkLoadListenerWasEnabled = false

    fun load() {
        // все, что меньше 3, нарушает формулу
        SETTINGS_CREEPER_MAX_RADIUS =
            SETTINGS_CREEPER_MAX_RADIUS.coerceAtLeast(3)
        SETTINGS_SPAWN_DISTANCE_FROM_PLAYER =
            SETTINGS_SPAWN_DISTANCE_FROM_PLAYER.coerceAtLeast(1)
    }

    val prefix: String
        get() = LocalizedMessages.prefix()

    fun sendNoPermissionMsg(sender: CommandSender) {
        LocalizedMessages.send(sender, "common.no-permission")
    }
}
