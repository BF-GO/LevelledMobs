package io.github.arcaneplugins.levelledmobs.misc

/**
 * Сохраняет уровень и имя моба, когда моба убивают, поэтому
 * информация может быть записана
 *
 * @author stumper66
 * @since 3.2.1
 */
class LastMobKilledInfo {
    var entityLevel: Int? = null
    var entityName: String? = null
}