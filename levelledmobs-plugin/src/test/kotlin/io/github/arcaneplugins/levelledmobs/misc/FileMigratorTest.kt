package io.github.arcaneplugins.levelledmobs.misc

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class FileMigratorTest {
    @Test
    fun `version 12 migration removes only level 999 announcements`() {
        val source = """
            all_levellable_mobs:
              - DIAMOND:
                  chance: 1.0
                  minLevel: 999
                  maxLevel: 999
              - customCommand:
                  name: jw_999_spawn_announcement
                  command:
                    - 'minecraft:tellraw @a {"text":"spawn"}'
                    - 'minecraft:execute as @a run playsound minecraft:entity.wither.spawn master @s'
                  run-on-spawn: true
              - customCommand:
                  name: keep_this_command
                  command:
                    - 'say preserved'
              - customCommand:
                  name: jw_999_death_announcement
                  command:
                    - 'minecraft:tellraw @a {"text":"death"}'
                    - 'minecraft:execute as @a run playsound minecraft:ui.toast.challenge_complete master @s'
                  run-on-death: true
            file-version: 12
        """.trimIndent() + "\n"

        val migrated = FileMigrator.migrateCustomDropsContent(source, 12)

        assertFalse(migrated.contains("jw_999_spawn_announcement"))
        assertFalse(migrated.contains("jw_999_death_announcement"))
        assertFalse(migrated.contains("minecraft:entity.wither.spawn"))
        assertFalse(migrated.contains("minecraft:ui.toast.challenge_complete"))
        assertTrue(migrated.contains("DIAMOND:"))
        assertTrue(migrated.contains("name: keep_this_command"))
        assertTrue(migrated.contains("say preserved"))
        assertTrue(migrated.contains("file-version: 13"))
        Yaml().load<Any>(migrated)
    }

    @Test
    fun `bundled custom drops contain rewards but no global announcements`() {
        val resource = checkNotNull(javaClass.classLoader.getResourceAsStream("customdrops.yml"))
            .bufferedReader()
            .use { it.readText() }

        assertFalse(resource.contains("jw_999_spawn_announcement"))
        assertFalse(resource.contains("jw_999_death_announcement"))
        assertFalse(resource.contains("minecraft:entity.wither.spawn"))
        assertFalse(resource.contains("minecraft:ui.toast.challenge_complete"))
        assertTrue(resource.contains("jw_999_diamonds"))
        assertTrue(resource.contains("jw_999_totem"))
        assertTrue(resource.contains("jw_trophy_999"))
        assertTrue(resource.contains("file-version: 13"))
        Yaml().load<Any>(resource)
    }
}
