package io.github.arcaneplugins.levelledmobs.misc

import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper

/**
 * Если класс, используемый в правилах, реализует этот интерфейс, то это будет
 * вызывается при выполнении команды Rules show-effective, а не
 * чем простой вызов #toString()
 *
 * @author stumper66
 * @since 4.4.0
 */
interface EffectiveInfo {
    fun getEffectiveInfo(lmEntity: LivingEntityWrapper): String
}