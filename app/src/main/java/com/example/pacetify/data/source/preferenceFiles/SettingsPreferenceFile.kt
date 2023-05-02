package com.example.pacetify.data.source.preferenceFiles

import android.content.Context
import com.example.pacetify.R

/**
 * This is a singleton class for the settings preference file manipulation.
 * The file stores the settings entered by the user - motivate, rest and restTime.
 *
 * author: Jiří Loun
 */
class SettingsPreferenceFile(
    context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: SettingsPreferenceFile? = null

        // make this class a singleton
        fun getInstance(context: Context): SettingsPreferenceFile {
            synchronized(this) {
                return INSTANCE ?: SettingsPreferenceFile(context).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val prefEditor = sharedPref.edit()

    var walkingMode: Boolean
        get() = sharedPref.getBoolean("walkingMode", false)
        set(walkingMode) {
            prefEditor.putBoolean("walkingMode", walkingMode)
            prefEditor.apply()
        }

    var motivate: Boolean
        get() = sharedPref.getBoolean("motivate", true)
        set(motivate) {
            prefEditor.putBoolean("motivate", motivate)
            prefEditor.apply()
        }

    var rest: Boolean
        get() = sharedPref.getBoolean("rest", true)
        set(rest) {
            prefEditor.putBoolean("rest", rest)
            prefEditor.apply()
        }

    var restTime: Int
        get() = sharedPref.getInt("time", 60)
        set(time) {
            prefEditor.putInt("time", time)
            prefEditor.apply()
        }

    var theme: Theme
        get() = Theme.values().find { x -> sharedPref.getInt("theme", 0) == x.ordinal } ?: Theme.DEFAULT
        set(theme) {
            prefEditor.putInt("theme", theme.ordinal)
            prefEditor.apply()
        }

    var addBackground: Boolean
        get() = sharedPref.getBoolean("addBg", false)
        set(value) {
            prefEditor.putBoolean("addBg", value)
            prefEditor.apply()
        }

    var themeResource: Int
        get() = if (addBackground) {
            when (theme) {
                Theme.DEFAULT -> R.style.Theme_Pacetify_Default
                Theme.FIRE -> R.style.Theme_Pacetify_Fire
                Theme.WATER -> R.style.Theme_Pacetify_Water
                Theme.EARTH -> R.style.Theme_Pacetify_Earth
                Theme.AIR -> R.style.Theme_Pacetify_Air
            }
        } else {
            when (theme) {
                Theme.DEFAULT -> R.style.Theme_Pacetify_Default_NoBg
                Theme.FIRE -> R.style.Theme_Pacetify_Fire_NoBg
                Theme.WATER -> R.style.Theme_Pacetify_Water_NoBg
                Theme.EARTH -> R.style.Theme_Pacetify_Earth_NoBg
                Theme.AIR -> R.style.Theme_Pacetify_Air_NoBg
            }
        }
        private set(_){}
}