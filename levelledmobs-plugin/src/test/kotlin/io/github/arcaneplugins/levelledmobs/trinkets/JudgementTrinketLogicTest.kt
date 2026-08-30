package io.github.arcaneplugins.levelledmobs.trinkets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JudgementTrinketLogicTest {
    @Test
    fun `persistent trinket ids resolve and duplicates never stack`() {
        assertEquals(
            JudgementTrinket.VITALITY_NECKLACE,
            JudgementTrinket.fromPersistentValue("vitality_necklace")
        )
        assertNull(JudgementTrinket.fromPersistentValue("ordinary_amethyst"))
        val active = JudgementTrinketLogic.activeTrinkets(listOf(
            "vitality_necklace",
            "VITALITY_NECKLACE",
            "armor_badge",
            null,
            "ordinary_item"
        ))
        assertEquals(setOf(
            JudgementTrinket.VITALITY_NECKLACE,
            JudgementTrinket.ARMOR_BADGE
        ), active)
    }

    @Test
    fun `signatures are stable regardless of hotbar order`() {
        val first = linkedMapOf(
            JudgementTrinket.EMERGENCY_SOCK to 0.05,
            JudgementTrinket.VITALITY_NECKLACE to 4.0
        )
        val second = linkedMapOf(
            JudgementTrinket.VITALITY_NECKLACE to 4.0,
            JudgementTrinket.EMERGENCY_SOCK to 0.05
        )
        assertEquals(JudgementTrinketLogic.signature(first), JudgementTrinketLogic.signature(second))
    }

    @Test
    fun `anti elytra absolute times have exact boundaries`() {
        assertTrue(JudgementTrinketLogic.cooldownReady(1_000L, 16_000L, 15_000L))
        assertFalse(JudgementTrinketLogic.cooldownReady(1_001L, 16_000L, 15_000L))
        assertTrue(JudgementTrinketLogic.isGrounded(6_001L, 6_000L))
        assertFalse(JudgementTrinketLogic.isGrounded(6_000L, 6_000L))
        assertEquals("airspace_denial_crossbow", JudgementTrinketLogic.ANTI_ELYTRA_ITEM_ID)
    }
}
