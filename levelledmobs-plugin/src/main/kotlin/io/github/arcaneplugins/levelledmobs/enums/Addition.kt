package io.github.arcaneplugins.levelledmobs.enums

/**
 * Содержит атрибуты, к которым можно применять множители.
 *
 * @author lokka30, stumper66
 * @see org.bukkit.attribute.Attribute
 *
 * @since 2.6.0
 */
enum class Addition {
    // Префикс ATTRIBUTE, если это ванильный атрибут Minecraft, например GENERIC_MOVEMENT_SPEED.
    ATTRIBUTE_MOVEMENT_SPEED,
    ATTRIBUTE_ATTACK_DAMAGE,
    ATTRIBUTE_MAX_HEALTH,

    // Префикс CUSTOM, если это пользовательское значение, используемое в прослушивателях.
    CUSTOM_RANGED_ATTACK_DAMAGE,
    CUSTOM_ITEM_DROP,
    CUSTOM_XP_DROP,

    CREEPER_BLAST_DAMAGE,
    ATTRIBUTE_ARMOR_BONUS,
    ATTRIBUTE_ARMOR_TOUGHNESS,
    ATTRIBUTE_ATTACK_KNOCKBACK,
    ATTRIBUTE_FLYING_SPEED,
    ATTRIBUTE_KNOCKBACK_RESISTANCE,
    ATTRIBUTE_HORSE_JUMP_STRENGTH,
    ATTRIBUTE_ZOMBIE_SPAWN_REINFORCEMENTS,
    ATTRIBUTE_FOLLOW_RANGE,
    MOB_SCALE
}
