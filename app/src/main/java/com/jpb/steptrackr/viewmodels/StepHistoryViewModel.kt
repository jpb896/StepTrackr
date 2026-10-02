package com.jpb.steptrackr.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jpb.steptrackr.services.HealthConnectRepository
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class StepHistoryViewModel(
    private val stepRepository: StepRepository,
    private val healthConnectRepository: HealthConnectRepository? = null
) : ViewModel() {

    private val today = LocalDate.now()
    private val currentHour = LocalTime.now().hour

    private val _selectedTimeFrame = MutableStateFlow(HistoryTimeFrame.HOURLY)
    private val _selectedHourlyDate = MutableStateFlow(today)
    private val _selectedDailyRange = MutableStateFlow(Pair(today.minusDays(6), today))
    private val _selectedHcTime = MutableStateFlow(Pair(today, currentHour))
    private val _hcData = MutableStateFlow<List<StepDataPoint>>(emptyList())

    init {
        fetchHealthConnectData(today, currentHour)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StepHistoryState> = combine(
        _selectedTimeFrame,
        _selectedHourlyDate.flatMapLatest { date -> stepRepository.getHourlyStepsForDate(date) },
        _selectedDailyRange.flatMapLatest { (start, end) -> stepRepository.getDailyStepsForRange(start, end) },
        _hcData,
        _selectedHcTime
    ) { timeFrame, hourlyList, dailyList, hcList, (hcDate, hcHour) ->
        StepHistoryState(
            selectedTimeFrame = timeFrame,
            hourlyData = hourlyList,
            dailyData = dailyList,
            healthConnectData = hcList,
            selectedHealthConnectDate = hcDate,
            selectedHealthConnectHour = hcHour
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StepHistoryState()
    )

    fun onTimeFrameSelected(timeFrame: HistoryTimeFrame) {
        _selectedTimeFrame.value = timeFrame
        if (timeFrame == HistoryTimeFrame.HEALTH_CONNECT_HOURLY) {
            val (date, hour) = _selectedHcTime.value
            fetchHealthConnectData(date, hour)
        }
    }

    fun onHourlyDateSelected(date: LocalDate) {
        _selectedHourlyDate.value = date
    }

    fun onDailyRangeSelected(startDate: LocalDate, endDate: LocalDate) {
        _selectedDailyRange.value = Pair(startDate, endDate)
    }

    fun onHealthConnectDateAndHourSelected(date: LocalDate, hour: Int) {
        _selectedHcTime.value = Pair(date, hour)
        fetchHealthConnectData(date, hour)
    }

    fun fetchHealthConnectData(date: LocalDate, hour: Int) {
        viewModelScope.launch {
            healthConnectRepository?.let { repo ->
                _hcData.value = repo.getHealthConnectStepsForHour(date, hour)
            }
        }
    }

    fun refreshHealthConnectData() {
        val (date, hour) = _selectedHcTime.value
        fetchHealthConnectData(date, hour)
    }
}

class StepHistoryViewModelFactory(
    private val repository: StepRepository,
    private val healthConnectRepository: HealthConnectRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StepHistoryViewModel::class.java)) {
            return StepHistoryViewModel(repository, healthConnectRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}