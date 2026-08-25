package io.github.arcaneplugins.levelledmobs.events

import io.github.arcaneplugins.levelledmobs.result.AdditionalLevelInformation
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Это событие запускается *до* уровня моба. Обратите внимание, что он не срабатывает, когда моб был
 * создается с помощью `/lm summon`, вместо этого см. SummonedMobPreLevelEvent.
 *
 * @author lokka30
 * @since 2.5.0
 */
class MobPreLevelEvent(
    val entity: LivingEntity,
    var level: Int,
    val levelCause: LevelCause,
    val additionalInformation: MutableSet<AdditionalLevelInformation>?
) : Event(!Bukkit.isPrimaryThread()), Cancellable {
    var showLMNametag = true
    private var cancelled = false

    /**
     * При назначении уровня мобу, используется следующее перечисление, позволяющее плагинам найти причину
     * моб получает уровень.
     * <p>
     * NORMAL: Порождается естественным путем, яйцом призыва и т. д. CHANGED_LEVEL: Когда существующий уровневый моб
     * изменился его уровень.
     */
    enum class LevelCause {
        NORMAL,
        CHANGED_LEVEL
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object{
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun setCancelled(cancelled: Boolean) {
        this.cancelled = cancelled
    }
}