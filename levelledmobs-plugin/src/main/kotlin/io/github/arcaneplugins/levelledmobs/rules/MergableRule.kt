package io.github.arcaneplugins.levelledmobs.rules

/**
 * Предоставляет общий интерфейс для различных правил.
 * которые можно объединить воедино
 *
 * @author stumper66
 * @since 3.12.0
 */
interface MergableRule {
    fun merge(mergableRule: MergableRule?)

    val doMerge: Boolean

    fun cloneItem(): Any
}