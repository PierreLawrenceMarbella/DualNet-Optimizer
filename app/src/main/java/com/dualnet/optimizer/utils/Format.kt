package com.dualnet.optimizer.utils

import java.util.Locale
import java.time.ZonedDateTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import com.dualnet.optimizer.data.UsagePeriod

fun speed(value: Double?): String = value?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
fun bytes(value: Long?): String = when {
    value == null -> "—"
    value >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", value / 1_000_000_000.0)
    value >= 1_000_000 -> String.format(Locale.US, "%.1f MB", value / 1_000_000.0)
    value >= 1000 -> String.format(Locale.US, "%.1f KB", value / 1000.0)
    else -> "$value B"
}
fun duration(seconds: Long): String = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
fun periodStart(period: UsagePeriod, now: ZonedDateTime = ZonedDateTime.now()): Long {
    val date = when (period) {
        UsagePeriod.TODAY -> now.toLocalDate()
        UsagePeriod.WEEK -> now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        UsagePeriod.MONTH -> now.toLocalDate().withDayOfMonth(1)
    }
    return date.atStartOfDay(now.zone).toInstant().toEpochMilli()
}
fun throughput(bytes: Long, nanos: Long): Double = if (bytes < 0 || nanos <= 0) 0.0 else bytes * 8000.0 / nanos
fun signalQuality(dbm: Int?, wifi: Boolean): String = when {
    dbm == null -> "Unavailable"
    dbm >= if (wifi) -55 else -85 -> "Excellent"
    dbm >= if (wifi) -67 else -100 -> "Good"
    dbm >= if (wifi) -75 else -110 -> "Fair"
    else -> "Weak"
}
fun validHost(host: String): Boolean = host.length in 1..253 && host.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?")) && !host.contains("..")
fun validIpv4(address: String): Boolean = address.split('.').let { it.size == 4 && it.all { p -> p.isNotEmpty() && p.all(Char::isDigit) && p.toIntOrNull() in 0..255 } }
