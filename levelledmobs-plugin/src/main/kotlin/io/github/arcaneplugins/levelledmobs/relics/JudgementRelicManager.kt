package io.github.arcaneplugins.levelledmobs.relics

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.util.MessageUtils.colorizeAll
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector

/** Handles the four persistent PvE relics awarded by level 999 mobs. */
class JudgementRelicManager : Listener {
    private val main: LevelledMobs
        get() = LevelledMobs.instance

    private val cleaveVictims = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var settings = RuntimeSettings()

    fun load() {
        val root = main.helperSettings.cs.getConfigurationSection("judgement-week-relics")
        settings = RuntimeSettings(
            enabled = root?.getBoolean("enabled", true) ?: true,
            announcement = root?.getString("level-999-death-announcement", DEFAULT_ANNOUNCEMENT)
                ?: DEFAULT_ANNOUNCEMENT,
            swordCooldown = timeMillis(root, "abilities.verdict-sword.cooldown", 8_000L),
            swordBonusDamage = number(root, "abilities.verdict-sword.bonus-damage", 4.0),
            axeCooldown = timeMillis(root, "abilities.execution-axe.cooldown", 10_000L),
            axeDamageFraction = number(root, "abilities.execution-axe.damage-fraction", 0.40),
            axeRadius = number(root, "abilities.execution-axe.radius", 4.0),
            bowCooldown = timeMillis(root, "abilities.final-warning-bow.cooldown", 10_000L),
            bowMarkDuration = timeMillis(root, "abilities.final-warning-bow.mark-duration", 5_000L),
            bowBonusDamage = number(root, "abilities.final-warning-bow.bonus-damage", 6.0),
            maceCooldown = timeMillis(root, "abilities.final-clause-mace.cooldown", 12_000L),
            maceMinimumFallDistance = number(root, "abilities.final-clause-mace.minimum-fall-distance", 1.5),
            maceRadius = number(root, "abilities.final-clause-mace.radius", 5.0),
            maceHorizontalForce = number(root, "abilities.final-clause-mace.horizontal-force", 1.10),
            maceVerticalForce = number(root, "abilities.final-clause-mace.vertical-force", 0.30)
        )
    }

    fun loadListener() {
        HandlerList.unregisterAll(this)
        Bukkit.getPluginManager().registerEvents(this, main)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRelicBowShot(event: EntityShootBowEvent) {
        val current = settings
        if (!current.enabled) return
        val player = event.entity as? Player ?: return
        if (relicFromItem(event.bow) != JudgementRelic.FINAL_WARNING_BOW) return
        val projectile = event.projectile as? Projectile ?: return
        val now = System.currentTimeMillis()
        if (!useCooldown(player, NamespacedKeys.judgementRelicBowCooldown, now, current.bowCooldown)) return

        projectile.persistentDataContainer.set(
            NamespacedKeys.judgementRelicBowProjectileOwner,
            PersistentDataType.STRING,
            player.uniqueId.toString()
        )
        telegraph(player, Particle.END_ROD, Sound.ENTITY_ARROW_SHOOT)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRelicDamage(event: EntityDamageByEntityEvent) {
        val current = settings
        if (!current.enabled || event.finalDamage <= 0.0) return
        val victim = event.entity as? LivingEntity ?: return
        if (victim is Player || victim is ArmorStand) return
        if (cleaveVictims.remove(victim.uniqueId)) return
        val attacker = resolvePlayerDamager(event.damager) ?: return
        val now = System.currentTimeMillis()

        consumeBowMark(event, victim, attacker, now, current)

        val projectile = event.damager as? Projectile
        val projectileOwner = projectile?.persistentDataContainer?.get(
            NamespacedKeys.judgementRelicBowProjectileOwner,
            PersistentDataType.STRING
        )
        if (projectileOwner == attacker.uniqueId.toString()) {
            victim.persistentDataContainer.set(
                NamespacedKeys.judgementRelicBowMarkOwner,
                PersistentDataType.STRING,
                projectileOwner
            )
            victim.persistentDataContainer.set(
                NamespacedKeys.judgementRelicBowMarkUntil,
                PersistentDataType.LONG,
                now + current.bowMarkDuration
            )
            telegraph(victim, Particle.END_ROD, Sound.ENTITY_ARROW_HIT_PLAYER)
            return
        }

        if (event.damager !is Player) return
        when (relicFromItem(attacker.inventory.itemInMainHand)) {
            JudgementRelic.VERDICT_SWORD -> {
                if (useCooldown(attacker, NamespacedKeys.judgementRelicSwordCooldown, now, current.swordCooldown)) {
                    event.damage = JudgementRelicLogic.damageWithBonus(event.damage, current.swordBonusDamage)
                    telegraph(victim, Particle.CRIT, Sound.ENTITY_PLAYER_ATTACK_CRIT)
                }
            }
            JudgementRelic.EXECUTION_AXE -> {
                if (useCooldown(attacker, NamespacedKeys.judgementRelicAxeCooldown, now, current.axeCooldown)) {
                    val cleaveDamage = JudgementRelicLogic.cleaveDamage(
                        event.finalDamage,
                        current.axeDamageFraction
                    )
                    activateCleave(victim, attacker, cleaveDamage, current.axeRadius)
                    telegraph(victim, Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_SWEEP)
                }
            }
            JudgementRelic.FINAL_CLAUSE_MACE -> {
                if (JudgementRelicLogic.isMaceSmash(
                        attacker.fallDistance,
                        current.maceMinimumFallDistance
                    ) && useCooldown(
                        attacker,
                        NamespacedKeys.judgementRelicMaceCooldown,
                        now,
                        current.maceCooldown
                    )
                ) {
                    activateMaceWave(victim, current)
                    telegraph(victim, Particle.CLOUD, Sound.ENTITY_GENERIC_EXPLODE)
                }
            }
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onLevel999Death(event: EntityDeathEvent) {
        val current = settings
        if (!current.enabled) return
        val level = event.entity.persistentDataContainer.get(
            NamespacedKeys.levelKey,
            PersistentDataType.INTEGER
        ) ?: return
        if (level != 999) return
        val killer = event.entity.killer ?: return

        val location = event.entity.location
        event.entity.world.spawnParticle(
            Particle.END_ROD, location.clone().add(0.0, event.entity.height * 0.5, 0.0),
            80, 1.2, 1.0, 1.2, 0.08
        )
        event.entity.world.spawnParticle(
            Particle.REVERSE_PORTAL, location.clone().add(0.0, 0.8, 0.0),
            60, 1.0, 0.8, 1.0, 0.05
        )
        event.entity.world.playSound(
            location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.75f
        )

        if (current.announcement.isBlank()) return
        val message = colorizeAll(current.announcement.replace("%player%", killer.name))
        SchedulerWrapper(Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                SchedulerWrapper(player, Runnable { player.sendMessage(message) }).run()
            }
        }).runGlobal()
    }

    private fun consumeBowMark(
        event: EntityDamageByEntityEvent,
        victim: LivingEntity,
        attacker: Player,
        now: Long,
        current: RuntimeSettings
    ) {
        val pdc = victim.persistentDataContainer
        val owner = pdc.get(NamespacedKeys.judgementRelicBowMarkOwner, PersistentDataType.STRING)
            ?: return
        val until = pdc.get(NamespacedKeys.judgementRelicBowMarkUntil, PersistentDataType.LONG) ?: 0L
        if (until < now) {
            clearBowMark(victim)
            return
        }
        if (!JudgementRelicLogic.markApplies(owner, attacker.uniqueId.toString(), until, now)) return

        event.damage = JudgementRelicLogic.damageWithBonus(event.damage, current.bowBonusDamage)
        clearBowMark(victim)
        telegraph(victim, Particle.CRIT, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE)
    }

    private fun clearBowMark(entity: LivingEntity) {
        entity.persistentDataContainer.remove(NamespacedKeys.judgementRelicBowMarkOwner)
        entity.persistentDataContainer.remove(NamespacedKeys.judgementRelicBowMarkUntil)
    }

    private fun activateCleave(
        primaryVictim: LivingEntity,
        attacker: Player,
        damage: Double,
        radius: Double
    ) {
        if (damage <= 0.0 || radius <= 0.0) return
        nearbyMobTargets(primaryVictim, radius).forEach { target ->
            SchedulerWrapper(target, Runnable {
                if (!target.isValid || target.isDead) return@Runnable
                cleaveVictims.add(target.uniqueId)
                try {
                    if (Bukkit.isOwnedByCurrentRegion(attacker)) target.damage(damage, attacker)
                    else target.damage(damage)
                } finally {
                    cleaveVictims.remove(target.uniqueId)
                }
            }).run()
        }
    }

    private fun activateMaceWave(primaryVictim: LivingEntity, current: RuntimeSettings) {
        val origin = primaryVictim.location.clone()
        nearbyMobTargets(primaryVictim, current.maceRadius).forEach { target ->
            SchedulerWrapper(target, Runnable {
                if (!target.isValid || target.isDead) return@Runnable
                val delta = target.location.toVector().subtract(origin.toVector())
                val horizontal = Vector(delta.x, 0.0, delta.z)
                val direction = if (horizontal.lengthSquared() < 0.0001) Vector(1.0, 0.0, 0.0)
                    else horizontal.normalize()
                target.velocity = direction.multiply(current.maceHorizontalForce)
                    .setY(current.maceVerticalForce)
            }).run()
        }
    }

    private fun nearbyMobTargets(primaryVictim: LivingEntity, radius: Double): List<LivingEntity> {
        if (radius <= 0.0) return emptyList()
        return primaryVictim.world.getNearbyEntities(
            primaryVictim.location,
            radius,
            radius,
            radius
        ).asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it.uniqueId != primaryVictim.uniqueId && it !is Player && it !is ArmorStand }
            .toList()
    }

    private fun relicFromItem(item: ItemStack?): JudgementRelic? {
        val value = item?.itemMeta?.persistentDataContainer?.get(
            NamespacedKeys.judgementRelicId,
            PersistentDataType.STRING
        )
        return JudgementRelic.fromPersistentValue(value)
    }

    private fun resolvePlayerDamager(damager: Entity): Player? = when (damager) {
        is Player -> damager
        is Projectile -> damager.shooter as? Player
        is AreaEffectCloud -> damager.source as? Player
        else -> null
    }

    private fun useCooldown(
        player: Player,
        key: org.bukkit.NamespacedKey,
        now: Long,
        cooldownMillis: Long
    ): Boolean {
        val pdc = player.persistentDataContainer
        val previous = pdc.get(key, PersistentDataType.LONG) ?: 0L
        if (!JudgementRelicLogic.cooldownReady(previous, now, cooldownMillis)) return false
        pdc.set(key, PersistentDataType.LONG, now)
        return true
    }

    private fun telegraph(entity: LivingEntity, particle: Particle, sound: Sound) {
        val location: Location = entity.location.clone().add(0.0, entity.height * 0.6, 0.0)
        entity.world.spawnParticle(particle, location, 12, 0.35, 0.35, 0.35, 0.02)
        entity.world.playSound(entity.location, sound, 0.75f, 1.0f)
    }

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
        val announcement: String = DEFAULT_ANNOUNCEMENT,
        val swordCooldown: Long = 8_000L,
        val swordBonusDamage: Double = 4.0,
        val axeCooldown: Long = 10_000L,
        val axeDamageFraction: Double = 0.40,
        val axeRadius: Double = 4.0,
        val bowCooldown: Long = 10_000L,
        val bowMarkDuration: Long = 5_000L,
        val bowBonusDamage: Double = 6.0,
        val maceCooldown: Long = 12_000L,
        val maceMinimumFallDistance: Double = 1.5,
        val maceRadius: Double = 5.0,
        val maceHorizontalForce: Double = 1.10,
        val maceVerticalForce: Double = 0.30
    )

    companion object {
        private const val DEFAULT_ANNOUNCEMENT =
            "&4&l[СУДНАЯ НЕДЕЛЯ] &f%player% &7уничтожил &4Предвестника Конца 999-го уровня&7!"
    }
}
