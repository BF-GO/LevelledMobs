package io.github.arcaneplugins.levelledmobs.trinkets

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.enums.AttributeNames
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerResult
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.AttributeModifier
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/** Applies non-stacking hotbar trinkets and the Judgement Week anti-elytra counter. */
class JudgementTrinketManager : Listener {
    private val main: LevelledMobs
        get() = LevelledMobs.instance

    @Volatile
    private var settings = RuntimeSettings()
    private var scanTask: SchedulerResult? = null

    fun load() {
        val root = main.helperSettings.cs.getConfigurationSection("judgement-week-trinkets")
        settings = RuntimeSettings(
            enabled = root?.getBoolean("enabled", true) ?: true,
            scanPeriodTicks = (root?.getLong("hotbar-scan-period-ticks", 10L) ?: 10L)
                .coerceAtLeast(1L),
            vitalityHealth = number(root, "effects.vitality-necklace.max-health", 4.0),
            armorBonus = number(root, "effects.armor-badge.armor", 2.0),
            speedMultiplier = number(root, "effects.emergency-sock.movement-speed", 0.05),
            knockbackResistance = number(
                root,
                "effects.common-sense-anchor.knockback-resistance",
                0.15
            ),
            majorVitalityHealth = number(root, "effects.major-vitality.max-health", 8.0),
            absoluteBulwarkArmor = number(root, "effects.absolute-bulwark.armor", 5.0),
            absoluteBulwarkToughness = number(
                root,
                "effects.absolute-bulwark.armor-toughness",
                2.0
            ),
            damageLicenseMultiplier = number(
                root,
                "effects.damage-license.attack-damage",
                0.15
            ),
            antiElytraCooldown = timeMillis(root, "anti-elytra.cooldown", 15_000L),
            groundingDuration = timeMillis(root, "anti-elytra.grounding-duration", 6_000L),
            rocketLockDuration = timeMillis(root, "anti-elytra.rocket-lock-duration", 6_000L),
            slowFallingDuration = timeMillis(root, "anti-elytra.slow-falling-duration", 6_000L),
            glowingDuration = timeMillis(root, "anti-elytra.glowing-duration", 6_000L)
        )
    }

    fun loadListener() {
        HandlerList.unregisterAll(this)
        scanTask?.cancelTask()
        Bukkit.getPluginManager().registerEvents(this, main)
        val current = settings
        scanTask = SchedulerWrapper(Runnable { scanOnlinePlayers() })
            .runTaskTimerGlobal(1L, current.scanPeriodTicks)
    }

    fun stop() {
        scanTask?.cancelTask()
        scanTask = null
        Bukkit.getOnlinePlayers().forEach { player ->
            if (Bukkit.isOwnedByCurrentRegion(player)) clearPlayer(player)
            else SchedulerWrapper(player, Runnable { clearPlayer(player) }).run()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        SchedulerWrapper(event.player, Runnable { syncPlayer(event.player) }).runDelayed(1L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        clearPlayer(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAntiElytraShot(event: EntityShootBowEvent) {
        val current = settings
        if (!current.enabled) return
        val shooter = event.entity as? Player ?: return
        if (!isAntiElytraItem(event.bow)) return
        val projectile = event.projectile as? Projectile ?: return
        val now = System.currentTimeMillis()
        if (!useCooldown(shooter, now, current.antiElytraCooldown)) return

        projectile.persistentDataContainer.set(
            NamespacedKeys.antiElytraProjectileOwner,
            PersistentDataType.STRING,
            shooter.uniqueId.toString()
        )
        shooter.world.playSound(shooter.location, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAntiElytraHit(event: EntityDamageByEntityEvent) {
        val current = settings
        if (!current.enabled) return
        val target = event.entity as? Player ?: return
        if (!target.isGliding) return
        val projectile = event.damager as? Projectile ?: return
        val owner = projectile.persistentDataContainer.get(
            NamespacedKeys.antiElytraProjectileOwner,
            PersistentDataType.STRING
        ) ?: return
        val shooter = projectile.shooter as? Player ?: return
        if (owner != shooter.uniqueId.toString()) return
        projectile.persistentDataContainer.remove(NamespacedKeys.antiElytraProjectileOwner)

        val now = System.currentTimeMillis()
        target.persistentDataContainer.set(
            NamespacedKeys.antiElytraGroundedUntil,
            PersistentDataType.LONG,
            now + current.groundingDuration
        )
        target.isGliding = false
        target.setCooldown(
            Material.FIREWORK_ROCKET,
            millisToTicks(current.rocketLockDuration)
        )
        target.addPotionEffect(PotionEffect(
            PotionEffectType.SLOW_FALLING,
            millisToTicks(current.slowFallingDuration),
            0,
            false,
            true,
            true
        ))
        target.addPotionEffect(PotionEffect(
            PotionEffectType.GLOWING,
            millisToTicks(current.glowingDuration),
            0,
            false,
            true,
            true
        ))
        target.world.spawnParticle(
            Particle.CLOUD,
            target.location.clone().add(0.0, target.height * 0.5, 0.0),
            24,
            0.6,
            0.6,
            0.6,
            0.04
        )
        target.world.playSound(target.location, Sound.ENTITY_PHANTOM_HURT, 1.0f, 0.7f)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleGlide(event: EntityToggleGlideEvent) {
        val player = event.entity as? Player ?: return
        if (!settings.enabled) {
            player.persistentDataContainer.remove(NamespacedKeys.antiElytraGroundedUntil)
            return
        }
        if (!event.isGliding) return
        val pdc = player.persistentDataContainer
        val until = pdc.get(NamespacedKeys.antiElytraGroundedUntil, PersistentDataType.LONG)
            ?: return
        if (JudgementTrinketLogic.isGrounded(until, System.currentTimeMillis())) {
            event.isCancelled = true
            return
        }
        pdc.remove(NamespacedKeys.antiElytraGroundedUntil)
    }

    private fun scanOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach { player ->
            SchedulerWrapper(player, Runnable { syncPlayer(player) }).run()
        }
    }

    private fun syncPlayer(player: Player) {
        val current = settings
        val active = if (current.enabled) {
            JudgementTrinketLogic.activeTrinkets((0..8).map { slot ->
                trinketValue(player.inventory.getItem(slot))
            })
        } else emptySet()
        val desired = active.flatMap { effectsFor(it, current) }
            .associateBy(TrinketEffect::suffix)
        val signature = JudgementTrinketLogic.signature(
            desired.mapValues { it.value.amount }
        )
        val expectedModifierKeys = desired.keys.mapTo(mutableSetOf(), ::modifierName)
        val actualModifierKeys = currentModifierKeys(player)
        val storedSignature = player.persistentDataContainer.get(
            NamespacedKeys.judgementTrinketsActive,
            PersistentDataType.STRING
        )
        if (storedSignature == signature && actualModifierKeys == expectedModifierKeys) return

        val oldHealth = player.health
        removeTrinketModifiers(player)
        desired.values.forEach { effect -> addModifier(player, effect) }
        if (signature.isEmpty()) {
            player.persistentDataContainer.remove(NamespacedKeys.judgementTrinketsActive)
        } else {
            player.persistentDataContainer.set(
                NamespacedKeys.judgementTrinketsActive,
                PersistentDataType.STRING,
                signature
            )
        }

        if (!player.isDead) {
            val newMax = maxHealth(player)
            player.health = oldHealth.coerceIn(0.01, newMax)
        }
    }

    private fun clearPlayer(player: Player) {
        val oldHealth = player.health
        removeTrinketModifiers(player)
        player.persistentDataContainer.remove(NamespacedKeys.judgementTrinketsActive)
        player.persistentDataContainer.remove(NamespacedKeys.antiElytraGroundedUntil)
        if (!player.isDead) player.health = oldHealth.coerceIn(0.01, maxHealth(player))
    }

    private fun trinketValue(item: ItemStack?): String? =
        item?.itemMeta?.persistentDataContainer?.get(
            NamespacedKeys.judgementTrinketId,
            PersistentDataType.STRING
        )

    private fun isAntiElytraItem(item: ItemStack?): Boolean =
        item?.itemMeta?.persistentDataContainer?.get(
            NamespacedKeys.antiElytraItemId,
            PersistentDataType.STRING
        ) == JudgementTrinketLogic.ANTI_ELYTRA_ITEM_ID

    private fun effectsFor(
        trinket: JudgementTrinket,
        current: RuntimeSettings
    ): List<TrinketEffect> = when (trinket) {
        JudgementTrinket.VITALITY_NECKLACE -> listOf(TrinketEffect(
            trinket.modifierSuffix,
            AttributeNames.MAX_HEALTH,
            current.vitalityHealth
        ))
        JudgementTrinket.ARMOR_BADGE -> listOf(TrinketEffect(
            trinket.modifierSuffix,
            AttributeNames.ARMOR,
            current.armorBonus
        ))
        JudgementTrinket.EMERGENCY_SOCK -> listOf(TrinketEffect(
            trinket.modifierSuffix,
            AttributeNames.MOVEMENT_SPEED,
            current.speedMultiplier,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        ))
        JudgementTrinket.COMMON_SENSE_ANCHOR -> listOf(TrinketEffect(
            trinket.modifierSuffix,
            AttributeNames.KNOCKBACK_RESISTANCE,
            current.knockbackResistance
        ))
        JudgementTrinket.MAJOR_VITALITY -> listOf(TrinketEffect(
            trinket.modifierSuffix,
            AttributeNames.MAX_HEALTH,
            current.majorVitalityHealth
        ))
        JudgementTrinket.ABSOLUTE_BULWARK -> listOf(
            TrinketEffect("major_armor", AttributeNames.ARMOR, current.absoluteBulwarkArmor),
            TrinketEffect(
                "major_toughness",
                AttributeNames.ARMOR_TOUGHNESS,
                current.absoluteBulwarkToughness
            )
        )
        JudgementTrinket.DAMAGE_LICENSE -> listOf(TrinketEffect(
            trinket.modifierSuffix,
            AttributeNames.ATTACK_DAMAGE,
            current.damageLicenseMultiplier,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        ))
    }

    private fun modifierName(suffix: String): String = "jw_trinket_$suffix"

    private fun addModifier(player: Player, effect: TrinketEffect) {
        if (effect.amount == 0.0) return
        val attribute = Utils.getAttribute(effect.attribute) ?: return
        val instance = player.getAttribute(attribute) ?: return
        val keyName = modifierName(effect.suffix)
        val key = NamespacedKey(main, keyName)
        @Suppress("DEPRECATION", "removal")
        val modifier = if (main.ver.useOldEnums) {
            AttributeModifier(keyName, effect.amount, effect.operation)
        } else {
            val anySlot = main.definitions.fieldEquipmentSlotAny!!.get(null)
            main.definitions.ctorAttributeModifier!!.newInstance(
                key,
                effect.amount,
                effect.operation,
                anySlot
            ) as AttributeModifier
        }
        instance.addModifier(modifier)
    }

    private fun removeTrinketModifiers(player: Player) {
        trinketAttributes.forEach { attributeName ->
            val attribute = Utils.getAttribute(attributeName) ?: return@forEach
            val instance = player.getAttribute(attribute) ?: return@forEach
            instance.modifiers.filter { modifierKey(it).startsWith(TRINKET_MODIFIER_PREFIX) }
                .forEach(instance::removeModifier)
        }
    }

    private fun currentModifierKeys(player: Player): Set<String> {
        val result = mutableSetOf<String>()
        trinketAttributes.forEach { attributeName ->
            val attribute = Utils.getAttribute(attributeName) ?: return@forEach
            val instance = player.getAttribute(attribute) ?: return@forEach
            instance.modifiers.mapTo(result) { modifierKey(it) }
        }
        return result.filterTo(mutableSetOf()) { it.startsWith(TRINKET_MODIFIER_PREFIX) }
    }

    @Suppress("DEPRECATION")
    private fun modifierKey(modifier: AttributeModifier): String =
        if (main.ver.useOldEnums) modifier.name else modifier.key.key

    private fun maxHealth(player: Player): Double {
        val attribute = Utils.getAttribute(AttributeNames.MAX_HEALTH) ?: return player.health
        return player.getAttribute(attribute)?.value ?: player.health
    }

    private fun useCooldown(player: Player, now: Long, cooldownMillis: Long): Boolean {
        val pdc = player.persistentDataContainer
        val previous = pdc.get(NamespacedKeys.antiElytraCooldown, PersistentDataType.LONG) ?: 0L
        if (!JudgementTrinketLogic.cooldownReady(previous, now, cooldownMillis)) return false
        pdc.set(NamespacedKeys.antiElytraCooldown, PersistentDataType.LONG, now)
        return true
    }

    private fun millisToTicks(millis: Long): Int =
        ((millis.coerceAtLeast(0L) + 49L) / 50L).toInt().coerceAtLeast(1)

    private fun number(root: ConfigurationSection?, path: String, fallback: Double): Double =
        if (root?.contains(path) == true) root.getDouble(path) else fallback

    private fun timeMillis(root: ConfigurationSection?, path: String, fallback: Long): Long {
        if (root?.contains(path) != true) return fallback
        val raw = root.get(path)
        if (raw is Number) return raw.toLong()
        val text = raw?.toString()?.trim()?.lowercase() ?: return fallback
        return when {
            text.endsWith("ms") -> text.dropLast(2).toLongOrNull() ?: fallback
            text.endsWith("s") -> (text.dropLast(1).toDoubleOrNull()?.times(1_000.0))?.toLong()
                ?: fallback
            text.endsWith("m") -> (text.dropLast(1).toDoubleOrNull()?.times(60_000.0))?.toLong()
                ?: fallback
            else -> text.toLongOrNull() ?: fallback
        }
    }

    private data class RuntimeSettings(
        val enabled: Boolean = true,
        val scanPeriodTicks: Long = 10L,
        val vitalityHealth: Double = 4.0,
        val armorBonus: Double = 2.0,
        val speedMultiplier: Double = 0.05,
        val knockbackResistance: Double = 0.15,
        val majorVitalityHealth: Double = 8.0,
        val absoluteBulwarkArmor: Double = 5.0,
        val absoluteBulwarkToughness: Double = 2.0,
        val damageLicenseMultiplier: Double = 0.15,
        val antiElytraCooldown: Long = 15_000L,
        val groundingDuration: Long = 6_000L,
        val rocketLockDuration: Long = 6_000L,
        val slowFallingDuration: Long = 6_000L,
        val glowingDuration: Long = 6_000L
    )

    companion object {
        private const val TRINKET_MODIFIER_PREFIX = "jw_trinket_"
        private val trinketAttributes = setOf(
            AttributeNames.MAX_HEALTH,
            AttributeNames.ARMOR,
            AttributeNames.ARMOR_TOUGHNESS,
            AttributeNames.MOVEMENT_SPEED,
            AttributeNames.KNOCKBACK_RESISTANCE,
            AttributeNames.ATTACK_DAMAGE
        )
    }

    private data class TrinketEffect(
        val suffix: String,
        val attribute: AttributeNames,
        val amount: Double,
        val operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER
    )
}
