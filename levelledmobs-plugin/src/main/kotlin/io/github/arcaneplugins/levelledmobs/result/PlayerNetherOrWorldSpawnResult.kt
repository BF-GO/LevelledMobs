package io.github.arcaneplugins.levelledmobs.result

import org.bukkit.Location

/**
 * Используется для хранения информации, которая используется для различных
 * пользовательские заполнители
 *
 * @author stumper66
 * @since 3.6.0
 */
class PlayerNetherOrWorldSpawnResult(
    val location: Location?,
    var isNetherPortalLocation: Boolean,
    var isWorldPortalLocation: Boolean
)