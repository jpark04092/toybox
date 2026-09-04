package com.jpark.alarmcard.notify

import com.jpark.alarmcard.domain.model.AutoEnableSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AutoEnableScheduleTest {
    private val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = this@AutoEnableScheduleTest.timeZone
    }

    @Test
    fun dayBit_usesUiBitmaskContract() {
        assertEquals(1 shl 1, AutoEnableSchedule.dayBit(Calendar.MONDAY))
        assertEquals(1 shl 2, AutoEnableSchedule.dayBit(Calendar.TUESDAY))
        assertEquals(1 shl 3, AutoEnableSchedule.dayBit(Calendar.WEDNESDAY))
        assertEquals(1 shl 4, AutoEnableSchedule.dayBit(Calendar.THURSDAY))
        assertEquals(1 shl 5, AutoEnableSchedule.dayBit(Calendar.FRIDAY))
        assertEquals(1 shl 6, AutoEnableSchedule.dayBit(Calendar.SATURDAY))
        assertEquals(1 shl 7, AutoEnableSchedule.dayBit(Calendar.SUNDAY))
    }

    @Test
    fun nextRunTimeMillis_whenTodaySelectedAndTimeIsFuture_returnsToday() {
        val now = millis("2026-09-07 07:50") // Monday
        val next = AutoEnableSchedule.nextRunTimeMillis(
            nowMillis = now,
            hour = 8,
            minute = 0,
            daysMask = AutoEnableSchedule.MONDAY_BIT,
            timeZone = timeZone
        )

        assertEquals("2026-09-07 08:00", text(next))
    }

    @Test
    fun nextRunTimeMillis_whenTodaySelectedButTimePassed_returnsNextSelectedWeekday() {
        val now = millis("2026-09-07 08:01") // Monday
        val next = AutoEnableSchedule.nextRunTimeMillis(
            nowMillis = now,
            hour = 8,
            minute = 0,
            daysMask = AutoEnableSchedule.MONDAY_BIT,
            timeZone = timeZone
        )

        assertEquals("2026-09-14 08:00", text(next))
    }

    @Test
    fun nextRunTimeMillis_skipsUnselectedDays() {
        val now = millis("2026-09-07 07:50") // Monday
        val next = AutoEnableSchedule.nextRunTimeMillis(
            nowMillis = now,
            hour = 8,
            minute = 0,
            daysMask = AutoEnableSchedule.WEDNESDAY_BIT,
            timeZone = timeZone
        )

        assertEquals("2026-09-09 08:00", text(next))
    }

    @Test
    fun nextRunTimeMillis_whenNoDaySelected_returnsNull() {
        val next = AutoEnableSchedule.nextRunTimeMillis(
            nowMillis = millis("2026-09-07 07:50"),
            hour = 8,
            minute = 0,
            daysMask = 0,
            timeZone = timeZone
        )

        assertNull(next)
    }

    private fun millis(value: String): Long = format.parse(value)!!.time

    private fun text(millis: Long?): String = format.format(millis!!)
}