package io.github.arcaneplugins.levelledmobs.listeners

import java.util.UUID
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.enums.AttributeNames
import io.github.arcaneplugins.levelledmobs.misc.Cooldown
import io.github.arcaneplugins.levelledmobs.util.MessageUtils.colorizeAll
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Creeper
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

/**
 * This class is used for debugging the plugin. When an entity is punched, a player with permission
 * will receive a bunch of data about the mob.
 *
 * @author lokka30
 * @since 2.4.0
 */
class EntityDamageDebugListener : Listener {
    private val cooldownMap = mutableMapOf<UUID, Cooldown>()

    //This class is used to debug levellable mobs. It simply displays their current attributes, current health and current level.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        // Make sure debug entity damage is enabled
        if (!LevelledMobs.instance.debugManager.damageDebugOutputIsEnabled)
            return

        // Make sure the mob is a LivingEntity and the attacker is a Player
        if (event.entity !is LivingEntity || event.damager !is Player)
            return

        val lmEntity = LivingEntityWrapper.getInstance(event.entity as LivingEntity)

        checkEntity(event.damager as Player, lmEntity)
        lmEntity.free()
    }

    @Suppress("DEPRECATION")
    private fun checkEntity(
        player: Player,
        lmEntity: LivingEntityWrapper
    ) {
        // Make sure the mob is levelled
        if (!lmEntity.isLevelled) return

        // Make sure the player has debug perm
        if (!player.hasPermission("levelledmobs.debug")) return

        // Don't spam the player's chat
        val entityId = lmEntity.livingEntity.entityId.toString()
        if (cooldownMap.containsKey(player.uniqueId)) {
            val cooldown = cooldownMap[player.uniqueId]

            if (cooldown!!.doesCooldownBelongToIdentifier(entityId)) {
                if (!cooldown.hasCooldownExpired(2))
                    return
            }

            cooldownMap.remove(player.uniqueId)
        }

        /* Now send them the debug message! :) */
        send(
            player,
            LocalizedMessages.text(
                "command.levelledmobs.debug.damage-heading",
                mapOf("entity" to lmEntity.typeName), colorize = false
            ),
            false
        )

        // Print non-attributes
        send(player, LocalizedMessages.text(
            "command.levelledmobs.debug.damage-global-values", colorize = false
        ), false)
        send(player, LocalizedMessages.text(
            "command.levelledmobs.debug.damage-level",
            mapOf("level" to lmEntity.getMobLevel), colorize = false
        ), false)
        send(
            player,
            LocalizedMessages.text(
                "command.levelledmobs.debug.damage-health",
                mapOf("health" to Utils.round(lmEntity.livingEntity.health)), colorize = false
            ),
            false
        )
        if (lmEntity.livingEntity.customName != null) {
            send(
                player, LocalizedMessages.text(
                    "command.levelledmobs.debug.damage-nametag",
                    mapOf("nametag" to lmEntity.livingEntity.customName), colorize = false
                ),
                false
            )
        }

        // Print attributes
        player.sendMessage(" ")
        send(player, LocalizedMessages.text(
            "command.levelledmobs.debug.damage-attributes", colorize = false
        ), false)
        for (attributeName in AttributeNames.entries) {
            val attribute = Utils.getAttribute(attributeName) ?: continue
            val attributeInstance = lmEntity.livingEntity.getAttribute(attribute) ?: continue

            if (Utils.round(attributeInstance.value) == 0.0) continue

            val sb = StringBuilder(LocalizedMessages.text(
                "command.levelledmobs.debug.damage-attribute",
                mapOf(
                    "attribute" to attribute.toString().replace("GENERIC_", ""),
                    "value" to Utils.round(attributeInstance.value)
                ), colorize = false
            ))

            var hadItems = false
            for (mod in attributeInstance.modifiers) {
                if (!hadItems)
                    sb.append(" (")
                else
                    sb.append(", ")

                if (mod.operation == AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                    sb.append("* ")
                else
                    sb.append("+ ")

                sb.append(Utils.round(mod.amount, 5))

                hadItems = true
            }

            if (hadItems) {
                sb.append(LocalizedMessages.text(
                    "command.levelledmobs.debug.damage-base", colorize = false
                ))
                val remainingDigitsStr = attributeInstance.baseValue.toString()
                val remainingDigits = remainingDigitsStr.substringAfter('.').length - 1
                if (remainingDigits > 1)
                    sb.append(Utils.round(attributeInstance.baseValue), 3)
                else
                    sb.append(attributeInstance.baseValue)
            }
            send(player, sb.toString(), false)
        }

        if (lmEntity.livingEntity is Creeper) {
            // Print unique values (per-mob)
            player.sendMessage(" ")
            send(player, LocalizedMessages.text(
                "command.levelledmobs.debug.damage-unique-values", colorize = false
            ), false)

            send(player, LocalizedMessages.text(
                "command.levelledmobs.debug.damage-creeper-radius",
                mapOf("radius" to (lmEntity.livingEntity as Creeper).explosionRadius),
                colorize = false
            ), false)
        }

        send(player, LocalizedMessages.text(
            "command.levelledmobs.debug.damage-end", colorize = false
        ), false)

        // Add them to a delay, and remove them after 2 seconds (40 ticks)
        cooldownMap[player.uniqueId] = Cooldown(System.currentTimeMillis(), entityId)
    }

    private fun send(
        player: Player,
        message: String
    ) {
        send(player, message, true)
    }

    private fun send(
        player: Player,
        message: String,
        usePrefix: Boolean
    ) {
        if (usePrefix) {
            player.sendMessage(
                colorizeAll(LevelledMobs.instance.configUtils.prefix + "&7 " + message)
            )
        } else
            player.sendMessage(colorizeAll(message))
    }
}
