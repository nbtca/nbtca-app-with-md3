package space.nbtca.app.core.academic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal const val TIMETABLE_PERIODS_PATH = "/jwglxt/kbcx/xskbcx_cxRjc.html"

internal class TimetableMetadata(timetable: String, periodResponse: String?) {
    private val root = parse(timetable) as? JsonObject
    val periods = readPeriods(root?.get("xqbzxxszList")) + readPeriods(parse(periodResponse))
    val days = rows(root?.get("rqazcList")).mapNotNull { row ->
        val week = row.text("zc")?.toIntOrNull()
        val weekday = row.text("xqj")?.toIntOrNull()
        val date = row.text("rq")?.toTimetableDate()
        if (week == null || week !in 1..60 || date == null || weekday != date.dayOfWeek.value) null
        else TimetableDay(week, weekday, date.toString())
    }.distinct().groupBy { it.week to it.weekday }.values.mapNotNull { it.singleOrNull() }

    private fun parse(raw: String?) = raw?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }

    private fun rows(raw: JsonElement?): List<JsonObject> {
        val list = when (raw) {
            is JsonArray -> raw
            is JsonObject -> (raw["data"] as? JsonArray) ?: (raw["jcList"] as? JsonArray)
            else -> null
        }
        return list.orEmpty().filterIsInstance<JsonObject>()
    }

    private fun readPeriods(raw: JsonElement?): Map<Int, TimetablePeriod> =
        rows(raw).mapNotNull { row ->
            val label = row.text("jcdm") ?: row.text("jc") ?: row.text("jcmc").orEmpty()
            val number = periodNumber.find(label)?.value?.toInt()
            val start = bellTime(row.text("qssj"))
            val end = bellTime(row.text("jssj"))
            if (number == null || number !in 1..99 || start == null || end == null || start >= end) null
            else number to TimetablePeriod(start.format(hourMinute), end.format(hourMinute))
        }.toMap()

    private fun JsonObject.text(key: String): String? =
        ((get(key) ?: get(key.uppercase())) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun bellTime(text: String?): LocalTime? {
        val match = text?.let(clockFormat::matchEntire) ?: return null
        val (hour, minute, second) = match.destructured
        return runCatching { LocalTime.of(hour.toInt(), minute.toInt(), second.toIntOrNull() ?: 0) }.getOrNull()
    }

    private companion object {
        val periodNumber = Regex("\\d{1,2}")
        val clockFormat = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
        val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
    }
}

internal fun String.toTimetableDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

/** A week-one anchor can fill dates only when the known dates agree with it. */
internal fun timetableWeekOne(days: List<TimetableDay>, calendarMonday: LocalDate?): LocalDate? {
    val anchors = days.mapNotNull { day ->
        day.date.toTimetableDate()?.minusWeeks((day.week - 1).toLong())?.minusDays((day.weekday - 1).toLong())
    }.distinct()
    return if (anchors.isEmpty()) calendarMonday else anchors.singleOrNull()
}

internal fun timetableCurrentWeek(today: LocalDate, firstMonday: LocalDate?, totalWeeks: Int): Int? =
    firstMonday?.takeUnless(today::isBefore)
        ?.let { (ChronoUnit.WEEKS.between(it, today) + 1).toInt() }
        ?.takeIf { it in 1..totalWeeks }
