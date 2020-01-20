package com.bradope.blinkactivator.blink

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HoursAndMins(val hours: Int, val minutes: Int) {

    companion object {
        fun now(): HoursAndMins {
            val nowString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val nums = nowString.split(":")
            return HoursAndMins(nums.get(0).toInt(), nums.get(1).toInt())
        }
    }


    fun isGivenTimeBeforeMyTime(other: HoursAndMins): Boolean {
        if (other.hours < hours) return true
        return other.minutes < minutes
    }

    fun isGivenTimeAfterOrEqualToMyTime(other: HoursAndMins): Boolean {
        if (other.hours > hours) return true
        return other.hours >= hours
    }
}