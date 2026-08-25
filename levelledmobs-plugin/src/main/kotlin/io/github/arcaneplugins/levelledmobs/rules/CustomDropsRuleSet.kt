package io.github.arcaneplugins.levelledmobs.rules

/**
 * В сочетании с обработкой пользовательских дропов
 *
 * @author stumper66
 * @since 3.0.0
 */
class CustomDropsRuleSet {
    var useDrops = false
    var chunkKillOptions: ChunkKillOptions? = null
    val useDropTableIds = mutableListOf<String>()
}