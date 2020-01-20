package com.bradope.blinkactivator.blink

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface HoursAndMinsFactory {
    fun now(): HoursAndMins
}
class DefaultHoursAndMinsFactory: HoursAndMinsFactory {
   override fun now(): HoursAndMins {
        val nowString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val nums = nowString.split(":")
        return HoursAndMins(nums.get(0).toInt(), nums.get(1).toInt())
    }
}

data class HoursAndMins(val hours: Int, val minutes: Int) {

    companion object {
        fun compare(first: HoursAndMins, second: HoursAndMins): Int {
            if (first.hours > second.hours) return -1
            if (second.hours > first.hours) return 1
            if (first.minutes > second.minutes) return -1
            if (second.minutes > first.minutes) return 1
            return 0
        }
    }
}