package io.github.arcaneplugins.levelledmobs.enums

/**
 * Содержит результат проверки возможности назначить мобу уровень и причину отказа.
 * Уровень можно назначить, если получено состояние LevellableState.ALLOWED.
 *
 * @author lokka30
 * @since 2.4.0
 */
enum class LevellableState {
    /**
     * Сущности разрешено назначить уровень. В перечислении должна быть только одна константа ALLOWED.
     */
    ALLOWED,

    /**
     * плагин принудительно заблокировал тип объекта, например PLAYER или ARMOR STAND, которые не предназначены
     * быть уровневым мобом.
     */
    DENIED_FORCE_BLOCKED_ENTITY_TYPE,

    /**
     * settings.yml настроен так, чтобы блокировать повышение уровня мобов такого типа.
     */
    DENIED_CONFIGURATION_BLOCKED_ENTITY_TYPE,

    /**
     * Было настроено правило, блокирующее повышение уровня мобов с тегами имен.
     */
    DENIED_CONFIGURATION_CONDITION_NAMETAGGED,

    /**
     * Если к мобу не применяются никакие правила в списке правил, то ему будет отказано.
     */
    DENIED_NO_APPLICABLE_RULES,

    /**
     * Если причина неприменима, используйте это. Если вы верите, пожалуйста, свяжитесь с ведущим разработчиком
     * придется прибегнуть к этому.
     */
    DENIED_OTHER,

    DENIED_LEVEL_0
}
