package io.github.arcaneplugins.levelledmobs.misc

import org.bukkit.entity.Player

/**
 * Содержит информацию об игроке, когда он присоединяется к серверу или миру.
 *
 * @author stumper66
 * @since 3.2.3
 */
class PlayerQueueItem(
    val player: Player,
    val isPlayerJoin: Boolean
)