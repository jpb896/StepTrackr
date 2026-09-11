package com.jpb.steptrackr.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jpb.steptrackr.utils.HistoryTimeFrame
import com.jpb.steptrackr.utils.StepDataPoint
import com.jpb.steptrackr.utils.StepHistoryState
import com.jpb.steptrackr.utils.StepRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class StepHistoryViewModel(
    private val stepRepository: StepRepository
) : ViewModel() {

    // Holds the user's currently selected tab (Hourly vs Daily)
    private val _selectedTimeFrame = MutableStateFlow(HistoryTimeFrame.HOURLY)

    // Combines database flows with the selected time frame to produce UI State
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StepHistoryState> = combine(
        _selectedTimeFrame,
        stepRepository.getHourlyStepsForToday(),
        stepRepository.getDailyStepsForPastDays(7)
    ) { timeFrame, hourlyList, dailyList ->
        StepHistoryState(
            selectedTimeFrame = timeFrame,
            hourlyData = hourlyList,
            dailyData = dailyList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000), // Retains state for 5s across orientation changes
        initialValue = StepHistoryState()
    )

    // Call this when the user clicks a tab in the UI
    fun onTimeFrameSelected(timeFrame: HistoryTimeFrame) {
        _selectedTimeFrame.value = timeFrame
    }
}

class StepHistoryViewModelFactory(
    private val repository: StepRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StepHistoryViewModel::class.java)) {
            return StepHistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}