package com.example.mts.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatPrice(value: Long): String = "%,d".format(value)

fun formatSigned(value: Long): String = (if (value > 0) "+" else "") + "%,d".format(value)

fun formatRate(rate: Double): String = "%+.2f%%".format(rate)

/** "▲ 1,200 +1.71%" 형태의 등락 표시 */
fun formatChange(change: Long, rate: Double): String {
    val arrow = when {
        change > 0 -> "▲"
        change < 0 -> "▼"
        else -> "-"
    }
    return "$arrow ${formatPrice(abs(change))}  ${formatRate(rate)}"
}

fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
