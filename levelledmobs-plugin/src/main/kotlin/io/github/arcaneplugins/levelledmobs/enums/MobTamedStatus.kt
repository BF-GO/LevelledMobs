package io.github.arcaneplugins.levelledmobs.enums

/**
 * Содержит значения, проанализированные из rules.yml.
 *
 * @author stumper66
 * @since 3.0.0
 */
enum class MobTamedStatus {
    NOT_SPECIFIED,  // по умолчанию
    TAMED,  // Чтобы правило сработало, моба необходимо приручить.
    NOT_TAMED,  // Чтобы правило работало, моба нельзя приручить.
    EITHER // Не имеет значения, какой у моба прирученный статус.
}