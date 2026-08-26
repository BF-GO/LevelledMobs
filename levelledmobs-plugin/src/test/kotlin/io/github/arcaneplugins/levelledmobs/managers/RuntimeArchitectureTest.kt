package io.github.arcaneplugins.levelledmobs.managers

import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RuntimeArchitectureTest {
    @Test
    fun `compiled runtime contains no legacy full scanner or polling queue`() {
        val levelManager = classText(LevelManager::class.java)
        val mobsQueue = classText(MobsQueueManager::class.java)
        val nametagQueue = classText(NametagQueueManager::class.java)

        assertFalse(levelManager.contains("enumerateNearbyEntities"))
        assertFalse(levelManager.contains("getNearbyEntities"))
        assertFalse(levelManager.contains("getScheduler"))
        assertFalse(mobsQueue.contains("Thread.sleep"))
        assertFalse(mobsQueue.contains("poll"))
        assertFalse(nametagQueue.contains("Thread.sleep"))
        assertFalse(nametagQueue.contains("poll"))
    }

    private fun classText(type: Class<*>): String {
        val resourceName = "/${type.name.replace('.', '/')}.class"
        val bytes = requireNotNull(type.getResourceAsStream(resourceName)).use { it.readAllBytes() }
        return String(bytes, StandardCharsets.ISO_8859_1)
    }
}
