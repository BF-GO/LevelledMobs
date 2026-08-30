package io.github.arcaneplugins.levelledmobs.trinkets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class JudgementTrinketConfigurationTest {
    private val settings: Map<String, Any?> by lazy {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("settings.yml"))
        stream.bufferedReader().use { Yaml().load(it) }
    }

    @Test
    fun `hotbar effects and anti elytra counter have exact defaults`() {
        assertEquals(40, settings["file-version"])
        val root = map(settings, "judgement-week-trinkets")
        assertEquals(true, root["enabled"])
        assertEquals(10, (root["hotbar-scan-period-ticks"] as Number).toInt())
        val effects = map(root, "effects")
        assertEquals(4.0, number(map(effects, "vitality-necklace"), "max-health"))
        assertEquals(2.0, number(map(effects, "armor-badge"), "armor"))
        assertEquals(0.05, number(map(effects, "emergency-sock"), "movement-speed"))
        assertEquals(0.15, number(map(effects, "common-sense-anchor"), "knockback-resistance"))
        assertEquals(8.0, number(map(effects, "major-vitality"), "max-health"))
        assertEquals(5.0, number(map(effects, "absolute-bulwark"), "armor"))
        assertEquals(2.0, number(map(effects, "absolute-bulwark"), "armor-toughness"))
        assertEquals(0.15, number(map(effects, "damage-license"), "attack-damage"))

        val antiElytra = map(root, "anti-elytra")
        assertEquals("15s", antiElytra["cooldown"])
        assertEquals("6s", antiElytra["grounding-duration"])
        assertEquals("6s", antiElytra["rocket-lock-duration"])
        assertEquals("6s", antiElytra["slow-falling-duration"])
        assertEquals("6s", antiElytra["glowing-duration"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(parent: Map<String, Any?>, key: String): Map<String, Any?> =
        parent[key] as Map<String, Any?>

    private fun number(parent: Map<String, Any?>, key: String): Double =
        (parent[key] as Number).toDouble()
}
