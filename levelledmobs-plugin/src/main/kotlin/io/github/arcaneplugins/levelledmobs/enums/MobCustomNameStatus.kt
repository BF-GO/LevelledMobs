package io.github.arcaneplugins.levelledmobs.enums

/**
 * Содержит значения, проанализированные из rules.yml.
 *
 * @author stumper66
 * @since 3.0.0
 */
enum class MobCustomNameStatus {
    NOT_SPECIFIED,  // по умолчанию
    NAMETAGGED,  // Чтобы правило работало, мобу необходимо пометить имя.
    NOT_NAMETAGGED,  // Чтобы правило работало, моб не должен быть помечен по имени.
    EITHER // Не имеет значения, какой статус у моба.
}
