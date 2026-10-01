package com.jpb.steptrackr.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jpb.steptrackr.utils.HistoryTimeFrame
import com.jpb.steptrackr.utils.StepHistoryState
import com.jpb.steptrackr.utils.StepRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StepHistoryViewModel(
    private val stepRepository: StepRepository
) : ViewModel() {

    private val today = LocalDate.now()

    private val _selectedTimeFrame = MutableStateFlow(HistoryTimeFrame.HOURLY)
    private val _selectedHourlyDate = MutableStateFlow(today)
    private val _selectedDailyRange = MutableStateFlow(Pair(today.minusDays(6), today)) // Default last 7 days

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StepHistoryState> = combine(
        _selectedTimeFrame,
        _selectedHourlyDate.flatMapLatest { date -> stepRepository.getHourlyStepsForDate(date) },
        _selectedDailyRange.flatMapLatest { (start, end) -> stepRepository.getDailyStepsForRange(start, end) }
    ) { timeFrame, hourlyList, dailyList ->
        StepHistoryState(
            selectedTimeFrame = timeFrame,
            hourlyData = hourlyList,
            dailyData = dailyList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StepHistoryState()
    )

    fun onTimeFrameSelected(timeFrame: HistoryTimeFrame) {
        _selectedTimeFrame.value = timeFrame
    }

    fun onHourlyDateSelected(date: LocalDate) {
        _selectedHourlyDate.value = date
    }

    fun onDailyRangeSelected(startDate: LocalDate, endDate: LocalDate) {
        _selectedDailyRange.value = Pair(startDate, endDate)
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