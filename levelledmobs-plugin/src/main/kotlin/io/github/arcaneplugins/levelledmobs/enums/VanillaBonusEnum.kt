package io.github.arcaneplugins.levelledmobs.enums

/**
 * Каждый доступный множитель, который можно использовать в rules.yml.
 *
 * @author stumper66
 * @since 3.7.5
 */
enum class VanillaBonusEnum {
    ARMOR_MODIFIER,  // не требуется: определяется предметом в слоте брони
    ARMOR_TOUGHNESS,  // не требуется: определяется предметом в слоте брони
    ATTACKING_SPEED_BOOST,
    BABY_SPEED_BOOST,
    COVERED_ARMOR_BONUS,
    DRINKING_SPEED_PENALTY,
    FLEEING_SPEED_BOOST,
    HORSE_ARMOR_BONUS,  // не требуется: определяется предметом в слоте брони
    KNOCKBACK_RESISTANCE,  // не требуется: определяется предметом или атрибутом
    LEADER_ZOMBIE_BONUS,
    RANDOM_SPAWN_BONUS,
    RANDOM_ZOMBIE_SPAWN_BONUS,
    SPRINTING_SPEED_BOOST,
    TOOL_MODIFIER,  // не требуется: определяется предметом в руке
    WEAPON_MODIFIER,  // не требуется: определяется предметом в руке
    ZOMBIE_REINFORCE_CALLEE,
    ZOMBIE_REINFORCE_CALLER
}
