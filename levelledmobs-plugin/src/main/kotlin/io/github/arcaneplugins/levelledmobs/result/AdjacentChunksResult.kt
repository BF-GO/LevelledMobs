package io.github.arcaneplugins.levelledmobs.result

/**
 * Используется в сочетании с функцией подсчета уничтожений фрагментов.
 *
 * @author stumper66
 * @since 3.4.0
 */
class AdjacentChunksResult {
    var entities: Int = 0
    val chunkKeys = mutableListOf<Long>()
}