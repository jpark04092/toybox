package com.jpark.alarmcard.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Auto-enable 요일 bitmask 규칙과 다음 실행 시각 계산을 한 곳에서 관리한다.
 *
 * Bitmask 규칙은 UI/DB/Worker 공통으로 다음과 같다.
 * - 월: 1 shl 1
 * - 화: 1 shl 2
 * - 수: 1 shl 3
 * - 목: 1 shl 4
 * - 금: 1 shl 5
 * - 토: 1 shl 6
 * - 일: 1 shl 7
 */
object AutoEnableSchedule {
    const val MONDAY_BIT = 1 shl 1
    const val TUESDAY_BIT = 1 shl 2
    const val WEDNESDAY_BIT = 1 shl 3
    const val THURSDAY_BIT = 1 shl 4
    const val FRIDAY_BIT = 1 shl 5
    const val SATURDAY_BIT = 1 shl 6
    const val SUNDAY_BIT = 1 shl 7

    const val VALID_DAYS_MASK = MONDAY_BIT or TUESDAY_BIT or WEDNESDAY_BIT or THURSDAY_BIT or
        FRIDAY_BIT or SATURDAY_BIT or SUNDAY_BIT

    fun dayBit(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> MONDAY_BIT
        Calendar.TUESDAY -> TUESDAY_BIT
        Calendar.WEDNESDAY -> WEDNESDAY_BIT
        Calendar.THURSDAY -> THURSDAY_BIT
        Calendar.FRIDAY -> FRIDAY_BIT
        Calendar.SATURDAY -> SATURDAY_BIT
        Calendar.SUNDAY -> SUNDAY_BIT
        else -> 0
    }

    fun hasSelectedDay(daysMask: Int): Boolean = (daysMask and VALID_DAYS_MASK) != 0

    fun isSelected(calendarDayOfWeek: Int, daysMask: Int): Boolean =
        (daysMask and dayBit(calendarDayOfWeek)) != 0

    /**
     * nowMillis 이후에 도래하는, 선택된 요일 중 가장 가까운 HH:mm 시각을 계산한다.
     * 선택 요일이 없거나 시간이 유효하지 않으면 null을 반환한다.
     */
    fun nextRunTimeMillis(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        daysMask: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (hour !in 0..23 || minute !in 0..59) return null
        if (!hasSelectedDay(daysMask)) return null

        val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }

        for (dayOffset in 0..7) {
            val candidate = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                add(Calendar.DATE, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (candidate.after(now) && isSelected(candidate.get(Calendar.DAY_OF_WEEK), daysMask)) {
                return candidate.timeInMillis
            }
        }

        return null
    }
}