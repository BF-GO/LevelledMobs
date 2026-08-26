package io.github.arcaneplugins.levelledmobs.archetypes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MobArchetypeLogicTest {
    @Test
    fun `default class chances have exact inclusive boundaries`() {
        val expected = mapOf(
            299 to 0.0, 300 to 0.10, 449 to 0.10,
            450 to 0.15, 599 to 0.15,
            600 to 0.25, 749 to 0.25,
            750 to 0.40, 899 to 0.40,
            900 to 0.60, 998 to 0.60,
            999 to 1.0, 1000 to 0.0
        )
        expected.forEach { (level, chance) ->
            assertEquals(chance, MobArchetypeLogic.defaultChanceForLevel(level), 0.000001)
        }
    }

    @Test
    fun `one regular class is selected with equal index coverage`() {
        assertNull(MobArchetypeLogic.selectRegular(299, 1.0, 0.0, 0))
        assertNull(MobArchetypeLogic.selectRegular(300, 0.10, 0.10, 0))
        val selected = (0 until 5).map {
            MobArchetypeLogic.selectRegular(300, 1.0, 0.0, it)
        }.toSet()
        assertEquals(MobArchetype.regularEntries.toSet(), selected)
        assertEquals(MobArchetype.BERSERKER, MobArchetypeLogic.selectRegular(999, 1.0, 0.999, 0))
    }

    @Test
    fun `four bosses have fixed archetypes`() {
        assertEquals(MobArchetype.DRAGON_TEMPEST, MobArchetypeLogic.bossForEntityType("ENDER_DRAGON"))
        assertEquals(MobArchetype.WITHER_NECROMANCER, MobArchetypeLogic.bossForEntityType("wither"))
        assertEquals(MobArchetype.WARDEN_RESONANCE, MobArchetypeLogic.bossForEntityType("WARDEN"))
        assertEquals(MobArchetype.ELDER_ABYSS, MobArchetypeLogic.bossForEntityType("ELDER_GUARDIAN"))
        assertNull(MobArchetypeLogic.bossForEntityType("ZOMBIE"))
    }

    @Test
    fun `combat calculations use final multipliers caps and thresholds`() {
        assertEquals(20.625, MobArchetypeLogic.outgoingDamage(10.0, 1.25, 1.25, 5.0), 0.000001)
        assertEquals(5.0, MobArchetypeLogic.incomingDamage(10.0, 0.5), 0.000001)
        assertEquals(6.0, MobArchetypeLogic.healingAmount(20.0, 0.30, 8.0), 0.000001)
        assertEquals(8.0, MobArchetypeLogic.healingAmount(100.0, 0.30, 8.0), 0.000001)
        assertTrue(MobArchetypeLogic.crossesHealthThreshold(50.0, 11.0, 100.0, 0.40))
        assertFalse(MobArchetypeLogic.crossesHealthThreshold(50.0, 9.0, 100.0, 0.40))
    }

    @Test
    fun `absolute cooldowns and health percentage are stable`() {
        assertTrue(MobArchetypeLogic.cooldownReady(1_000L, 9_000L, 8_000L))
        assertFalse(MobArchetypeLogic.cooldownReady(1_001L, 9_000L, 8_000L))
        assertEquals(50.0, MobArchetypeLogic.healthAtSamePercentage(25.0, 100.0, 200.0), 0.000001)
    }

    @Test
    fun `artifact multiplier applies only to artifact groups and caps at one`() {
        assertTrue(MobArchetypeLogic.isArtifactGroup("jw_artifact_900_998"))
        assertTrue(MobArchetypeLogic.isArtifactGroup("jw_boss_warden_artifact"))
        assertFalse(MobArchetypeLogic.isArtifactGroup("jw_resource_900_998_diamond"))
        assertFalse(MobArchetypeLogic.isArtifactGroup("jw_totem_900_998"))
        assertEquals(0.12f, MobArchetypeLogic.effectiveArtifactChance(0.08f, true, 1.5), 0.000001f)
        assertEquals(1.0f, MobArchetypeLogic.effectiveArtifactChance(1.0f, true, 1.5), 0.000001f)
        assertEquals(0.08f, MobArchetypeLogic.effectiveArtifactChance(0.08f, false, 1.5), 0.000001f)
    }
}
