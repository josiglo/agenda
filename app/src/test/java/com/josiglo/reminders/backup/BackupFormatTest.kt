package com.josiglo.reminders.backup

import com.josiglo.reminders.data.Item
import com.josiglo.reminders.data.Repeat
import com.josiglo.reminders.data.RepeatFrom
import com.josiglo.reminders.data.RepeatUnit
import com.josiglo.reminders.settings.Preferences
import com.josiglo.reminders.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalTime

class BackupFormatTest {

    @Test
    fun roundTripKeepsEverything() {
        val data = BackupData(
            Preferences(
                theme = ThemeMode.DARK,
                wallpaperColors = true,
                calendars = setOf(3, 7),
                morning = LocalTime.of(7, 30),
                evening = LocalTime.of(20, 0),
                dayStart = LocalTime.of(3, 0),
            ),
            listOf(
                Item(
                    id = 1, title = "Water plants", note = "Balcony too", place = "Home", position = 0,
                    remindAt = 1_800_000_000_000, repeat = Repeat(4, RepeatUnit.DAY, RepeatFrom.DONE),
                    alertedAt = 1_800_000_000_000, created = 1_790_000_000_000,
                ),
                Item(id = 2, title = "Fill can", position = 1, doneAt = 1_800_000_100_000, nextId = 4),
                Item(id = 3, title = "Balcony solar", position = 2),
            ),
        )
        assertEquals(data, BackupFormat.decode(BackupFormat.encode(data)))
    }

    @Test
    fun missingSettingsFallBackToDefaults() {
        val text = """{"version": 1, "settings": {}, "items": [{"id": 1, "title": "x"}]}"""
        val decoded = BackupFormat.decode(text)
        assertEquals(Preferences(), decoded.preferences)
        assertEquals("x", decoded.items.single().title)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("""{"name": "x"}""") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("""{"version": 1, "items": []}""") }
    }

    @Test
    fun oldBackupsWithKindsAndSubtasksBecomeReminders() {
        val text = """{"version": 1, "settings": {"kindOrder": "task,inbox,reminder"}, "items": [
            {"id": 1, "kind": "task", "title": "Proposal", "topSlot": 2, "topDay": 20756},
            {"id": 2, "kind": "task", "title": "Pricing", "parentId": 1}
        ]}"""
        val items = BackupFormat.decode(text).items
        assertEquals(listOf("Proposal", "Pricing"), items.map { it.title })
    }
}
