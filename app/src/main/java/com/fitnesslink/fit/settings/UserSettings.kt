package com.fitnesslink.fit.settings

import android.content.Context
import android.content.SharedPreferences
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS `AppTheme`: System follows the device, Light / Dark force a scheme. */
enum class Appearance(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromWire(raw: String?): Appearance = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/**
 * Device-level user preferences that are not session cues (those live in
 * `session/SessionSettings.kt`). Port of the non-session half of iOS
 * `SettingsStore` + the `@AppStorage(AppTheme.storageKey)` appearance key.
 *
 * `FitnessLinkApp.onCreate` must call [attach] once; it hydrates
 * `WeightUnit.current` / `DistanceUnit.current` from the saved choice so
 * every screen renders in the right unit from the first frame. Until then
 * the in-memory defaults apply.
 */
object UserSettings {
    private const val PREFS = "fl_user_settings"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_WEIGHT_UNIT = "weight_unit"
    private const val KEY_DISTANCE_UNIT = "distance_unit"
    private const val KEY_HEALTH_CONNECT_CALORIES = "health_connect_calories_enabled"

    private var prefs: SharedPreferences? = null

    private val _appearance = MutableStateFlow(Appearance.SYSTEM)
    /** Observed by `FitnessLinkTheme`; changes apply app-wide immediately. */
    val appearanceFlow: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _version = MutableStateFlow(0)
    /** Bumped on every write so Compose readers refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _appearance.value = Appearance.fromWire(p.getString(KEY_APPEARANCE, null))
        WeightUnit.current = weightUnit
        DistanceUnit.current = distanceUnit
    }

    var appearance: Appearance
        get() = _appearance.value
        set(value) {
            prefs?.edit()?.putString(KEY_APPEARANCE, value.name)?.apply()
            _appearance.value = value
            bump()
        }

    /**
     * Display unit for lifting weights (FTNSSLNK-325). Stored data stays in
     * lbs; this only changes what is shown and how entered values are read.
     * Defaults to lbs so existing users see no change.
     */
    var weightUnit: WeightUnit
        get() = when (prefs?.getString(KEY_WEIGHT_UNIT, null)) {
            "kg" -> WeightUnit.KG
            "lbs" -> WeightUnit.LBS
            else -> WeightUnit.LBS
        }
        set(value) {
            prefs?.edit()?.putString(KEY_WEIGHT_UNIT, value.label)?.apply()
            WeightUnit.current = value
            bump()
        }

    /** Distance entry / display unit for cardio sets (FTNSSLNK-409). Defaults from the locale. */
    var distanceUnit: DistanceUnit
        get() = DistanceUnit.fromWire(prefs?.getString(KEY_DISTANCE_UNIT, null)) ?: DistanceUnit.defaultUnit
        set(value) {
            prefs?.edit()?.putString(KEY_DISTANCE_UNIT, value.rawValue)?.apply()
            DistanceUnit.current = value
            bump()
        }

    /**
     * iOS "Use Apple Health for Calories" (FTNSSLNK-328). Stored only — the
     * Health Connect read path is not built yet; reports keep the estimate.
     */
    var useHealthConnectCalories: Boolean
        get() = prefs?.getBoolean(KEY_HEALTH_CONNECT_CALORIES, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_HEALTH_CONNECT_CALORIES, value)?.apply()
            bump()
        }

    private fun bump() { _version.value += 1 }
}
