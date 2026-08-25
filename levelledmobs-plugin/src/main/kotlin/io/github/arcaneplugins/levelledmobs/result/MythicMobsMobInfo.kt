package io.github.arcaneplugins.levelledmobs.result

/**
 * Содержит информацию, полученную из внутренних
 * Настройки Mythic Mobs для конкретного моба
 *
 * @author stumper66
 * @since 3.6.0
 */
class MythicMobsMobInfo {
    var preventOtherDrops: Boolean = false
    var preventRandomEquipment: Boolean = false
    var internalName: String? = null
}