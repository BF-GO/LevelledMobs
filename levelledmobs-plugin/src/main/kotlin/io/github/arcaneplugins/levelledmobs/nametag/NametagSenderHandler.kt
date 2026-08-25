package io.github.arcaneplugins.levelledmobs.nametag

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.util.Log

/**
 * Manages the ideal nametag sender implementation for the server's version
 *
 * @author PenalBuffalo (aka stumper66)
 * @since 3.6.0
 */
class NametagSenderHandler {
    private var currentUtil: NametagSender? = null

    fun getCurrentUtil(): NametagSender? {
        if (this.currentUtil != null) return this.currentUtil

        this.currentUtil = NmsNametagSender()

        if (LevelledMobs.instance.ver.minecraftVersion.isLessThan("26.1")) {
            Log.infKey("console.nametag.nms-version", mapOf(
                "version" to LevelledMobs.instance.ver.nmsVersion
            ))
        }

        return this.currentUtil
    }

    fun refresh() {
        if (currentUtil is NmsNametagSender)
            (currentUtil as NmsNametagSender).refresh()
    }
}
