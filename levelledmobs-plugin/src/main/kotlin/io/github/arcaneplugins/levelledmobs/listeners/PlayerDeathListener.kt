package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.listeners.paper.PlayerDeathListener
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.SpigotUtils
import org.bukkit.Bukkit
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

/**
 * Слушает, когда игрок умирает
 *
 * @author stumper66
 * @since 2.6.0
 */
class PlayerDeathListener : Listener {
    private var paperListener: PlayerDeathListener? = null
    private var lastPriority: EventPriority? = null
    private val settingName = "player-death-event"

    fun load(){
        if (LevelledMobs.instance.ver.isRunningPaper && paperListener == null)
            paperListener = PlayerDeathListener()

        val priority = LevelledMobs.instance.mainCompanion.getEventPriority(settingName, EventPriority.NORMAL)
        if (lastPriority != null){
            if (priority == lastPriority) return

            HandlerList.unregisterAll(this)
            Log.infKey("console.events.priority-changed", mapOf("event" to settingName, "old" to lastPriority.toString(), "new" to priority.toString()))
        }

        Bukkit.getPluginManager().registerEvent(
            PlayerDeathEvent::class.java,
            this,
            priority,
            { _, event -> if (event is PlayerDeathEvent) onPlayerDeath(event) },
            LevelledMobs.instance,
            false
        )
        lastPriority = priority
    }

    /**
     * Этот прослушиватель обрабатывает теги смерти, поэтому мы можем определить, какой моб его убил, и обновить
     * сообщение о смерти соответственно
     *
     * @param event PlayerDeathEvent
     */
    private fun onPlayerDeath(event: PlayerDeathEvent) {
        // возвращает false, если это не переводимый компонент, и в этом случае просто используйте старый метод
        // это может произойти, если другой плагин удалил событие, используя устаревший метод (*кхе* мифические мобы)
        if (!LevelledMobs.instance.ver.isRunningPaper || !paperListener!!.onPlayerDeathEvent(event))
            nonPaperPlayerDeath(event)
    }

    private fun nonPaperPlayerDeath(event: PlayerDeathEvent) {
        val lmEntity = SpigotUtils.getPlayersKiller(event)

        if (LevelledMobs.instance.placeholderApiIntegration != null) {
            LevelledMobs.instance.placeholderApiIntegration!!.putPlayerOrMobDeath(event.entity, lmEntity, true)
            return
        }

        lmEntity?.free()
    }
}
