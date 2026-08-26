package io.github.arcaneplugins.levelledmobs.customdrops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class CustomDropsRewardsTest {
    private data class Drop(
        val material: String,
        val values: Map<String, Any?>
    )

    private data class Reward(
        val material: String,
        val amount: String
    )

    private data class Artifact(
        val section: String,
        val material: String,
        val minLevel: Int,
        val maxLevel: Int,
        val chance: Double,
        val customModelData: Int,
        val groupId: String,
        val enchantments: Map<String, Int>
    )

    private val config: Map<String, Any?> by lazy {
        val resource = checkNotNull(javaClass.classLoader.getResourceAsStream("customdrops.yml"))
        resource.bufferedReader().use { reader ->
            Yaml().load(reader)
        }
    }

    @Test
    fun `level 300 through 999 resources are guaranteed and exact`() {
        val tiers = listOf(
            Triple(300, 449, listOf(
                Reward("IRON_INGOT", "32-64"),
                Reward("GOLD_INGOT", "16-32"),
                Reward("DIAMOND", "2-4")
            )),
            Triple(450, 599, listOf(
                Reward("GOLD_INGOT", "32-64"),
                Reward("DIAMOND", "4-8"),
                Reward("GOLDEN_APPLE", "1-2")
            )),
            Triple(600, 749, listOf(
                Reward("DIAMOND", "8-16"),
                Reward("NETHERITE_SCRAP", "2-4"),
                Reward("GOLDEN_APPLE", "2-4"),
                Reward("EXPERIENCE_BOTTLE", "16-32")
            )),
            Triple(750, 899, listOf(
                Reward("DIAMOND", "16-32"),
                Reward("NETHERITE_SCRAP", "4-8"),
                Reward("ENCHANTED_GOLDEN_APPLE", "1"),
                Reward("EXPERIENCE_BOTTLE", "32-64")
            )),
            Triple(900, 998, listOf(
                Reward("DIAMOND", "32-64"),
                Reward("NETHERITE_INGOT", "2-4"),
                Reward("ENCHANTED_GOLDEN_APPLE", "1-2"),
                Reward("ECHO_SHARD", "8-16")
            )),
            Triple(999, 999, listOf(
                Reward("DIAMOND", "64"),
                Reward("NETHERITE_INGOT", "8"),
                Reward("ENCHANTED_GOLDEN_APPLE", "4"),
                Reward("TOTEM_OF_UNDYING", "2"),
                Reward("ECHO_SHARD", "32")
            ))
        )

        for ((minLevel, maxLevel, rewards) in tiers) {
            for (reward in rewards) {
                val drop = findDrop("all_levellable_mobs", reward.material, minLevel, maxLevel)
                assertEquals(1.0, number(drop.values, "chance").toDouble())
                assertEquals(reward.amount, drop.values["amount"].toString())
                assertProtected(drop)
                assertGroupCapCoversAmount(drop)
            }
        }

        val totem750 = findDrop("all_levellable_mobs", "TOTEM_OF_UNDYING", 750, 899)
        assertEquals(0.15, number(totem750.values, "chance").toDouble())
        assertEquals("jw_totem_750_899", totem750.values["groupid"])

        val totem900 = findDrop("all_levellable_mobs", "TOTEM_OF_UNDYING", 900, 998)
        assertEquals(0.30, number(totem900.values, "chance").toDouble())
        assertEquals("jw_totem_900_998", totem900.values["groupid"])
    }

    @Test
    fun `artifacts have exact chances models and unsafe enchantment levels`() {
        val artifacts = listOf(
            Artifact("all_levellable_mobs", "DIAMOND_PICKAXE", 300, 449, 0.03, 991301,
                "jw_artifact_300_449", mapOf("efficiency" to 6, "fortune" to 4, "unbreaking" to 4)),
            Artifact("all_levellable_mobs", "DIAMOND_SWORD", 450, 599, 0.04, 991302,
                "jw_artifact_450_599", mapOf("sharpness" to 6, "looting" to 4, "unbreaking" to 4)),
            Artifact("all_levellable_mobs", "DIAMOND_CHESTPLATE", 600, 749, 0.05, 991303,
                "jw_artifact_600_749", mapOf("protection" to 5, "thorns" to 4, "unbreaking" to 4)),
            Artifact("all_levellable_mobs", "NETHERITE_AXE", 750, 899, 0.06, 991304,
                "jw_artifact_750_899", mapOf("sharpness" to 7, "efficiency" to 7, "unbreaking" to 5)),
            Artifact("all_levellable_mobs", "NETHERITE_SWORD", 900, 998, 0.08, 991305,
                "jw_artifact_900_998", mapOf("sharpness" to 8, "looting" to 5, "fire_aspect" to 3, "unbreaking" to 5)),
            Artifact("all_levellable_mobs", "NETHERITE_CHESTPLATE", 999, 999, 1.0, 991306,
                "jw_artifact_999", mapOf("protection" to 7, "thorns" to 5, "unbreaking" to 6)),
            Artifact("ENDER_DRAGON", "ELYTRA", 300, 400, 0.10, 991401,
                "jw_boss_ender_dragon_artifact", mapOf("unbreaking" to 5, "mending" to 1)),
            Artifact("WITHER", "NETHERITE_HOE", 300, 400, 0.10, 991402,
                "jw_boss_wither_artifact", mapOf("sharpness" to 7, "looting" to 4, "unbreaking" to 5)),
            Artifact("WARDEN", "NETHERITE_LEGGINGS", 300, 400, 0.10, 991403,
                "jw_boss_warden_artifact", mapOf("protection" to 6, "swift_sneak" to 4, "unbreaking" to 5)),
            Artifact("ELDER_GUARDIAN", "TRIDENT", 300, 400, 0.10, 991404,
                "jw_boss_elder_guardian_artifact", mapOf("impaling" to 7, "loyalty" to 4, "unbreaking" to 5))
        )

        for (artifact in artifacts) {
            val drop = findDrop(artifact.section, artifact.material, artifact.minLevel, artifact.maxLevel)
            assertEquals(artifact.chance, number(drop.values, "chance").toDouble())
            assertEquals(artifact.customModelData, number(drop.values, "custommodeldata").toInt())
            assertEquals(artifact.groupId, drop.values["groupid"])
            assertEquals(artifact.enchantments, enchantments(drop))
            assertFalse(list(drop.values, "lore").isEmpty())
            assertProtected(drop)
        }
    }

    @Test
    fun `bosses receive guaranteed independent level 300 through 400 bundles`() {
        val bossRewards = mapOf(
            "ENDER_DRAGON" to listOf(
                Reward("DRAGON_BREATH", "16-32"),
                Reward("END_CRYSTAL", "2-4"),
                Reward("CHORUS_FRUIT", "32-64")
            ),
            "WITHER" to listOf(
                Reward("NETHER_STAR", "1"),
                Reward("WITHER_SKELETON_SKULL", "1-2"),
                Reward("SOUL_SAND", "16-32")
            ),
            "WARDEN" to listOf(
                Reward("ECHO_SHARD", "8-16"),
                Reward("SCULK_CATALYST", "1-2"),
                Reward("RECOVERY_COMPASS", "1")
            ),
            "ELDER_GUARDIAN" to listOf(
                Reward("WET_SPONGE", "8-16"),
                Reward("PRISMARINE_CRYSTALS", "16-32"),
                Reward("HEART_OF_THE_SEA", "1")
            )
        )

        for ((boss, rewards) in bossRewards) {
            for (reward in rewards) {
                val drop = findDrop(boss, reward.material, 300, 400)
                assertEquals(1.0, number(drop.values, "chance").toDouble())
                assertEquals(reward.amount, drop.values["amount"].toString())
                assertTrue(drop.values["groupid"].toString().startsWith("jw_boss_"))
                assertProtected(drop)
                assertGroupCapCoversAmount(drop)
            }
        }
    }

    @Test
    fun `reward groups are independent while vanilla drops and file version stay unchanged`() {
        val trackedSections = listOf(
            "all_levellable_mobs", "ENDER_DRAGON", "WITHER", "WARDEN", "ELDER_GUARDIAN"
        )
        val trackedDrops = trackedSections.flatMap(::drops)
            .filter { it.values["groupid"]?.toString()?.startsWith("jw_") == true }
        val groupIds = trackedDrops.map { it.values["groupid"].toString() }

        assertEquals(groupIds.size, groupIds.toSet().size, "Every reward check must use its own group")
        assertTrue(trackedDrops.all { it.values["override"] != true })
        assertEquals(false, map(config, "defaults")["override"])
        assertEquals(13, number(config, "file-version").toInt())

        val trophyChances = mapOf(
            Pair(300, 449) to Pair(0.02, 990301),
            Pair(450, 599) to Pair(0.03, 990302),
            Pair(600, 749) to Pair(0.05, 990303),
            Pair(750, 899) to Pair(0.08, 990304),
            Pair(900, 998) to Pair(0.15, 990305),
            Pair(999, 999) to Pair(1.0, 990306)
        )
        for ((range, expected) in trophyChances) {
            val trophy = findDrop("all_levellable_mobs", "PAPER", range.first, range.second)
            assertEquals(expected.first, number(trophy.values, "chance").toDouble())
            assertEquals(expected.second, number(trophy.values, "custommodeldata").toInt())
            val expectedGroup = if (range.first == 999) "jw_trophy_999"
                else "jw_trophy_${range.first}_${range.second}"
            assertEquals(expectedGroup, trophy.values["groupid"])
            assertProtected(trophy)
        }
    }

    private fun findDrop(section: String, material: String, minLevel: Int, maxLevel: Int): Drop {
        val matches = drops(section).filter {
            it.material == material &&
                number(it.values, "minLevel").toInt() == minLevel &&
                number(it.values, "maxLevel").toInt() == maxLevel
        }
        assertEquals(1, matches.size, "$section must contain one $material reward for $minLevel-$maxLevel")
        return matches.single()
    }

    private fun assertProtected(drop: Drop) {
        assertEquals(0, number(drop.values, "equipped").toInt())
        assertEquals(true, drop.values["player-caused"])
        assertEquals(true, drop.values["nospawner"])
        assertEquals(true, drop.values["nomultiplier"])
        assertEquals(true, drop.values["use-chunk-kill-max"])
        assertFalse(drop.values["override"] == true)
    }

    private fun assertGroupCapCoversAmount(drop: Drop) {
        val limits = map(drop.values, "group-limits")
        val maxAmount = drop.values["amount"].toString().substringAfterLast('-').toInt()
        assertEquals(1, number(limits, "cap-select").toInt())
        assertTrue(number(limits, "cap-per-item").toInt() >= maxAmount)
    }

    private fun enchantments(drop: Drop): Map<String, Int> =
        map(drop.values, "enchantments").mapValues { (_, value) ->
            assertNotNull(value)
            (value as Number).toInt()
        }

    private fun drops(section: String): List<Drop> = list(config, section).mapNotNull { rawDrop ->
        val entry = (rawDrop as? Map<*, *>)?.entries?.singleOrNull() ?: return@mapNotNull null
        Drop(entry.key.toString(), stringMap(entry.value))
    }

    private fun map(source: Map<String, Any?>, key: String): Map<String, Any?> =
        stringMap(checkNotNull(source[key]) { "Missing map '$key'" })

    private fun list(source: Map<String, Any?>, key: String): List<Any?> =
        checkNotNull(source[key] as? List<*>) { "Missing list '$key'" }

    private fun number(source: Map<String, Any?>, key: String): Number =
        checkNotNull(source[key] as? Number) { "Missing number '$key'" }

    private fun stringMap(value: Any?): Map<String, Any?> =
        checkNotNull(value as? Map<*, *>) { "Expected map but got ${value?.javaClass?.name}" }
            .entries
            .associate { (key, entryValue) -> key.toString() to entryValue }
}
