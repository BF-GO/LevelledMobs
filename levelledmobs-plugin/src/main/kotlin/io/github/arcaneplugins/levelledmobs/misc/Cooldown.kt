package io.github.arcaneplugins.levelledmobs.misc

/**
 * Этот класс используется для установки времени восстановления определенных действий для определённых игровых объектов - например.
 * щелкая объекты или блоки.
 * <p>
 * Например, это используется при отладке повреждения объекта, чтобы спам не щелкал один и тот же объект.
 * и более. Он также используется в информации о спавнере по той же причине: блокирует ненужный спам в чате.
 *
 * @author lokka30
 * @see System#currentTimeMillis()
 * @since 3.1.2
 */
class Cooldown(
    /**
     * Начальная точка восстановления — просто запустите System#currentTimeMillis().
     */
    private val startingTime: Long,
    /**
     * По чему должно быть идентифицировано это время восстановления - например. сущность ID, местоположение
     */
    private val identifier: String
) {
    /**
     * Проверяет, принадлежит ли этот кулдаун чему-то с определенным ID.
     *
     * @param identifier ID, чтобы проверить, имеет ли это время восстановления тот же ID.
     * @return если IDs совпадает.
     */
    fun doesCooldownBelongToIdentifier(identifier: String): Boolean {
        return identifier == this.identifier
    }

    /**
     * Проверьте, не прошло ли необходимое время восстановления (в секундах!) с начальной точки.
     *
     * @param requiredTimeInSeconds сколько секунд должен длиться кулдаун?
     * @return если время восстановления истекло.
     */
    fun hasCooldownExpired(requiredTimeInSeconds: Long): Boolean {
        return ((System.currentTimeMillis() - startingTime) >= (requiredTimeInSeconds * 1000))
    }
}