package com.example.pacetify.data.source.preferenceFiles

import android.content.Context

/**
 * This is a singleton class for the settings preference file manipulation.
 * The file stores the settings entered by the user - motivate, rest and restTime.
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
}