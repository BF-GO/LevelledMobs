package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.result.AdditionalLevelInformation
import io.github.arcaneplugins.levelledmobs.debug.DebugType
import io.github.arcaneplugins.levelledmobs.enums.LevellableState
import io.github.arcaneplugins.levelledmobs.enums.MobTamedStatus
import io.github.arcaneplugins.levelledmobs.managers.MobDataManager
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTameEvent

/**
 * Слушает, когда сущность приручается, поэтому можно применять различные правила.
 *
 * @author stumper66
 * @since 2.4.0
 */
class EntityTameListener : Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private fun onEntityTameEvent(event: EntityTameEvent) {
        val main = LevelledMobs.instance
        val lmEntity = LivingEntityWrapper.getInstance(event.entity)
        MobDataManager.populateAttributeCache(lmEntity)
        val levellableState: LevellableState = main.levelInterface.getLevellableState(lmEntity)

        if (levellableState != LevellableState.ALLOWED) {
            DebugManager.log(DebugType.ENTITY_TAME, lmEntity) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d043", mapOf("value-1" to (levellableState)), false) }
            lmEntity.free()
            return
        }

        if (main.rulesManager.getRuleMobTamedStatus(lmEntity) === MobTamedStatus.NOT_TAMED) {
            DebugManager.log(DebugType.ENTITY_TAME, lmEntity) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d044", colorize = false) }

            // если моб имел уровень, то удалите его
            main.levelInterface.removeLevel(lmEntity)

            DebugManager.log(DebugType.ENTITY_TAME, lmEntity) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d045", colorize = false) }
            lmEntity.free()
            return
        }

        DebugManager.log(DebugType.ENTITY_TAME, lmEntity) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d046", colorize = false) }
        var level = -1
        if (lmEntity.isLevelled)
            level = lmEntity.getMobLevel

        if (level == -1) {
            level = main.levelInterface.generateLevel(lmEntity)
            lmEntity.invalidateCache()
        }

        main.levelInterface.applyLevelToMob(
            lmEntity,
            level,
            isSummoned = false,
            bypassLimits = false,
            additionalLevelInformation = mutableSetOf(AdditionalLevelInformation.FROM_TAME_LISTENER)
        )

        lmEntity.free()
    }
}