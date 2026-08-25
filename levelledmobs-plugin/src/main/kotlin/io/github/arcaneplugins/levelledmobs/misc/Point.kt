package io.github.arcaneplugins.levelledmobs.misc

import org.bukkit.Location

/**
 * Уменьшенная версия класса Location, включающая только имя мира и три целых числа для
 * х, у и z. Находит области применения, в которых дополнительные данные и точность класса Location полностью
 * ненужно.
 *
 * @author lokka30
 * @see Location
 * @since 3.1.2
 */
class Point {
    private var worldName: String? = null
    private var x = 0
    private var y = 0
    private var z = 0

    constructor(location: Location) {
        this.worldName = location.world.name
        this.x = location.blockX
        this.y = location.blockY
        this.z = location.blockZ
    }

    override fun toString(): String {
        return "$worldName, $x, $y, $z"
    }
}