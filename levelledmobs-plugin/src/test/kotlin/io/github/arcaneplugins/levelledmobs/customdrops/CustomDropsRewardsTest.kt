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

    private data class ArmorPiece(
        val material: String,
        val slot: String,
        val name: String,
        val enchantments: Map<String, Int>,
        val lore: String? = null,
        val trimPattern: String? = null,
        val trimMaterial: String? = null,
        val customModelData: Int? = null
    )

    private data class ArmorSet(
        val id: String,
        val minLevel: Int,
        val maxLevel: Int,
        val chance: Double,
        val pieces: List<ArmorPiece>
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
    fun `collectible armor sets use one exact group roll and four uniform slots`() {
        val armorSets = listOf(
            ArmorSet("basic_survivor", 300, 449, 0.05, listOf(
                ArmorPiece("IRON_HELMET", "head", "Каска тревожного жильца",
                    mapOf("protection" to 2, "unbreaking" to 2), "Сверху капает уже не так страшно."),
                ArmorPiece("IRON_CHESTPLATE", "chest", "Жилет разумной осторожности",
                    mapOf("protection" to 2, "unbreaking" to 2), "Разум продаётся отдельно."),
                ArmorPiece("IRON_LEGGINGS", "legs", "Штаны быстрого старта",
                    mapOf("protection" to 2, "unbreaking" to 2), "Главное направление — от опасности."),
                ArmorPiece("IRON_BOOTS", "feet", "Ботинки раннего рассвета",
                    mapOf("protection" to 2, "unbreaking" to 2), "Рассвет не ускоряют. Но попытка хорошая.")
            )),
            ArmorSet("lucky_raider", 450, 599, 0.03, listOf(
                ArmorPiece("DIAMOND_HELMET", "head", "Каска уверенного лица",
                    mapOf("protection" to 4, "unbreaking" to 4), trimPattern = "sentry", trimMaterial = "quartz"),
                ArmorPiece("DIAMOND_CHESTPLATE", "chest", "Жилет законного беспредела",
                    mapOf("protection" to 4, "unbreaking" to 4), trimPattern = "dune", trimMaterial = "redstone"),
                ArmorPiece("DIAMOND_LEGGINGS", "legs", "Поножи быстрого обыска",
                    mapOf("protection" to 4, "unbreaking" to 4), trimPattern = "host", trimMaterial = "copper"),
                ArmorPiece("DIAMOND_BOOTS", "feet", "Ботинки чистого отхода",
                    mapOf("protection" to 4, "unbreaking" to 4), trimPattern = "shaper", trimMaterial = "gold")
            )),
            ArmorSet("titan_special", 600, 749, 0.02, listOf(
                ArmorPiece("DIAMOND_HELMET", "head", "Шлем служебной необходимости",
                    mapOf("protection" to 6, "unbreaking" to 6, "mending" to 1), trimPattern = "sentry", trimMaterial = "emerald"),
                ArmorPiece("DIAMOND_CHESTPLATE", "chest", "Китель Отдела Ликвидации",
                    mapOf("protection" to 5, "thorns" to 4, "unbreaking" to 4),
                    "Форма сотрудника, пережившего собственный отчёт.", "dune", "emerald", 991303),
                ArmorPiece("DIAMOND_LEGGINGS", "legs", "Поножи особого отдела",
                    mapOf("protection" to 6, "unbreaking" to 6, "mending" to 1), trimPattern = "host", trimMaterial = "emerald"),
                ArmorPiece("DIAMOND_BOOTS", "feet", "Берцы убедительного шага",
                    mapOf("protection" to 6, "unbreaking" to 6, "mending" to 1), trimPattern = "shaper", trimMaterial = "emerald")
            )),
            ArmorSet("cursed_optimist", 750, 899, 0.01, listOf(
                ArmorPiece("NETHERITE_HELMET", "head", "Венец плохих предчувствий",
                    mapOf("protection" to 8, "thorns" to 4, "unbreaking" to 8, "mending" to 1), trimPattern = "silence", trimMaterial = "amethyst"),
                ArmorPiece("NETHERITE_CHESTPLATE", "chest", "Панцирь отрицания урона",
                    mapOf("protection" to 8, "thorns" to 4, "unbreaking" to 8, "mending" to 1), trimPattern = "rib", trimMaterial = "redstone"),
                ArmorPiece("NETHERITE_LEGGINGS", "legs", "Поножи дурного знамения",
                    mapOf("protection" to 8, "thorns" to 4, "unbreaking" to 8, "mending" to 1), trimPattern = "eye", trimMaterial = "amethyst"),
                ArmorPiece("NETHERITE_BOOTS", "feet", "Следы на месте преступления",
                    mapOf("protection" to 8, "thorns" to 4, "unbreaking" to 8, "mending" to 1), trimPattern = "spire", trimMaterial = "redstone")
            )),
            ArmorSet("doom_plot_armor", 900, 998, 0.005, listOf(
                ArmorPiece("NETHERITE_HELMET", "head", "Корона без лицензии",
                    mapOf("protection" to 10, "thorns" to 5, "unbreaking" to 10, "mending" to 1), trimPattern = "silence", trimMaterial = "redstone"),
                ArmorPiece("NETHERITE_CHESTPLATE", "chest", "Бронежилет главного злодея",
                    mapOf("protection" to 10, "thorns" to 5, "unbreaking" to 10, "mending" to 1), trimPattern = "rib", trimMaterial = "amethyst"),
                ArmorPiece("NETHERITE_LEGGINGS", "legs", "Поножи сюжетной важности",
                    mapOf("protection" to 10, "thorns" to 5, "unbreaking" to 10, "mending" to 1), trimPattern = "eye", trimMaterial = "redstone"),
                ArmorPiece("NETHERITE_BOOTS", "feet", "Ботинки финальных титров",
                    mapOf("protection" to 10, "thorns" to 5, "unbreaking" to 10, "mending" to 1), trimPattern = "spire", trimMaterial = "amethyst")
            ))
        )

        for (armorSet in armorSets) {
            val groupId = "jw_armor_set_${armorSet.id}"
            val pieces = drops("all_levellable_mobs").filter { it.values["groupid"] == groupId }
            assertEquals(4, pieces.size, "$groupId must contain exactly four pieces")

            for (expected in armorSet.pieces) {
                val drop = pieces.single { it.material == expected.material }
                assertEquals(armorSet.minLevel, number(drop.values, "minLevel").toInt())
                assertEquals(armorSet.maxLevel, number(drop.values, "maxLevel").toInt())
                assertEquals(1.0, number(drop.values, "chance").toDouble())
                assertEquals("1", drop.values["amount"].toString())
                assertTrue(drop.values["name"].toString().endsWith(expected.name))
                assertEquals(expected.enchantments, enchantments(drop))
                assertProtected(drop)

                val limits = map(drop.values, "group-limits")
                assertEquals(1, number(limits, "cap-select").toInt())
                assertEquals(1, number(limits, "cap-per-item").toInt())
                assertEquals("random-single", limits["selection-mode"])
                assertEquals(armorSet.chance, number(limits, "selection-chance").toDouble())

                val persistentData = map(drop.values, "persistent-data")
                assertEquals(armorSet.id, persistentData["crazyenvoys:crazyenvoys_armor_set_id"])
                assertEquals(expected.slot, persistentData["crazyenvoys:crazyenvoys_armor_set_piece"])

                if (expected.lore != null) {
                    assertTrue(list(drop.values, "lore").single().toString().endsWith(expected.lore))
                }
                if (expected.trimPattern != null) {
                    val trim = map(drop.values, "armor-trim")
                    assertEquals(expected.trimPattern, trim["pattern"])
                    assertEquals(expected.trimMaterial, trim["material"])
                } else {
                    assertFalse(drop.values.containsKey("armor-trim"))
                }
                if (expected.customModelData != null) {
                    assertEquals(expected.customModelData, number(drop.values, "custommodeldata").toInt())
                }
            }

            assertEquals(setOf("head", "chest", "legs", "feet"), pieces.map {
                map(it.values, "persistent-data")["crazyenvoys:crazyenvoys_armor_set_piece"]
            }.toSet())
        }
    }

    @Test
    fun `warden leggings and level 999 uniform are new set pieces only`() {
        val wardenLeggings = findDrop("WARDEN", "NETHERITE_LEGGINGS", 300, 400)
        assertEquals(0.01, number(wardenLeggings.values, "chance").toDouble())
        assertEquals("jw_armor_set_cursed_warden", wardenLeggings.values["groupid"])
        assertEquals(991403, number(wardenLeggings.values, "custommodeldata").toInt())
        assertSetMarker(wardenLeggings, "cursed_optimist", "legs")
        assertTrim(wardenLeggings, "eye", "amethyst")
        assertProtected(wardenLeggings)

        val level999Uniform = findDrop("all_levellable_mobs", "NETHERITE_CHESTPLATE", 999, 999)
        assertEquals(1.0, number(level999Uniform.values, "chance").toDouble())
        assertEquals("jw_artifact_999", level999Uniform.values["groupid"])
        assertEquals(991306, number(level999Uniform.values, "custommodeldata").toInt())
        assertSetMarker(level999Uniform, "doom_plot_armor", "chest")
        assertTrim(level999Uniform, "rib", "amethyst")
        assertProtected(level999Uniform)

        assertTrue(drops("all_levellable_mobs").none {
            it.values["groupid"] == "jw_armor_set_doom_plot_armor" &&
                number(it.values, "maxLevel").toInt() > 998
        })
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
        val groupedDrops = trackedDrops.groupBy { it.values["groupid"].toString() }
        for ((groupId, entries) in groupedDrops) {
            val expectedSize = if (groupId in setOf(
                    "jw_armor_set_basic_survivor",
                    "jw_armor_set_lucky_raider",
                    "jw_armor_set_titan_special",
                    "jw_armor_set_cursed_optimist",
                    "jw_armor_set_doom_plot_armor"
                )) 4 else 1
            assertEquals(expectedSize, entries.size, "Unexpected duplicate group $groupId")
        }
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

    private fun assertSetMarker(drop: Drop, setId: String, slot: String) {
        val persistentData = map(drop.values, "persistent-data")
        assertEquals(setId, persistentData["crazyenvoys:crazyenvoys_armor_set_id"])
        assertEquals(slot, persistentData["crazyenvoys:crazyenvoys_armor_set_piece"])
    }

    private fun assertTrim(drop: Drop, pattern: String, material: String) {
        val trim = map(drop.values, "armor-trim")
        assertEquals(pattern, trim["pattern"])
        assertEquals(material, trim["material"])
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
