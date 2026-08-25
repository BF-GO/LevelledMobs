package io.github.arcaneplugins.levelledmobs.util

import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * Предоставляет функции для APIs, которые используются в Paper 1.17+, но отсутствуют в 1.16.
 *
 * @author stumper66
 * @since 3.3.0
 */
object Paper117Utils {
    fun serializeTextComponent(
        textComponent: TextComponent
    ): String {
        return PlainTextComponentSerializer.plainText().serialize(textComponent)
    }
}