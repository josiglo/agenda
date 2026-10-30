package com.josiglo.agenda.backup

import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import com.josiglo.agenda.settings.Preferences
import com.josiglo.agenda.settings.ThemeMode
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
                    id = 1, kind = Kind.TASK, title = "Water plants", note = "Balcony too", place = "Home", position = 0,
                    remindAt = 1_800_000_000_000, repeat = Repeat(4, RepeatUnit.DAY, RepeatFrom.DONE),
                    topSlot = 2, topDay = 20_756, alertedAt = 1_800_000_000_000, created = 1_790_000_000_000,
                ),
                Item(id = 2, kind = Kind.TASK, title = "Fill can", parentId = 1, position = 1, doneAt = 1_800_000_100_000),
                Item(id = 3, kind = Kind.INBOX, title = "Balcony solar", position = 2, nextId = null),
            ),
        )
        assertEquals(data, BackupFormat.decode(BackupFormat.encode(data)))
    }

    @Test
    fun missingSettingsFallBackToDefaults() {
        val text = """{"version": 1, "settings": {}, "items": [{"id": 1, "title": "x"}]}"""
        val decoded = BackupFormat.decode(text)
        assertEquals(Preferences(), decoded.preferences)
        assertEquals(Kind.INBOX, decoded.items.single().kind)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("""{"name": "x"}""") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("""{"version": 1, "items": []}""") }
    }
}
