package com.example.pacetify.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.pacetify.data.PacetifyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PacetifyRepository.getInstance(application)

    private val _isCheckedMotivate = MutableStateFlow(repository.settingsMotivate)
    val isCheckedMotivate = _isCheckedMotivate.asStateFlow()

    private val _isCheckedRest = MutableStateFlow(repository.settingsRest)
    val isCheckedRest = _isCheckedRest.asStateFlow()

    private val _restTime = MutableStateFlow(repository.settingsRestTime)
    val restBarProgress = _restTime.asStateFlow().map { time -> timeToSliderProgress(time) }
    val restTime = _restTime.asStateFlow()

    fun setMotivate(isChecked: Boolean) {
        _isCheckedMotivate.value = isChecked
        repository.settingsMotivate = isChecked
    }

    fun setRest(isChecked: Boolean) {
        _isCheckedRest.value = isChecked
        repository.settingsRest = isChecked
    }

    fun setRestTime(barProgress: Int) {
        val newRestTime = sliderProgressToTime(barProgress)
        _restTime.value = newRestTime
        repository.settingsRestTime = newRestTime
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