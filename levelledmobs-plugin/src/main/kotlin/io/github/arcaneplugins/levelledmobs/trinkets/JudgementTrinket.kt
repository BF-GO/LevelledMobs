package io.github.arcaneplugins.levelledmobs.trinkets

enum class JudgementTrinket(
    val persistentId: String,
    val modifierSuffix: String
) {
    VITALITY_NECKLACE("vitality_necklace", "vitality"),
    ARMOR_BADGE("armor_badge", "armor"),
    EMERGENCY_SOCK("emergency_sock", "speed"),
    COMMON_SENSE_ANCHOR("common_sense_anchor", "knockback"),
    MAJOR_VITALITY("major_vitality", "major_vitality"),
    ABSOLUTE_BULWARK("absolute_bulwark", "absolute_bulwark"),
    DAMAGE_LICENSE("damage_license", "damage");

    companion object {
        fun fromPersistentValue(value: String?): JudgementTrinket? =
            entries.firstOrNull { it.persistentId.equals(value, ignoreCase = true) }
    }
}

object JudgementTrinketLogic {
    fun activeTrinkets(hotbarValues: Iterable<String?>): Set<JudgementTrinket> =
        hotbarValues.mapNotNull(JudgementTrinket::fromPersistentValue).toSet()

    fun signature(values: Map<String, Double>): String =
        values.entries.sortedBy { it.key }
            .joinToString("|") { "${it.key}:${it.value}" }

    fun cooldownReady(lastActivation: Long, now: Long, cooldownMillis: Long): Boolean =
        cooldownMillis <= 0L || lastActivation <= 0L || now - lastActivation >= cooldownMillis

    fun isGrounded(groundedUntil: Long, now: Long): Boolean = groundedUntil > now

    const val ANTI_ELYTRA_ITEM_ID = "airspace_denial_crossbow"
}
