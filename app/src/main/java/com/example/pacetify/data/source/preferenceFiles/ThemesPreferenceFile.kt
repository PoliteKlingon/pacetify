package com.example.pacetify.data.source.preferenceFiles

import android.content.Context
import com.example.pacetify.R

/**
 * This is a singleton class for the theme preference file manipulation.
 */
class ThemesPreferenceFile(
    context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: ThemesPreferenceFile? = null

        // make this class a singleton
        fun getInstance(context: Context): ThemesPreferenceFile {
            synchronized(this) {
                return INSTANCE ?: ThemesPreferenceFile(context).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val sharedPref = context.getSharedPreferences("themes", Context.MODE_PRIVATE)
    private val prefEditor = sharedPref.edit()

    var theme: Theme
        get() = Theme.values().find { x -> sharedPref.getInt("theme", 0) == x.ordinal } ?: Theme.DEFAULT
        set(theme) {
            prefEditor.putInt("theme", theme.ordinal)
            prefEditor.apply()
        }

    var themeResource: Int
        get() = when (theme) {
            Theme.DEFAULT -> R.style.Theme_Pacetify_Default
            Theme.FIRE -> R.style.Theme_Pacetify_Fire
            Theme.WATER -> R.style.Theme_Pacetify_Water
            Theme.EARTH -> R.style.Theme_Pacetify_Earth
            Theme.AIR -> R.style.Theme_Pacetify_Air
        }
        private set(_){}
}