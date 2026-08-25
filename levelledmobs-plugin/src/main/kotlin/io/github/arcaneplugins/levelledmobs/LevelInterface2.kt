package io.github.arcaneplugins.levelledmobs

import io.github.arcaneplugins.levelledmobs.result.AdditionalLevelInformation
import io.github.arcaneplugins.levelledmobs.enums.LevellableState
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper

/**
 * Добро пожаловать в LevelInterface(2), этот класс представляет собой «глобальный» интерфейс для самого LM и других плагинов, позволяющий
 * применять и изменять основные функции LevelledMobs.
 *
 * @author lokka30, stumper66
 * @since 2.5
 */
interface LevelInterface2 : LevelInterface {
    /**
     * Проверьте, разрешено ли повышать уровень существующего моба в соответствии с конфигурацией пользователя.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmInterface целевой моб
     * @return разрешено ли назначать мобу уровень (да/нет), с указанием причины
     */
    fun getLevellableState(lmInterface: LivingEntityInterface): LevellableState

    /**
     * Этот метод генерирует уровень для моба. Он использует стратегию назначения уровня, указанный
     * администратором через конфигурацию settings.yml.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmEntity сущность, генерирующая уровень для
     * @return уровень для сущности
     */
    fun generateLevel(lmEntity: LivingEntityWrapper): Int

    /**
     * Этот метод генерирует уровень для моба. Он использует стратегию назначения уровня, указанный
     * администратором через конфигурацию settings.yml.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmEntity сущность, генерирующая уровень для
     * @param minLevel минимальный уровень, который будет использоваться для моба
     * @param maxLevel максимальный уровень, который будет использоваться для моба
     * @return уровень для сущности
     */
    fun generateLevel(
        lmEntity: LivingEntityWrapper,
        minLevel: Int,
        maxLevel: Int
    ): Int

    /**
     * Этот метод применяет уровень к целевому мобу.
     *
     * Метод можно вызвать независимо от того, имеет моб уровень или нет.
     *
     * Метод не проверяет, можно ли назначить сущности уровень. Вызывающий плагин должен выполнить
     * эту проверку самостоятельно, если намеренно не обходит ограничения.
     *
     * Рекомендуется оставить bypassLimits = false, если не требуется намеренно
     * переопределить ограничения, заданные пользователем.
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmEntity                   целевой моб
     * @param level                      уровень, который должен быть у моба
     * @param isSummoned                 если моб был создан LevelledMobs, а не сервером
     * @param bypassLimits               должен ли LM игнорировать максимальный уровень и т. д.
     * @param additionalLevelInformation используется для определения исходного события
     */
    fun applyLevelToMob(
        lmEntity: LivingEntityWrapper,
        level: Int,
        isSummoned: Boolean,
        bypassLimits: Boolean,
        additionalLevelInformation: MutableSet<AdditionalLevelInformation>?
    )

    /**
     * Снять уровень моба.
     *
     * @param lmEntity уровневый моб до снятия уровня
     */
    fun removeLevel(lmEntity: LivingEntityWrapper)
}
