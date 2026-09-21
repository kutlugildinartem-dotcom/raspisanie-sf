package ru.uust.schedule.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("uust_settings")

/**
 * Единственный источник настроек. Всё хранится одной JSON-строкой:
 * настройки читаются и пишутся целиком, а виджетам нужен атомарный снимок.
 */
class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETTINGS]?.let { decode(it) } ?: AppSettings()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val now = prefs[KEY_SETTINGS]?.let { decode(it) } ?: AppSettings()
            prefs[KEY_SETTINGS] = json.encodeToString(AppSettings.serializer(), transform(now))
        }
    }

    // --- настройки отдельных виджетов, по appWidgetId ---

    suspend fun widgetConfig(appWidgetId: Int): WidgetConfig {
        val raw = context.dataStore.data.first()[widgetKey(appWidgetId)] ?: return WidgetConfig()
        return runCatching { json.decodeFromString(WidgetConfig.serializer(), raw) }
            .getOrDefault(WidgetConfig())
    }

    fun widgetConfigFlow(appWidgetId: Int): Flow<WidgetConfig> =
        context.dataStore.data.map { prefs ->
            prefs[widgetKey(appWidgetId)]
                ?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
                ?: WidgetConfig()
        }

    suspend fun updateWidgetConfig(appWidgetId: Int, transform: (WidgetConfig) -> WidgetConfig) {
        context.dataStore.edit { prefs ->
            val key = widgetKey(appWidgetId)
            val now = prefs[key]
                ?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }
                ?: WidgetConfig()
            prefs[key] = json.encodeToString(WidgetConfig.serializer(), transform(now))
        }
    }

    suspend fun clearWidgetConfig(appWidgetId: Int) {
        context.dataStore.edit { it.remove(widgetKey(appWidgetId)) }
    }

    /** Тема, которой должен рисоваться конкретный виджет. */
    suspend fun themeForWidget(appWidgetId: Int): NeonTheme =
        widgetConfig(appWidgetId).themeOverride ?: current().effectiveWidgetTheme

    /** Группа, расписание которой показывает конкретный виджет. */
    suspend fun groupForWidget(appWidgetId: Int): Int {
        val override = widgetConfig(appWidgetId).groupIdOverride
        return if (override > 0) override else current().groupId
    }

    private fun decode(raw: String): AppSettings =
        runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrDefault(AppSettings())

    private fun widgetKey(id: Int) = stringPreferencesKey("widget_$id")

    companion object {
        private val KEY_SETTINGS = stringPreferencesKey("settings_json")

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}
