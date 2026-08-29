package io.github.arcaneplugins.levelledmobs.customdrops

import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RandomSingleGroupSelectorTest {
    @Test
    fun `selection modes parse without changing the sequential default`() {
        assertEquals(GroupSelectionMode.SEQUENTIAL, GroupSelectionMode.fromConfig(null))
        assertEquals(GroupSelectionMode.SEQUENTIAL, GroupSelectionMode.fromConfig("sequential"))
        assertEquals(GroupSelectionMode.RANDOM_SINGLE, GroupSelectionMode.fromConfig("random-single"))
        assertEquals(GroupSelectionMode.RANDOM_SINGLE, GroupSelectionMode.fromConfig("RANDOM_SINGLE"))
        assertNull(GroupSelectionMode.fromConfig("random-many"))

        val limits = GroupLimits()
        assertTrue(limits.isEmpty)
        assertEquals(GroupSelectionMode.SEQUENTIAL, limits.selectionMode)
        assertEquals(1.0, limits.selectionChance)
    }

    @Test
    fun `chance boundaries are exact`() {
        assertTrue(RandomSingleGroupSelector.passesChance(1.0, 0.999999))
        assertTrue(RandomSingleGroupSelector.passesChance(0.05, 0.049999))
        assertTrue(!RandomSingleGroupSelector.passesChance(0.05, 0.05))
        assertTrue(!RandomSingleGroupSelector.passesChance(0.0, 0.0))
        assertNull(RandomSingleGroupSelector.selectIndex(0, 1.0, Random(1L)))
    }

    @Test
    fun `successful selection is uniform and returns one candidate`() {
        val random = Random(991_306L)
        val counts = IntArray(4)
        repeat(200_000) {
            val selected = RandomSingleGroupSelector.selectIndex(4, 1.0, random)
            counts[checkNotNull(selected)]++
        }

        for (count in counts) {
            assertTrue(count in 48_500..51_500, "Unexpected distribution: ${counts.toList()}")
        }
    }

    @Test
    fun `group chance is applied once before uniform selection`() {
        val random = Random(300_449L)
        val counts = IntArray(4)
        repeat(200_000) {
            val selected = RandomSingleGroupSelector.selectIndex(4, 0.05, random)
            if (selected != null) counts[selected]++
        }

        val selectedTotal = counts.sum()
        assertTrue(selectedTotal in 9_700..10_300, "Unexpected 5% total: $selectedTotal")
        for (count in counts) {
            assertTrue(count in 2_250..2_750, "Unexpected part distribution: ${counts.toList()}")
        }
    }
}
