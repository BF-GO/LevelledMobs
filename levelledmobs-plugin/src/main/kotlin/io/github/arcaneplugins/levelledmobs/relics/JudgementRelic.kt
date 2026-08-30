package io.github.arcaneplugins.levelledmobs.relics

enum class JudgementRelic(val persistentId: String) {
    VERDICT_SWORD("verdict_sword"),
    EXECUTION_AXE("execution_axe"),
    FINAL_WARNING_BOW("final_warning_bow"),
    FINAL_CLAUSE_MACE("final_clause_mace");

    companion object {
        fun fromPersistentValue(value: String?): JudgementRelic? =
            entries.firstOrNull { it.persistentId.equals(value, ignoreCase = true) }
    }
}

object JudgementRelicLogic {
    fun cooldownReady(lastActivation: Long, now: Long, cooldownMillis: Long): Boolean =
        cooldownMillis <= 0L || lastActivation <= 0L || now - lastActivation >= cooldownMillis

    fun damageWithBonus(baseDamage: Double, bonusDamage: Double): Double =
        baseDamage.coerceAtLeast(0.0) + bonusDamage.coerceAtLeast(0.0)

    fun cleaveDamage(finalDamage: Double, fraction: Double): Double =
        finalDamage.coerceAtLeast(0.0) * fraction.coerceIn(0.0, 1.0)

    fun markApplies(markOwner: String?, attacker: String, markUntil: Long, now: Long): Boolean =
        markOwner == attacker && markUntil >= now

    fun isMaceSmash(fallDistance: Float, minimumFallDistance: Double): Boolean =
        fallDistance.toDouble() >= minimumFallDistance.coerceAtLeast(0.0)
}
