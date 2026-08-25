package io.github.arcaneplugins.levelledmobs

import io.github.arcaneplugins.levelledmobs.enums.LevellableState
import org.bukkit.entity.LivingEntity
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/**
 * Этот интерфейс используется в API и предоставляет функции, использующие
 * в основном общие классы для применения и изменения основных функций LevelledMobs.
 *
 * @author stumper66
 * @since 4.0
 */
interface LevelInterface {
    /**
     * Проверьте, разрешено ли повышать уровень существующего моба в соответствии с конфигурацией пользователя.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param livingEntity целевой моб
     * @return разрешено ли назначать мобу уровень (да/нет), с указанием причины
     */
    @NotNull
    fun getLevellableState(@NotNull livingEntity: LivingEntity): LevellableState

    /**
     * Проверьте, является ли LivingEntity уровневым мобом или нет. Это определяется *после*
     * MobPreLevelEvent.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param livingEntity живое существо для проверки
     * @return есть ли у моба уровень
     */
    fun isLevelled(@NotNull livingEntity: LivingEntity): Boolean

    /**
     * Получите уровень уровневого моба.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param livingEntity уровневый моб, чтобы получить уровень
     * @return уровень моба
     */
    fun getLevelOfMob(@NotNull livingEntity: LivingEntity): Int

    /**
     * Снять уровень моба.
     *
     * @param livingEntity уровневый моб до снятия уровня
     */
    fun removeLevel(@NotNull livingEntity: LivingEntity)

    @Nullable
    fun getMobNametag(@NotNull livingEntity: LivingEntity): String?
}