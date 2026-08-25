package io.github.arcaneplugins.levelledmobs.result

import java.time.Instant

/**
 * Записывает смерти объектов для использования в функции максимального уничтожения фрагментов.
 *
 * @author stumper66
 * @since 3.4.0
 */
class ChunkKillInfo {
    // временная метка смерти, максимальное время восстановления
    val entityCounts = mutableMapOf<Instant, Int>()

    val entrySet: Set<Map.Entry<Instant, Int>>
        get() = entityCounts.entries

    val isEmpty: Boolean
        get() = entityCounts.isEmpty()

    val count: Int
        get() = entityCounts.size

    override fun toString(): String {
        return entityCounts.toString()
    }
}