package com.pengxh.daily.app.utils

import com.pengxh.kt.lite.extensions.appendZero
import com.pengxh.kt.lite.utils.SaveKeyValues

object ResetTime {

    fun getMinutes(): Int {
        val minutes = SaveKeyValues.getValue(
            Constant.RESET_TIME_MINUTES_KEY,
            -1
        ) as Int
        if (minutes in 0..1439) {
            return minutes
        }

        val hour = SaveKeyValues.getValue(
            Constant.RESET_TIME_KEY,
            Constant.DEFAULT_RESET_HOUR
        ) as Int
        return hour.coerceIn(0, 23) * 60
    }

    fun save(minutes: Int) {
        val safeMinutes = minutes.coerceIn(0, 1439)
        SaveKeyValues.putValue(Constant.RESET_TIME_MINUTES_KEY, safeMinutes)
        SaveKeyValues.putValue(Constant.RESET_TIME_KEY, safeMinutes / 60)
    }

    fun from(hour: Int, minute: Int): Int {
        return hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
    }

    fun format(minutes: Int): String {
        val safeMinutes = minutes.coerceIn(0, 1439)
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60
        return "${hour.appendZero()}:${minute.appendZero()}"
    }

    fun formatDaily(minutes: Int = getMinutes()): String {
        return "每天${format(minutes)}"
    }
}
