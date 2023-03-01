package com.example.pacetify.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsFile = SettingsPreferenceFile.getInstance(application)

    private val _isCheckedMotivate = MutableStateFlow(settingsFile.motivate)
    val isCheckedMotivate = _isCheckedMotivate.asStateFlow()

    private val _isCheckedRest = MutableStateFlow(settingsFile.rest)
    val isCheckedRest = _isCheckedRest.asStateFlow()

    private val _restTime = MutableStateFlow(settingsFile.restTime)
    val restBarProgress = _restTime.asStateFlow().map { time -> timeToSliderProgress(time) }
    val restTime = _restTime.asStateFlow()

    fun setMotivate(isChecked: Boolean) {
        _isCheckedMotivate.value = isChecked
        settingsFile.motivate = isChecked
    }

    fun setRest(isChecked: Boolean) {
        _isCheckedRest.value = isChecked
        settingsFile.rest = isChecked
    }

    fun setRestTime(barProgress: Int) {
        val newRestTime = sliderProgressToTime(barProgress)
        _restTime.value = newRestTime
        settingsFile.restTime = newRestTime
    }

    // the slider progress bar needs these two functions to convert between steps on the bar and
    // the resting time. The bar goes from 0, hence the '+ 1'
    private fun sliderProgressToTime(progress: Int): Int {
        return (progress + 1) * 10
    }

    private fun timeToSliderProgress(time: Int): Int {
        return (time / 10) - 1
    }
}