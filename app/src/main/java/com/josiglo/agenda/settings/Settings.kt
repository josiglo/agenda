package com.josiglo.agenda.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.time.DayClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.ZoneId

enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

data class Preferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperColors: Boolean = false,
    /** IDs of the calendars shown on Today and Week. */
    val calendars: Set<Long> = emptySet(),
    val morning: LocalTime = LocalTime.of(8, 0),
    val evening: LocalTime = LocalTime.of(19, 0),
    /** When the top 3 and finished items reset. */
    val dayStart: LocalTime = LocalTime.MIDNIGHT,
    /** Order of the kind chips in the editor. The first is the kind of new items. */
    val kindOrder: List<Kind> = Kind.entries,
) {
    fun clock(): DayClock = DayClock(ZoneId.systemDefault(), dayStart)
}

/** App settings, stored in shared preferences. */
class Settings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<Preferences> = _preferences.asStateFlow()

    fun update(change: (Preferences) -> Preferences) = save(change(_preferences.value))

    fun save(new: Preferences) {
        prefs.edit {
            putString(KEY_THEME, new.theme.name)
            putBoolean(KEY_WALLPAPER, new.wallpaperColors)
            putStringSet(KEY_CALENDARS, new.calendars.map { it.toString() }.toSet())
            putInt(KEY_MORNING, new.morning.toSecondOfDay() / 60)
            putInt(KEY_EVENING, new.evening.toSecondOfDay() / 60)
            putInt(KEY_DAY_START, new.dayStart.toSecondOfDay() / 60)
            putString(KEY_KIND_ORDER, new.kindOrder.joinToString(",") { it.key })
        }
        _preferences.value = new
    }

    private fun load(): Preferences {
        val defaults = Preferences()
        return Preferences(
            theme = prefs.getString(KEY_THEME, null)
                ?.let { name -> ThemeMode.entries.find { it.name == name } } ?: defaults.theme,
            wallpaperColors = prefs.getBoolean(KEY_WALLPAPER, defaults.wallpaperColors),
            calendars = prefs.getStringSet(KEY_CALENDARS, null).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            morning = time(KEY_MORNING) ?: defaults.morning,
            evening = time(KEY_EVENING) ?: defaults.evening,
            dayStart = time(KEY_DAY_START) ?: defaults.dayStart,
            kindOrder = kindOrder(prefs.getString(KEY_KIND_ORDER, null)),
        )
    }

    private fun time(key: String): LocalTime? =
        prefs.getInt(key, -1).takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_WALLPAPER = "wallpaper_colors"
        private const val KEY_CALENDARS = "calendars"
        private const val KEY_MORNING = "morning"
        private const val KEY_EVENING = "evening"
        private const val KEY_DAY_START = "day_start"
        private const val KEY_KIND_ORDER = "kind_order"

        /** Reads a comma separated order, adding kinds it lacks at the end. */
        fun kindOrder(text: String?): List<Kind> {
            val listed = text.orEmpty().split(",").mapNotNull { Kind.fromKey(it.trim()) }.distinct()
            return listed + Kind.entries.filter { it !in listed }
        }

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(context.applicationContext).also { instance = it }
        }
    }
}
