// One getter/setter/flow trio per setting, the WidgetPreferences shape.
@file:Suppress("TooManyFunctions")

package com.bydmate.app.acbubble

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Settings of the floating AC bubble, in their own "bydmate_ac_bubble" file so they never
 * collide with the stats widget's keys (WidgetPreferences) when upstream changes those.
 */
class AcBubblePreferences(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    fun enabledFlow(): Flow<Boolean> = flowOf(KEY_ENABLED) { isEnabled() }

    fun getX(): Int = prefs.getInt(KEY_X, NO_POSITION)
    fun getY(): Int = prefs.getInt(KEY_Y, NO_POSITION)
    fun hasPosition(): Boolean = prefs.contains(KEY_X) && prefs.contains(KEY_Y)
    fun savePosition(x: Int, y: Int) = prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    fun resetPosition() = prefs.edit().remove(KEY_X).remove(KEY_Y).remove(KEY_DOCK_RIGHT).remove(KEY_DOCK_Y).apply()

    fun getAlpha(): Float = prefs.getFloat(KEY_ALPHA, 1.0f)
    fun setAlpha(alpha: Float) = prefs.edit().putFloat(KEY_ALPHA, alpha.coerceIn(ALPHA_MIN, 1.0f)).apply()
    fun alphaFlow(): Flow<Float> = flowOf(KEY_ALPHA) { getAlpha() }

    /** Colour scheme of every piece of the bubble, by [AcTheme.key]; unknown keys fall back to the default. */
    fun getThemeKey(): String? = prefs.getString(KEY_THEME, null)
    fun setThemeKey(key: String) = prefs.edit().putString(KEY_THEME, key).apply()
    fun themeKeyFlow(): Flow<String?> = flowOf(KEY_THEME) { getThemeKey() }

    fun getScale(): Float = prefs.getFloat(KEY_SCALE, 1.0f)
    fun setScale(scale: Float) = prefs.edit().putFloat(KEY_SCALE, scale.coerceIn(SCALE_MIN, SCALE_MAX)).apply()
    fun scaleFlow(): Flow<Float> = flowOf(KEY_SCALE) { getScale() }

    /** Show the bubble only over the home screen (launcher); hide it in every other app. */
    fun isHomeOnly(): Boolean = prefs.getBoolean(KEY_HOME_ONLY, false)
    fun setHomeOnly(homeOnly: Boolean) = prefs.edit().putBoolean(KEY_HOME_ONLY, homeOnly).apply()
    fun homeOnlyFlow(): Flow<Boolean> = flowOf(KEY_HOME_ONLY) { isHomeOnly() }

    fun isHideOnYoutube(): Boolean = prefs.getBoolean(KEY_HIDE_ON_YOUTUBE, false)
    fun setHideOnYoutube(hide: Boolean) = prefs.edit().putBoolean(KEY_HIDE_ON_YOUTUBE, hide).apply()
    fun hideOnYoutubeFlow(): Flow<Boolean> = flowOf(KEY_HIDE_ON_YOUTUBE) { isHideOnYoutube() }

    /** The strip stays open for good: the bubble becomes a drag grip and nothing folds it away. */
    fun isPinned(): Boolean = prefs.getBoolean(KEY_PINNED, false)
    fun setPinned(pinned: Boolean) = prefs.edit().putBoolean(KEY_PINNED, pinned).apply()
    fun pinnedFlow(): Flow<Boolean> = flowOf(KEY_PINNED) { isPinned() }

    /** The edge tab replaces the bubble: docked to a side of the screen, showing temperature, fan and airflow. */
    fun isDocked(): Boolean = prefs.getBoolean(KEY_DOCKED, false)
    fun setDocked(docked: Boolean) = prefs.edit().putBoolean(KEY_DOCKED, docked).apply()
    fun dockedFlow(): Flow<Boolean> = flowOf(KEY_DOCKED) { isDocked() }

    /** Where the tab hangs: the right edge until it is dragged to the left one. */
    fun isDockRight(): Boolean = prefs.getBoolean(KEY_DOCK_RIGHT, true)
    fun getDockY(): Int = prefs.getInt(KEY_DOCK_Y, NO_POSITION)
    fun saveDock(right: Boolean, y: Int) = prefs.edit().putBoolean(KEY_DOCK_RIGHT, right).putInt(KEY_DOCK_Y, y).apply()

    /** Seconds without a tap before the open strip folds back into the bubble. */
    fun getAutoCloseSec(): Int = prefs.getInt(KEY_AUTO_CLOSE_SEC, DEFAULT_AUTO_CLOSE_SEC)
    fun setAutoCloseSec(sec: Int) =
        prefs.edit().putInt(KEY_AUTO_CLOSE_SEC, sec.coerceIn(AUTO_CLOSE_MIN_SEC, AUTO_CLOSE_MAX_SEC)).apply()
    fun autoCloseSecFlow(): Flow<Int> = flowOf(KEY_AUTO_CLOSE_SEC) { getAutoCloseSec() }

    /** The custom buttons, always [AcPresets.MAX] slots (empty ones are disabled). */
    fun getPresets(): List<AcPreset> = AcPresets.decode(prefs.getString(KEY_PRESETS, null))
    fun setPresets(presets: List<AcPreset>) = prefs.edit().putString(KEY_PRESETS, AcPresets.encode(presets)).apply()
    fun setPreset(slot: Int, preset: AcPreset) =
        setPresets(getPresets().toMutableList().also { if (slot in it.indices) it[slot] = preset })
    fun presetsFlow(): Flow<List<AcPreset>> = flowOf(KEY_PRESETS) { getPresets() }

    private fun <T> flowOf(key: String, read: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) trySend(read())
        }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val PREFS_NAME = "bydmate_ac_bubble"
        const val KEY_ENABLED = "ac_bubble_enabled"
        const val KEY_X = "ac_bubble_x"
        const val KEY_Y = "ac_bubble_y"
        const val KEY_ALPHA = "ac_bubble_alpha"
        const val KEY_SCALE = "ac_bubble_scale"
        const val KEY_THEME = "ac_bubble_theme"
        const val KEY_HOME_ONLY = "ac_bubble_home_only"
        const val KEY_HIDE_ON_YOUTUBE = "ac_bubble_hide_youtube"
        const val KEY_AUTO_CLOSE_SEC = "ac_bubble_auto_close_sec"
        const val KEY_PINNED = "ac_bubble_pinned"
        const val KEY_DOCKED = "ac_bubble_docked"
        const val KEY_DOCK_RIGHT = "ac_bubble_dock_right"
        const val KEY_DOCK_Y = "ac_bubble_dock_y"
        const val KEY_PRESETS = "ac_bubble_presets"
        const val NO_POSITION = Int.MIN_VALUE
        const val ALPHA_MIN = 0.3f
        const val SCALE_MIN = 0.7f
        const val SCALE_MAX = 1.6f
        const val DEFAULT_AUTO_CLOSE_SEC = 6
        const val AUTO_CLOSE_MIN_SEC = 3
        const val AUTO_CLOSE_MAX_SEC = 20
    }
}
