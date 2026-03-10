package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.model.HorizontalCalendar
import java.util.Calendar

class HomeViewModel : ViewModel() {
    var calendarItems by mutableStateOf<List<HorizontalCalendar>>(emptyList())
    var dashboards by mutableStateOf<List<HomeDashboard>>(emptyList())
    var scrollToDay by mutableIntStateOf(1)

    fun loadData() {
        val totalDays = getTotalDaysOfMonth()
        val currentDay = getCurrentDay()
        scrollToDay = maxOf(1, currentDay - 3)
        calendarItems = MockDataProvider.calendarItems(totalDays, currentDay)
        dashboards = MockDataProvider.dashboards
    }

    private fun getTotalDaysOfMonth(): Int {
        val calendar = Calendar.getInstance()
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun getCurrentDay(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    }
}
