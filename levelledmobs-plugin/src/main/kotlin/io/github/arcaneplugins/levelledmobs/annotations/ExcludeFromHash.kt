package io.github.arcaneplugins.levelledmobs.annotations

/**
 * Используется в различных правилах, чтобы исключить их из
 * функция хеширования мобов
 *
 * @author stumper66
 * @since 3.12.0
 */
@Target(AnnotationTarget.FIELD)
annotation class ExcludeFromHash
