package com.josiglo.agenda.backup

import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import com.josiglo.agenda.settings.Preferences
import com.josiglo.agenda.settings.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/** The contents of a backup file. */
data class BackupData(val preferences: Preferences, val items: List<Item>)

/** Reads and writes backup files, which are JSON. */
object BackupFormat {
    private const val VERSION = 1

    fun encode(data: BackupData): String = JSONObject().apply {
        put("version", VERSION)
        put("settings", JSONObject().apply {
            val p = data.preferences
            put("theme", p.theme.name)
            put("wallpaperColors", p.wallpaperColors)
            put("calendars", JSONArray(p.calendars.toList()))
            put("morning", p.morning.toString())
            put("evening", p.evening.toString())
            put("dayStart", p.dayStart.toString())
        })
        put("items", JSONArray().apply {
            data.items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("kind", item.kind.key)
                    put("title", item.title)
                    put("note", item.note ?: JSONObject.NULL)
                    put("place", item.place ?: JSONObject.NULL)
                    put("parentId", item.parentId ?: JSONObject.NULL)
                    put("position", item.position)
                    put("remindAt", item.remindAt ?: JSONObject.NULL)
                    put("repeat", item.repeat?.let {
                        JSONObject().put("every", it.every).put("unit", it.unit.key).put("from", it.from.key)
                    } ?: JSONObject.NULL)
                    put("doneAt", item.doneAt ?: JSONObject.NULL)
                    put("topSlot", item.topSlot ?: JSONObject.NULL)
                    put("topDay", item.topDay ?: JSONObject.NULL)
                    put("alertedAt", item.alertedAt ?: JSONObject.NULL)
                    put("nextId", item.nextId ?: JSONObject.NULL)
                    put("created", item.created)
                })
            }
        })
    }.toString(2)

    /** Throws [IllegalArgumentException] if [text] isn't a backup. */
    fun decode(text: String): BackupData {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("not a backup", e)
        }
        require(json.optInt("version") in 1..VERSION) { "not a backup" }
        try {
            val settings = json.getJSONObject("settings")
            val defaults = Preferences()
            val calendars = settings.optJSONArray("calendars")
            val preferences = Preferences(
                theme = ThemeMode.entries.find { it.name == settings.optString("theme") } ?: defaults.theme,
                wallpaperColors = settings.optBoolean("wallpaperColors", defaults.wallpaperColors),
                calendars = (0 until (calendars?.length() ?: 0)).map { calendars!!.getLong(it) }.toSet(),
                morning = settings.time("morning") ?: defaults.morning,
                evening = settings.time("evening") ?: defaults.evening,
                dayStart = settings.time("dayStart") ?: defaults.dayStart,
            )
            val array = json.getJSONArray("items")
            val items = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val repeat = item.optJSONObject("repeat")?.let { r ->
                    val unit = RepeatUnit.fromKey(r.optString("unit"))
                    val from = RepeatFrom.fromKey(r.optString("from"))
                    if (unit != null && from != null && r.optInt("every") >= 1) Repeat(r.getInt("every"), unit, from) else null
                }
                Item(
                    id = item.getLong("id"),
                    kind = Kind.fromKey(item.optString("kind")) ?: Kind.INBOX,
                    title = item.getString("title"),
                    note = item.stringOrNull("note"),
                    place = item.stringOrNull("place"),
                    parentId = item.longOrNull("parentId"),
                    position = item.optLong("position", i.toLong()),
                    remindAt = item.longOrNull("remindAt"),
                    repeat = repeat,
                    doneAt = item.longOrNull("doneAt"),
                    topSlot = item.longOrNull("topSlot")?.toInt(),
                    topDay = item.longOrNull("topDay"),
                    alertedAt = item.longOrNull("alertedAt"),
                    nextId = item.longOrNull("nextId"),
                    created = item.optLong("created"),
                )
            }
            return BackupData(preferences, items)
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("not a backup", e)
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun JSONObject.time(key: String): LocalTime? =
        optString(key).takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}
