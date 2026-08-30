package io.github.arcaneplugins.levelledmobs.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JudgementRelicLogicTest {
    @Test
    fun `persistent relic ids resolve exactly`() {
        assertEquals(JudgementRelic.VERDICT_SWORD, JudgementRelic.fromPersistentValue("verdict_sword"))
        assertEquals(JudgementRelic.EXECUTION_AXE, JudgementRelic.fromPersistentValue("EXECUTION_AXE"))
        assertEquals(JudgementRelic.FINAL_WARNING_BOW, JudgementRelic.fromPersistentValue("final_warning_bow"))
        assertEquals(JudgementRelic.FINAL_CLAUSE_MACE, JudgementRelic.fromPersistentValue("final_clause_mace"))
        assertNull(JudgementRelic.fromPersistentValue("ordinary_item"))
    }

    @Test
    fun `relic damage calculations and cooldown boundaries are stable`() {
        assertTrue(JudgementRelicLogic.cooldownReady(1_000L, 9_000L, 8_000L))
        assertFalse(JudgementRelicLogic.cooldownReady(1_001L, 9_000L, 8_000L))
        assertEquals(14.0, JudgementRelicLogic.damageWithBonus(10.0, 4.0), 0.000001)
        assertEquals(4.0, JudgementRelicLogic.cleaveDamage(10.0, 0.40), 0.000001)
        assertEquals(10.0, JudgementRelicLogic.cleaveDamage(10.0, 2.0), 0.000001)
    }

    @Test
    fun `bow marks require the same owner and unexpired absolute time`() {
        assertTrue(JudgementRelicLogic.markApplies("owner", "owner", 5_000L, 5_000L))
        assertFalse(JudgementRelicLogic.markApplies("owner", "other", 5_000L, 4_000L))
        assertFalse(JudgementRelicLogic.markApplies("owner", "owner", 4_999L, 5_000L))
    }

    @Test
    fun `mace wave requires the configured fall distance`() {
        assertFalse(JudgementRelicLogic.isMaceSmash(1.49f, 1.5))
        assertTrue(JudgementRelicLogic.isMaceSmash(1.5f, 1.5))
        assertTrue(JudgementRelicLogic.isMaceSmash(8.0f, 1.5))
    }
}
