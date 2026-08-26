package io.github.arcaneplugins.levelledmobs.archetypes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class MobArchetypeConfigurationTest {
    private val settings: Map<String, Any?> by lazy { loadYaml("settings.yml") }
    private val rules: Map<String, Any?> by lazy { loadYaml("rules.yml") }

    @Test
    fun `settings retain version and exact class chances`() {
        assertEquals(40, settings["file-version"])
        val root = map(settings, "judgement-week-archetypes")
        assertEquals(true, root["enabled"])
        assertEquals(1.5, number(root, "artifact-chance-multiplier"))
        val chances = map(root, "chances")
        mapOf(
            "300-449" to 0.10, "450-599" to 0.15, "600-749" to 0.25,
            "750-899" to 0.40, "900-998" to 0.60, "999" to 1.0
        ).forEach { (range, expected) -> assertEquals(expected, number(chances, range), 0.000001) }
    }

    @Test
    fun `all class and boss displays and passives are configured`() {
        val root = map(settings, "judgement-week-archetypes")
        val classes = map(root, "classes")
        val bosses = map(root, "bosses")
        assertEquals(setOf("berserker", "bastion", "vampire", "plague", "thunderer"), classes.keys)
        assertEquals(setOf("dragon-tempest", "wither-necromancer", "warden-resonance", "elder-abyss"), bosses.keys)

        assertDefinition(classes, "berserker", "&c[Берсерк]", "outgoing-damage", 1.25)
        assertDefinition(classes, "bastion", "&7[Бастион]", "max-health", 1.35)
        assertDefinition(classes, "vampire", "&4[Вампир]", "outgoing-damage", 1.10)
        assertDefinition(classes, "plague", "&2[Чумной]", "follow-range", 1.30)
        assertDefinition(classes, "thunderer", "&b[Громовержец]", "attack-knockback", 0.35)
        assertDefinition(bosses, "dragon-tempest", "&5[Повелитель Бури]", "flying-speed", 1.15)
        assertDefinition(bosses, "wither-necromancer", "&8[Некромант]", "max-health", 1.15)
        assertDefinition(bosses, "warden-resonance", "&1[Абсолютный Резонанс]", "knockback-resistance", 0.40)
        assertDefinition(bosses, "elder-abyss", "&3[Владыка Глубин]", "armor-bonus", 6.0)
    }

    @Test
    fun `active ability timings and values match profile`() {
        val root = map(settings, "judgement-week-archetypes")
        val classes = map(root, "classes")
        val bosses = map(root, "bosses")
        assertAbility(classes, "berserker", "duration", "8s")
        assertAbility(classes, "bastion", "cooldown", "8s")
        assertAbility(classes, "vampire", "cooldown", "2s")
        assertAbility(classes, "plague", "cooldown", "7s")
        assertAbility(classes, "thunderer", "bonus-damage", 4.0)
        assertAbility(bosses, "dragon-tempest", "cooldown", "12s")
        assertAbility(bosses, "wither-necromancer", "phase-radius", 10.0)
        assertAbility(bosses, "warden-resonance", "bonus-damage", 6.0)
        assertAbility(bosses, "elder-abyss", "fatigue-duration", "8s")
    }

    @Test
    fun `archetype placeholder is present in living and death nametags`() {
        val presets = map(rules, "presets")
        val presentation = map(presets, "judgement-week-presentation")
        val config = map(presentation, "settings")
        assertTrue(config["nametag"].toString().startsWith("%mob-archetype%"))
        assertTrue(config["creature-death-nametag"].toString().startsWith("%mob-archetype%"))
    }

    private fun assertDefinition(
        group: Map<String, Any?>,
        id: String,
        display: String,
        passiveKey: String,
        passiveValue: Double
    ) {
        val definition = map(group, id)
        assertEquals(display, definition["display"])
        assertEquals(passiveValue, number(map(definition, "passive"), passiveKey), 0.000001)
    }

    private fun assertAbility(group: Map<String, Any?>, id: String, key: String, expected: Any) {
        val value = map(map(group, id), "ability")[key]
        if (expected is Number) assertEquals(expected.toDouble(), (value as Number).toDouble(), 0.000001)
        else assertEquals(expected, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYaml(name: String): Map<String, Any?> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(name))
        return stream.bufferedReader().use { Yaml().load(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(parent: Map<String, Any?>, key: String): Map<String, Any?> =
        parent[key] as Map<String, Any?>

    private fun number(parent: Map<String, Any?>, key: String): Double =
        (parent[key] as Number).toDouble()
}
