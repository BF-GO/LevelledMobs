package io.github.arcaneplugins.levelledmobs.summoning

import io.github.arcaneplugins.levelledmobs.misc.RequestedLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class JudgementSummonPolicyTest {
    @Test
    fun `default configuration blocks levels starting at 300`() {
        val settings = loadSettings()
        assertEquals(40, settings["file-version"])
        val root = map(settings, "judgement-week-summoning")
        assertEquals(false, root["allow-high-level-summons"])
        assertEquals(300, (root["high-level-start"] as Number).toInt())
        assertEquals(299, JudgementSummonPolicy.maximumLevel(true, false, 300))
    }

    @Test
    fun `natural spawns and enabled command summons are not capped`() {
        assertNull(JudgementSummonPolicy.maximumLevel(false, false, 300))
        assertNull(JudgementSummonPolicy.maximumLevel(true, true, 300))
    }

    @Test
    fun `single levels and ranges are capped without invalid ranges`() {
        val exact = RequestedLevel().apply { setLevelFromString("999") }
        assertTrue(JudgementSummonPolicy.limit(exact, 299))
        assertEquals(299, exact.level)

        val overlap = RequestedLevel().apply { setLevelFromString("200-400") }
        assertTrue(JudgementSummonPolicy.limit(overlap, 299))
        assertEquals(200, overlap.levelRangeMin)
        assertEquals(299, overlap.levelRangeMax)

        val above = RequestedLevel().apply { setLevelFromString("900-999") }
        assertTrue(JudgementSummonPolicy.limit(above, 299))
        assertEquals(299, above.levelRangeMin)
        assertEquals(299, above.levelRangeMax)

        val allowed = RequestedLevel().apply { setLevelFromString("100-299") }
        assertFalse(JudgementSummonPolicy.limit(allowed, 299))
        assertEquals(100, allowed.levelRangeMin)
        assertEquals(299, allowed.levelRangeMax)
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadSettings(): Map<String, Any?> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("settings.yml"))
        return stream.bufferedReader().use { Yaml().load(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(parent: Map<String, Any?>, key: String): Map<String, Any?> =
        parent[key] as Map<String, Any?>
}
