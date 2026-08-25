package io.github.arcaneplugins.levelledmobs.misc

/**
 * Это исключение должно быть выброшено, когда функция в плагине
 * требуется определенная версия сервера, но сервер работает
 * плагин не использует достаточно свежую версию Minecraft для этого.
 *
 * @author lokka30
 * @since 2.0.0
 */
class OutdatedServerVersionException(
    errorMsg: String?
) : RuntimeException(errorMsg)