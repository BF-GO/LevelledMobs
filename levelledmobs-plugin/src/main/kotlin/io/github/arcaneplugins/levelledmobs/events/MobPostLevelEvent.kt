package io.github.arcaneplugins.levelledmobs.events

import io.github.arcaneplugins.levelledmobs.result.AdditionalLevelInformation
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Это событие запускается *после* назначения уровня мобу. Другие плагины могут отменить это событие.
 *
 * @author lokka30
 * @since 2.5.0
 */
@Suppress("unused")
class MobPostLevelEvent(
    val lmEntity: LivingEntityWrapper,
    val levelCause: LevelCause,
    val additionalInformation: MutableSet<AdditionalLevelInformation>?
) : Event(!Bukkit.isPrimaryThread()) {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    /**
     * При назначении уровня мобу, используется следующее перечисление, позволяющее плагинам найти причину
     * моб получает уровень.
     *
     *
     * NORMAL: Порождается естественным путем, яйцом призыва и т. д. CHANGED_LEVEL: Когда существующий уровневый моб
     * изменился его уровень.
     */
    enum class LevelCause {
        NORMAL,
        CHANGED_LEVEL,
        SUMMONED
    }

    val entity: LivingEntity
        get() = lmEntity.livingEntity

    val level: Int
        get() = lmEntity.getMobLevel
}