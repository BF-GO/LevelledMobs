package io.github.arcaneplugins.levelledmobs.archetypes

enum class MobArchetype(
    val configKey: String,
    val isBoss: Boolean = false
) {
    BERSERKER("berserker"),
    BASTION("bastion"),
    VAMPIRE("vampire"),
    PLAGUE("plague"),
    THUNDERER("thunderer"),
    DRAGON_TEMPEST("dragon-tempest", true),
    WITHER_NECROMANCER("wither-necromancer", true),
    WARDEN_RESONANCE("warden-resonance", true),
    ELDER_ABYSS("elder-abyss", true);

    companion object {
        val regularEntries = entries.filterNot { it.isBoss }

        fun fromPersistentValue(value: String?): MobArchetype? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

data class ArchetypePassive(
    val outgoingDamage: Double = 1.0,
    val maxHealth: Double = 1.0,
    val movementSpeed: Double = 1.0,
    val flyingSpeed: Double = 1.0,
    val followRange: Double = 1.0,
    val armorBonus: Double = 0.0,
    val armorToughness: Double = 0.0,
    val knockbackResistance: Double = 0.0,
    val attackKnockback: Double = 0.0
)

data class ArchetypeAbility(
    val cooldownMillis: Long = 0L,
    val durationTicks: Int = 0,
    val secondaryDurationTicks: Int = 0,
    val healthThreshold: Double = 0.0,
    val damageMultiplier: Double = 1.0,
    val movementMultiplier: Double = 1.0,
    val incomingDamageMultiplier: Double = 1.0,
    val healingFraction: Double = 0.0,
    val healingCap: Double = 0.0,
    val bonusDamage: Double = 0.0,
    val radius: Double = 0.0,
    val horizontalForce: Double = 0.0,
    val verticalForce: Double = 0.0,
    val primaryEffectLevel: Int = 1,
    val secondaryEffectLevel: Int = 1
)

data class ArchetypeDefinition(
    val display: String,
    val passive: ArchetypePassive,
    val ability: ArchetypeAbility
)

object MobArchetypeLogic {
    private val levelChances = listOf(
        300..449 to 0.10,
        450..599 to 0.15,
        600..749 to 0.25,
        750..899 to 0.40,
        900..998 to 0.60,
        999..999 to 1.0
    )

    fun defaultChanceForLevel(level: Int): Double =
        levelChances.firstOrNull { level in it.first }?.second ?: 0.0

    fun selectRegular(
        level: Int,
        chance: Double,
        chanceRoll: Double,
        selectionRoll: Int
    ): MobArchetype? {
        if (level < 300 || chance <= 0.0 || chanceRoll >= chance.coerceAtMost(1.0)) return null
        val entries = MobArchetype.regularEntries
        return entries[Math.floorMod(selectionRoll, entries.size)]
    }

    fun bossForEntityType(entityType: String): MobArchetype? = when (entityType.uppercase()) {
        "ENDER_DRAGON" -> MobArchetype.DRAGON_TEMPEST
        "WITHER" -> MobArchetype.WITHER_NECROMANCER
        "WARDEN" -> MobArchetype.WARDEN_RESONANCE
        "ELDER_GUARDIAN" -> MobArchetype.ELDER_ABYSS
        else -> null
    }

    fun effectiveArtifactChance(
        baseChance: Float,
        hasArchetype: Boolean,
        multiplier: Double
    ): Float {
        if (!hasArchetype || multiplier <= 1.0) return baseChance
        return (baseChance * multiplier).coerceAtMost(1.0).toFloat()
    }

    fun isArtifactGroup(groupId: String?): Boolean =
        groupId?.startsWith("jw_artifact_") == true ||
            (groupId?.startsWith("jw_boss_") == true && groupId.endsWith("_artifact"))

    fun cooldownReady(lastActivation: Long, now: Long, cooldownMillis: Long): Boolean =
        cooldownMillis <= 0L || lastActivation <= 0L || now - lastActivation >= cooldownMillis

    fun healingAmount(finalDamage: Double, fraction: Double, cap: Double): Double =
        (finalDamage.coerceAtLeast(0.0) * fraction.coerceAtLeast(0.0)).coerceAtMost(cap.coerceAtLeast(0.0))

    fun outgoingDamage(
        baseDamage: Double,
        passiveMultiplier: Double,
        temporaryMultiplier: Double = 1.0,
        bonusDamage: Double = 0.0
    ): Double = baseDamage.coerceAtLeast(0.0) * passiveMultiplier.coerceAtLeast(0.0) *
        temporaryMultiplier.coerceAtLeast(0.0) + bonusDamage.coerceAtLeast(0.0)

    fun incomingDamage(baseDamage: Double, multiplier: Double): Double =
        baseDamage.coerceAtLeast(0.0) * multiplier.coerceAtLeast(0.0)

    fun healthAtSamePercentage(currentHealth: Double, oldMax: Double, newMax: Double): Double {
        if (oldMax <= 0.0 || newMax <= 0.0) return newMax.coerceAtLeast(0.0)
        return (newMax * (currentHealth / oldMax).coerceIn(0.0, 1.0)).coerceIn(0.0, newMax)
    }

    fun crossesHealthThreshold(
        currentHealth: Double,
        finalDamage: Double,
        maxHealth: Double,
        threshold: Double
    ): Boolean {
        if (maxHealth <= 0.0 || threshold <= 0.0) return false
        return (currentHealth - finalDamage).coerceAtLeast(0.0) / maxHealth <= threshold
    }
}
