package io.github.arcaneplugins.levelledmobs.archetypes

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.enums.AttributeNames
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

/** Implements the persistent combat archetypes used by the Judgement Week profile. */
class MobArchetypeManager : Listener {
    private val main: LevelledMobs
        get() = LevelledMobs.instance

    @Volatile
    private var enabled = true
    @Volatile
    private var artifactChanceMultiplier = 1.5
    @Volatile
    private var chances = defaultChances()
    @Volatile
    private var definitions = defaultDefinitions()

    fun load() {
        val root = main.helperSettings.cs.getConfigurationSection("judgement-week-archetypes")
        enabled = root?.getBoolean("enabled", true) ?: true
        artifactChanceMultiplier = root?.getDouble("artifact-chance-multiplier", 1.5) ?: 1.5
        chances = listOf(
            300..449 to value(root, "chances.300-449", 0.10),
            450..599 to value(root, "chances.450-599", 0.15),
            600..749 to value(root, "chances.600-749", 0.25),
            750..899 to value(root, "chances.750-899", 0.40),
            900..998 to value(root, "chances.900-998", 0.60),
            999..999 to value(root, "chances.999", 1.0)
        )
        definitions = buildDefinitions(root)
    }

    fun loadListener() {
        HandlerList.unregisterAll(this)
        Bukkit.getPluginManager().registerEvents(this, main)
    }

    fun ensureAssignedAndApplied(lmEntity: LivingEntityWrapper) {
        val entity = lmEntity.livingEntity
        val level = lmEntity.getMobLevel
        if (!enabled) {
            removeArchetypeModifiers(entity)
            return
        }
        if (level < 300) {
            clear(entity)
            return
        }

        lmEntity.buildCacheIfNeeded()
        if (lmEntity.isMobOfExternalType) {
            clear(entity)
            return
        }

        val pdc = entity.persistentDataContainer
        var archetype = getArchetype(entity)
        val wasChecked = pdc.has(NamespacedKeys.mobArchetypeChecked, PersistentDataType.INTEGER)
        var newlyAssigned = false
        val fixedBossArchetype = MobArchetypeLogic.bossForEntityType(entity.type.name)

        if (fixedBossArchetype != null && archetype != fixedBossArchetype) {
            archetype = fixedBossArchetype
            pdc.set(NamespacedKeys.mobArchetypeChecked, PersistentDataType.INTEGER, 1)
            pdc.set(NamespacedKeys.mobArchetype, PersistentDataType.STRING, archetype.name)
            newlyAssigned = true
        } else if (archetype == null && !wasChecked) {
            archetype = MobArchetypeLogic.selectRegular(
                level,
                chanceForLevel(level),
                ThreadLocalRandom.current().nextDouble(),
                ThreadLocalRandom.current().nextInt(MobArchetype.regularEntries.size)
            )
            pdc.set(NamespacedKeys.mobArchetypeChecked, PersistentDataType.INTEGER, 1)
            if (archetype != null)
                pdc.set(NamespacedKeys.mobArchetype, PersistentDataType.STRING, archetype.name)
            newlyAssigned = archetype != null
        }

        if (archetype == null) return
        applyPermanentModifiers(entity, archetype, preserveHealthRatio = !newlyAssigned)
        restoreOrExpireRage(entity, archetype)
    }

    fun clear(entity: LivingEntity) {
        removeArchetypeModifiers(entity)
        val pdc = entity.persistentDataContainer
        for (key in stateKeys) pdc.remove(key)
    }

    fun getArchetype(entity: LivingEntity): MobArchetype? =
        MobArchetype.fromPersistentValue(
            entity.persistentDataContainer.get(NamespacedKeys.mobArchetype, PersistentDataType.STRING)
        )

    fun getDisplay(entity: LivingEntity): String {
        if (!enabled) return ""
        val archetype = getArchetype(entity) ?: return ""
        val display = definitions[archetype]?.display.orEmpty()
        return if (display.isEmpty()) "" else "$display "
    }

    fun getArtifactChance(entity: LivingEntity, groupId: String?, baseChance: Float): Float {
        if (!enabled || !MobArchetypeLogic.isArtifactGroup(groupId)) return baseChance
        return MobArchetypeLogic.effectiveArtifactChance(
            baseChance,
            getArchetype(entity) != null,
            artifactChanceMultiplier
        )
    }

    fun reapplyLoadedEntities() {
        Bukkit.getWorlds().forEach { world ->
            world.loadedChunks.forEach { chunk ->
                val scheduler = SchedulerWrapper(Runnable {
                    if (!chunk.isLoaded) return@Runnable
                    chunk.entities.filterIsInstance<LivingEntity>().forEach entityLoop@ { entity ->
                        if (!entity.persistentDataContainer.has(NamespacedKeys.levelKey, PersistentDataType.INTEGER))
                            return@entityLoop
                        SchedulerWrapper(entity, Runnable {
                            val wrapper = LivingEntityWrapper.getInstance(entity)
                            try {
                                ensureAssignedAndApplied(wrapper)
                                main.levelManager.updateNametagWithDelay(wrapper)
                            } finally {
                                wrapper.free()
                            }
                        }).run()
                    }
                })
                scheduler.locationForRegionScheduler = Location(
                    world, chunk.x * 16.0 + 8.0, world.minHeight.toDouble(), chunk.z * 16.0 + 8.0
                )
                scheduler.run()
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerCombat(event: EntityDamageByEntityEvent) {
        val playerAttacker = resolveLivingDamager(event.damager) as? Player
        val mobVictim = event.entity as? LivingEntity
        if (playerAttacker != null && mobVictim != null && mobVictim !is Player)
            handleIncomingPlayerDamage(event, mobVictim)

        val mobAttacker = resolveLivingDamager(event.damager)
        val playerVictim = event.entity as? Player
        if (mobAttacker != null && mobAttacker !is Player && playerVictim != null)
            handleOutgoingPlayerDamage(event, mobAttacker, playerVictim)
    }

    private fun handleIncomingPlayerDamage(event: EntityDamageByEntityEvent, mob: LivingEntity) {
        if (!isEligibleLevelledMob(mob)) return
        val archetype = getArchetype(mob) ?: return
        val definition = definitions[archetype] ?: return
        val now = System.currentTimeMillis()

        if (archetype == MobArchetype.BASTION && useCooldown(mob, now, definition.ability.cooldownMillis)) {
            event.damage = MobArchetypeLogic.incomingDamage(
                event.damage, definition.ability.incomingDamageMultiplier
            )
            telegraph(mob, Particle.CLOUD, Sound.ITEM_SHIELD_BLOCK)
        }

        val projectedDamage = event.finalDamage
        if (archetype == MobArchetype.BERSERKER &&
            !mob.persistentDataContainer.has(NamespacedKeys.mobArchetypeRageUsed, PersistentDataType.INTEGER) &&
            MobArchetypeLogic.crossesHealthThreshold(
                mob.health, projectedDamage, maxHealth(mob), definition.ability.healthThreshold
            )
        ) {
            activateRage(mob, definition)
        }

        if (archetype == MobArchetype.WITHER_NECROMANCER &&
            !mob.persistentDataContainer.has(NamespacedKeys.mobArchetypeBossPhaseUsed, PersistentDataType.INTEGER) &&
            MobArchetypeLogic.crossesHealthThreshold(
                mob.health, projectedDamage, maxHealth(mob), definition.ability.healthThreshold
            )
        ) {
            mob.persistentDataContainer.set(
                NamespacedKeys.mobArchetypeBossPhaseUsed, PersistentDataType.INTEGER, 1
            )
            applyToNearbyPlayers(mob, definition.ability.radius) { player ->
                player.addPotionEffect(PotionEffect(
                    PotionEffectType.WITHER, definition.ability.durationTicks,
                    definition.ability.primaryEffectLevel.coerceAtLeast(1) - 1, false, true, true
                ))
            }
            telegraph(mob, Particle.SOUL, Sound.ENTITY_WITHER_AMBIENT)
        }
    }

    private fun handleOutgoingPlayerDamage(
        event: EntityDamageByEntityEvent,
        mob: LivingEntity,
        player: Player
    ) {
        if (!isEligibleLevelledMob(mob)) return
        val archetype = getArchetype(mob) ?: return
        val definition = definitions[archetype] ?: return
        val now = System.currentTimeMillis()

        event.damage = MobArchetypeLogic.outgoingDamage(
            event.damage,
            definition.passive.outgoingDamage,
            if (archetype == MobArchetype.BERSERKER && isRaging(mob, now))
                definition.ability.damageMultiplier else 1.0
        )

        when (archetype) {
            MobArchetype.VAMPIRE, MobArchetype.WITHER_NECROMANCER -> {
                if (useCooldown(mob, now, definition.ability.cooldownMillis)) {
                    heal(mob, MobArchetypeLogic.healingAmount(
                        event.finalDamage, definition.ability.healingFraction, definition.ability.healingCap
                    ))
                    telegraph(mob, Particle.HEART, Sound.ENTITY_PLAYER_BREATH)
                }
            }
            MobArchetype.PLAGUE -> {
                if (useCooldown(mob, now, definition.ability.cooldownMillis)) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.POISON, definition.ability.durationTicks,
                        definition.ability.primaryEffectLevel.coerceAtLeast(1) - 1, false, true, true
                    ))
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.WEAKNESS, definition.ability.secondaryDurationTicks,
                        definition.ability.secondaryEffectLevel.coerceAtLeast(1) - 1, false, true, true
                    ))
                    telegraph(mob, Particle.WITCH, Sound.ENTITY_WITCH_CELEBRATE)
                }
            }
            MobArchetype.THUNDERER -> {
                if (useCooldown(mob, now, definition.ability.cooldownMillis)) {
                    event.damage += definition.ability.bonusDamage
                    mob.world.strikeLightningEffect(player.location)
                    telegraph(mob, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER)
                }
            }
            MobArchetype.DRAGON_TEMPEST -> {
                if (useCooldown(mob, now, definition.ability.cooldownMillis)) {
                    knockBackNearbyPlayers(mob, definition.ability)
                    telegraph(mob, Particle.CLOUD, Sound.ENTITY_ENDER_DRAGON_FLAP)
                }
            }
            MobArchetype.WARDEN_RESONANCE -> {
                if (useCooldown(mob, now, definition.ability.cooldownMillis)) {
                    event.damage += definition.ability.bonusDamage
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.DARKNESS, definition.ability.durationTicks,
                        definition.ability.primaryEffectLevel.coerceAtLeast(1) - 1, false, true, true
                    ))
                    telegraph(mob, Particle.SONIC_BOOM, Sound.ENTITY_WARDEN_SONIC_BOOM)
                }
            }
            MobArchetype.ELDER_ABYSS -> {
                if (useCooldown(mob, now, definition.ability.cooldownMillis)) {
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.SLOWNESS, definition.ability.durationTicks,
                        definition.ability.primaryEffectLevel.coerceAtLeast(1) - 1, false, true, true
                    ))
                    player.addPotionEffect(PotionEffect(
                        PotionEffectType.MINING_FATIGUE, definition.ability.secondaryDurationTicks,
                        definition.ability.secondaryEffectLevel.coerceAtLeast(1) - 1, false, true, true
                    ))
                    telegraph(mob, Particle.BUBBLE_POP, Sound.ENTITY_ELDER_GUARDIAN_CURSE)
                }
            }
            else -> Unit
        }
    }

    private fun activateRage(mob: LivingEntity, definition: ArchetypeDefinition) {
        val pdc = mob.persistentDataContainer
        val durationMillis = definition.ability.durationTicks * 50L
        val rageUntil = System.currentTimeMillis() + durationMillis
        pdc.set(NamespacedKeys.mobArchetypeRageUsed, PersistentDataType.INTEGER, 1)
        pdc.set(NamespacedKeys.mobArchetypeRageUntil, PersistentDataType.LONG, rageUntil)
        addModifier(
            mob,
            AttributeNames.MOVEMENT_SPEED,
            "jw_archetype_rage_speed",
            definition.ability.movementMultiplier - 1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        )
        telegraph(mob, Particle.ANGRY_VILLAGER, Sound.ENTITY_RAVAGER_ROAR)
        SchedulerWrapper(mob, Runnable { expireRageIfNeeded(mob) })
            .runDelayed(definition.ability.durationTicks.toLong())
    }

    private fun restoreOrExpireRage(mob: LivingEntity, archetype: MobArchetype) {
        if (archetype != MobArchetype.BERSERKER) return
        val until = mob.persistentDataContainer.get(
            NamespacedKeys.mobArchetypeRageUntil, PersistentDataType.LONG
        ) ?: return
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            expireRageIfNeeded(mob)
            return
        }
        val definition = definitions[archetype] ?: return
        addModifier(
            mob,
            AttributeNames.MOVEMENT_SPEED,
            "jw_archetype_rage_speed",
            definition.ability.movementMultiplier - 1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        )
        SchedulerWrapper(mob, Runnable { expireRageIfNeeded(mob) })
            .runDelayed(((remaining + 49L) / 50L).coerceAtLeast(1L))
    }

    private fun expireRageIfNeeded(mob: LivingEntity) {
        val pdc = mob.persistentDataContainer
        val until = pdc.get(NamespacedKeys.mobArchetypeRageUntil, PersistentDataType.LONG) ?: return
        if (until > System.currentTimeMillis()) return
        pdc.remove(NamespacedKeys.mobArchetypeRageUntil)
        removeModifier(mob, AttributeNames.MOVEMENT_SPEED, "jw_archetype_rage_speed")
    }

    private fun isRaging(mob: LivingEntity, now: Long): Boolean {
        val until = mob.persistentDataContainer.get(
            NamespacedKeys.mobArchetypeRageUntil, PersistentDataType.LONG
        ) ?: return false
        if (until > now) return true
        expireRageIfNeeded(mob)
        return false
    }

    private fun useCooldown(mob: LivingEntity, now: Long, cooldownMillis: Long): Boolean {
        val pdc = mob.persistentDataContainer
        val previous = pdc.get(NamespacedKeys.mobArchetypeCooldown, PersistentDataType.LONG) ?: 0L
        if (!MobArchetypeLogic.cooldownReady(previous, now, cooldownMillis)) return false
        pdc.set(NamespacedKeys.mobArchetypeCooldown, PersistentDataType.LONG, now)
        return true
    }

    private fun applyPermanentModifiers(
        mob: LivingEntity,
        archetype: MobArchetype,
        preserveHealthRatio: Boolean
    ) {
        val definition = definitions[archetype] ?: return
        val oldMax = maxHealth(mob)
        val oldHealth = mob.health
        removeArchetypeModifiers(mob)

        addMultiplier(mob, AttributeNames.MAX_HEALTH, "health", definition.passive.maxHealth)
        addMultiplier(mob, AttributeNames.MOVEMENT_SPEED, "speed", definition.passive.movementSpeed)
        addMultiplier(mob, AttributeNames.FLYING_SPEED, "flight", definition.passive.flyingSpeed)
        addMultiplier(mob, AttributeNames.FOLLOW_RANGE, "follow", definition.passive.followRange)
        addNumber(mob, AttributeNames.ARMOR, "armor", definition.passive.armorBonus)
        addNumber(mob, AttributeNames.ARMOR_TOUGHNESS, "toughness", definition.passive.armorToughness)
        addNumber(mob, AttributeNames.KNOCKBACK_RESISTANCE, "resistance", definition.passive.knockbackResistance)
        addNumber(mob, AttributeNames.ATTACK_KNOCKBACK, "attack_knockback", definition.passive.attackKnockback)

        val newMax = maxHealth(mob)
        mob.health = if (preserveHealthRatio)
            MobArchetypeLogic.healthAtSamePercentage(oldHealth, oldMax, newMax).coerceAtLeast(0.01)
        else newMax
    }

    private fun addMultiplier(
        entity: LivingEntity,
        attribute: AttributeNames,
        suffix: String,
        multiplier: Double
    ) {
        if (multiplier == 1.0) return
        addModifier(
            entity, attribute, "jw_archetype_$suffix", multiplier - 1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        )
    }

    private fun addNumber(entity: LivingEntity, attribute: AttributeNames, suffix: String, amount: Double) {
        if (amount == 0.0) return
        addModifier(
            entity, attribute, "jw_archetype_$suffix", amount,
            AttributeModifier.Operation.ADD_NUMBER
        )
    }

    private fun addModifier(
        entity: LivingEntity,
        attributeName: AttributeNames,
        keyName: String,
        amount: Double,
        operation: AttributeModifier.Operation
    ) {
        val attribute = Utils.getAttribute(attributeName) ?: return
        val instance = entity.getAttribute(attribute) ?: return
        removeModifier(instance, keyName)
        val key = NamespacedKey(main, keyName)
        @Suppress("DEPRECATION", "removal")
        val modifier = if (main.ver.useOldEnums) {
            AttributeModifier(keyName, amount, operation)
        } else {
            val anySlot = main.definitions.fieldEquipmentSlotAny!!.get(null)
            main.definitions.ctorAttributeModifier!!.newInstance(key, amount, operation, anySlot) as AttributeModifier
        }
        instance.addModifier(modifier)
    }

    private fun removeArchetypeModifiers(entity: LivingEntity) {
        for (attributeName in AttributeNames.entries) {
            val attribute = Utils.getAttribute(attributeName) ?: continue
            val instance = entity.getAttribute(attribute) ?: continue
            instance.modifiers.filter { modifierKey(it).startsWith("jw_archetype_") }
                .forEach(instance::removeModifier)
        }
    }

    private fun removeModifier(entity: LivingEntity, attributeName: AttributeNames, keyName: String) {
        val attribute = Utils.getAttribute(attributeName) ?: return
        val instance = entity.getAttribute(attribute) ?: return
        removeModifier(instance, keyName)
    }

    private fun removeModifier(instance: org.bukkit.attribute.AttributeInstance, keyName: String) {
        instance.modifiers.filter { modifierKey(it) == keyName }.forEach(instance::removeModifier)
    }

    @Suppress("DEPRECATION")
    private fun modifierKey(modifier: AttributeModifier): String =
        if (main.ver.useOldEnums) modifier.name else modifier.key.key

    private fun isEligibleLevelledMob(entity: LivingEntity): Boolean {
        if (!enabled) return false
        val pdc = entity.persistentDataContainer
        return (pdc.get(NamespacedKeys.levelKey, PersistentDataType.INTEGER) ?: -1) >= 300
    }

    private fun resolveLivingDamager(damager: Entity): LivingEntity? = when (damager) {
        is LivingEntity -> damager
        is Projectile -> damager.shooter as? LivingEntity
        is AreaEffectCloud -> damager.source as? LivingEntity
        else -> null
    }

    private fun heal(entity: LivingEntity, amount: Double) {
        if (amount <= 0.0 || entity.isDead) return
        entity.health = (entity.health + amount).coerceAtMost(maxHealth(entity))
    }

    private fun maxHealth(entity: LivingEntity): Double {
        val attribute: Attribute = Utils.getAttribute(AttributeNames.MAX_HEALTH) ?: return entity.health
        return entity.getAttribute(attribute)?.value ?: entity.health
    }

    private fun applyToNearbyPlayers(mob: LivingEntity, radius: Double, action: (Player) -> Unit) {
        val players = mob.world.getNearbyPlayers(mob.location, radius)
        for (player in players) {
            SchedulerWrapper(player, Runnable {
                if (player.isValid && !player.isDead) action(player)
            }).run()
        }
    }

    private fun knockBackNearbyPlayers(mob: LivingEntity, ability: ArchetypeAbility) {
        val origin = mob.location
        applyToNearbyPlayers(mob, ability.radius) { player ->
            val delta = player.location.toVector().subtract(origin.toVector())
            val horizontal = Vector(delta.x, 0.0, delta.z)
            val direction = if (horizontal.lengthSquared() < 0.0001) Vector(1.0, 0.0, 0.0)
                else horizontal.normalize()
            player.velocity = direction.multiply(ability.horizontalForce).setY(ability.verticalForce)
        }
    }

    private fun telegraph(entity: LivingEntity, particle: Particle, sound: Sound) {
        entity.world.spawnParticle(particle, entity.location.add(0.0, entity.height * 0.6, 0.0), 12, 0.35, 0.35, 0.35, 0.02)
        entity.world.playSound(entity.location, sound, 0.7f, 1.0f)
    }

    private fun chanceForLevel(level: Int): Double =
        chances.firstOrNull { level in it.first }?.second ?: 0.0

    private fun buildDefinitions(root: ConfigurationSection?): Map<MobArchetype, ArchetypeDefinition> {
        val defaults = defaultDefinitions()
        return MobArchetype.entries.associateWith { archetype ->
            val old = defaults.getValue(archetype)
            val category = if (archetype.isBoss) "bosses" else "classes"
            val path = "$category.${archetype.configKey}"
            ArchetypeDefinition(
                display = root?.getString("$path.display", old.display) ?: old.display,
                passive = ArchetypePassive(
                    outgoingDamage = value(root, "$path.passive.outgoing-damage", old.passive.outgoingDamage),
                    maxHealth = value(root, "$path.passive.max-health", old.passive.maxHealth),
                    movementSpeed = value(root, "$path.passive.movement-speed", old.passive.movementSpeed),
                    flyingSpeed = value(root, "$path.passive.flying-speed", old.passive.flyingSpeed),
                    followRange = value(root, "$path.passive.follow-range", old.passive.followRange),
                    armorBonus = value(root, "$path.passive.armor-bonus", old.passive.armorBonus),
                    armorToughness = value(root, "$path.passive.armor-toughness", old.passive.armorToughness),
                    knockbackResistance = value(root, "$path.passive.knockback-resistance", old.passive.knockbackResistance),
                    attackKnockback = value(root, "$path.passive.attack-knockback", old.passive.attackKnockback)
                ),
                ability = old.ability.copy(
                    cooldownMillis = timeMillis(root, "$path.ability.cooldown", old.ability.cooldownMillis),
                    durationTicks = durationTicks(root, primaryDurationKey(archetype), path, old.ability.durationTicks),
                    secondaryDurationTicks = durationTicks(root, secondaryDurationKey(archetype), path, old.ability.secondaryDurationTicks),
                    healthThreshold = value(root, "$path.ability.health-threshold", old.ability.healthThreshold),
                    damageMultiplier = value(root, "$path.ability.damage-multiplier", old.ability.damageMultiplier),
                    movementMultiplier = value(root, "$path.ability.movement-speed", old.ability.movementMultiplier),
                    incomingDamageMultiplier = value(root, "$path.ability.incoming-damage", old.ability.incomingDamageMultiplier),
                    healingFraction = value(root, "$path.ability.healing-fraction", old.ability.healingFraction),
                    healingCap = value(root, "$path.ability.healing-cap", old.ability.healingCap),
                    bonusDamage = value(root, "$path.ability.bonus-damage", old.ability.bonusDamage),
                    radius = value(root, "$path.ability.${if (archetype == MobArchetype.WITHER_NECROMANCER) "phase-radius" else "radius"}", old.ability.radius),
                    horizontalForce = value(root, "$path.ability.horizontal-force", old.ability.horizontalForce),
                    verticalForce = value(root, "$path.ability.vertical-force", old.ability.verticalForce),
                    primaryEffectLevel = effectLevel(
                        root, primaryEffectLevelKey(archetype), path, old.ability.primaryEffectLevel
                    ),
                    secondaryEffectLevel = effectLevel(
                        root, secondaryEffectLevelKey(archetype), path, old.ability.secondaryEffectLevel
                    )
                )
            )
        }
    }

    private fun primaryDurationKey(archetype: MobArchetype): String? = when (archetype) {
        MobArchetype.BERSERKER -> "duration"
        MobArchetype.PLAGUE -> "poison-duration"
        MobArchetype.WITHER_NECROMANCER -> "wither-duration"
        MobArchetype.WARDEN_RESONANCE -> "darkness-duration"
        MobArchetype.ELDER_ABYSS -> "slowness-duration"
        else -> null
    }

    private fun secondaryDurationKey(archetype: MobArchetype): String? = when (archetype) {
        MobArchetype.PLAGUE -> "weakness-duration"
        MobArchetype.ELDER_ABYSS -> "fatigue-duration"
        else -> null
    }

    private fun primaryEffectLevelKey(archetype: MobArchetype): String? = when (archetype) {
        MobArchetype.PLAGUE -> "poison-level"
        MobArchetype.WITHER_NECROMANCER -> "wither-level"
        MobArchetype.WARDEN_RESONANCE -> "darkness-level"
        MobArchetype.ELDER_ABYSS -> "slowness-level"
        else -> null
    }

    private fun secondaryEffectLevelKey(archetype: MobArchetype): String? = when (archetype) {
        MobArchetype.PLAGUE -> "weakness-level"
        MobArchetype.ELDER_ABYSS -> "fatigue-level"
        else -> null
    }

    private fun effectLevel(
        root: ConfigurationSection?, key: String?, path: String, fallback: Int
    ): Int = if (key == null || root?.contains("$path.ability.$key") != true) fallback
        else root.getInt("$path.ability.$key", fallback).coerceAtLeast(1)

    private fun durationTicks(
        root: ConfigurationSection?, key: String?, path: String, fallback: Int
    ): Int {
        if (key == null) return fallback
        return (timeMillis(root, "$path.ability.$key", fallback * 50L) / 50L).toInt().coerceAtLeast(1)
    }

    private fun timeMillis(root: ConfigurationSection?, path: String, fallback: Long): Long {
        if (root?.contains(path) != true) return fallback
        val raw = root.get(path)
        if (raw is Number) return raw.toLong()
        val text = raw?.toString()?.trim()?.lowercase() ?: return fallback
        return when {
            text.endsWith("ms") -> text.dropLast(2).toLongOrNull() ?: fallback
            text.endsWith("s") -> (text.dropLast(1).toDoubleOrNull()?.times(1000.0))?.toLong() ?: fallback
            text.endsWith("m") -> (text.dropLast(1).toDoubleOrNull()?.times(60000.0))?.toLong() ?: fallback
            else -> text.toLongOrNull() ?: fallback
        }
    }

    private fun value(root: ConfigurationSection?, path: String, fallback: Double): Double =
        if (root?.contains(path) == true) root.getDouble(path) else fallback

    companion object {
        private fun defaultChances() = listOf(
            300..449 to 0.10, 450..599 to 0.15, 600..749 to 0.25,
            750..899 to 0.40, 900..998 to 0.60, 999..999 to 1.0
        )

        private val stateKeys
            get() = listOf(
                NamespacedKeys.mobArchetype,
                NamespacedKeys.mobArchetypeChecked,
                NamespacedKeys.mobArchetypeCooldown,
                NamespacedKeys.mobArchetypeRageUsed,
                NamespacedKeys.mobArchetypeRageUntil,
                NamespacedKeys.mobArchetypeBossPhaseUsed
            )

        private fun defaultDefinitions(): Map<MobArchetype, ArchetypeDefinition> = mapOf(
            MobArchetype.BERSERKER to ArchetypeDefinition(
                "&c[Берсерк]", ArchetypePassive(outgoingDamage = 1.25, movementSpeed = 1.15),
                ArchetypeAbility(durationTicks = 160, healthThreshold = 0.40, damageMultiplier = 1.25, movementMultiplier = 1.20)
            ),
            MobArchetype.BASTION to ArchetypeDefinition(
                "&7[Бастион]", ArchetypePassive(maxHealth = 1.35, movementSpeed = 0.90, armorBonus = 6.0, armorToughness = 3.0, knockbackResistance = 0.35),
                ArchetypeAbility(cooldownMillis = 8000L, incomingDamageMultiplier = 0.50)
            ),
            MobArchetype.VAMPIRE to ArchetypeDefinition(
                "&4[Вампир]", ArchetypePassive(outgoingDamage = 1.10, movementSpeed = 1.10),
                ArchetypeAbility(cooldownMillis = 2000L, healingFraction = 0.30, healingCap = 8.0)
            ),
            MobArchetype.PLAGUE to ArchetypeDefinition(
                "&2[Чумной]", ArchetypePassive(maxHealth = 1.20, followRange = 1.30),
                ArchetypeAbility(cooldownMillis = 7000L, durationTicks = 80, secondaryDurationTicks = 60, primaryEffectLevel = 2)
            ),
            MobArchetype.THUNDERER to ArchetypeDefinition(
                "&b[Громовержец]", ArchetypePassive(outgoingDamage = 1.15, attackKnockback = 0.35),
                ArchetypeAbility(cooldownMillis = 8000L, bonusDamage = 4.0)
            ),
            MobArchetype.DRAGON_TEMPEST to ArchetypeDefinition(
                "&5[Повелитель Бури]", ArchetypePassive(outgoingDamage = 1.15, flyingSpeed = 1.15),
                ArchetypeAbility(cooldownMillis = 12000L, radius = 8.0, horizontalForce = 1.20, verticalForce = 0.35)
            ),
            MobArchetype.WITHER_NECROMANCER to ArchetypeDefinition(
                "&8[Некромант]", ArchetypePassive(outgoingDamage = 1.10, maxHealth = 1.15),
                ArchetypeAbility(cooldownMillis = 2000L, durationTicks = 100, healthThreshold = 0.50, healingFraction = 0.20, healingCap = 10.0, radius = 10.0, primaryEffectLevel = 2)
            ),
            MobArchetype.WARDEN_RESONANCE to ArchetypeDefinition(
                "&1[Абсолютный Резонанс]", ArchetypePassive(maxHealth = 1.15, knockbackResistance = 0.40),
                ArchetypeAbility(cooldownMillis = 10000L, durationTicks = 80, bonusDamage = 6.0)
            ),
            MobArchetype.ELDER_ABYSS to ArchetypeDefinition(
                "&3[Владыка Глубин]", ArchetypePassive(maxHealth = 1.20, armorBonus = 6.0),
                ArchetypeAbility(cooldownMillis = 8000L, durationTicks = 100, secondaryDurationTicks = 160, primaryEffectLevel = 3, secondaryEffectLevel = 2)
            )
        )
    }
}
