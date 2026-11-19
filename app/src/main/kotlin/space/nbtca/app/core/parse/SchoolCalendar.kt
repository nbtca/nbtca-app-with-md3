package space.nbtca.app.core.parse

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Serializable
data class SchoolWeek(
    val current: Int = 1,
    val total: Int = TOTAL_WEEKS,
    val termMondays: Map<String, String>? = null
) {
    fun firstMonday(termLabel: String, yearCode: String? = null, semesterCode: String? = null): LocalDate? {
        val academicYear = yearCode?.takeIf { it.matches(Regex("20\\d{2}")) }
            ?: Regex("(?<!\\d)20\\d{2}(?!\\d)").find(termLabel)?.value ?: return null
        val semester = when (semesterCode ?: termLabel.substringAfterLast('·').trim()) {
            "3", "1", "第一学期", "第1学期", "秋季学期" -> "fall"
            "12", "2", "第二学期", "第2学期", "春季学期" -> "spring"
            else -> return null
        }
        return termMondays?.get("$academicYear-$semester")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    companion object {
        const val TOTAL_WEEKS = 20
        val EMPTY = SchoolWeek(1, TOTAL_WEEKS)
    }
}

/**
 * 从 school.ics 计算当前教学周。逻辑与旧 BFF 的 CalendarService 保持一致：
 * · 9 月—次年 1 月为秋季学期，2 月—8 月为春季学期；
 * · 以「秋季/春季学期开始上课」事件为第 1 周起点；
 * · 找不到事件时回退到 9 月 1 日 / 3 月 1 日；
 * · 总周数固定 20，周次夹在 1..20。
 */
object SchoolCalendar {
    private val china: ZoneId = ZoneId.of("Asia/Shanghai")

    fun currentWeek(
        events: List<IcsEvent>,
        now: ZonedDateTime = ZonedDateTime.now(china)
    ): SchoolWeek {
        val today = now.withZoneSameInstant(china).toLocalDate()
        val isSpring = today.monthValue in 2..8
        val semester = if (isSpring) "春季学期" else "秋季学期"
        val startYear = if (today.monthValue < 9) today.year - 1 else today.year

        val start = findTermStart(events, today, semester, startYear)
            ?: if (isSpring) LocalDate.of(startYear + 1, 3, 1)
            else LocalDate.of(startYear, 9, 1)

        val days = ChronoUnit.DAYS.between(start, today)
        val current = (days / 7 + 1).toInt().coerceIn(1, SchoolWeek.TOTAL_WEEKS)
        val mondays = events.mapNotNull { event ->
            val date = event.date ?: return@mapNotNull null
            val key = when (event.title.trim()) {
                "秋季学期开始上课" -> "${date.year}-fall"
                "春季学期开始上课" -> "${date.year - 1}-spring"
                else -> return@mapNotNull null
            }
            key to date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        }.groupBy({ it.first }, { it.second }).mapValues { (_, dates) -> dates.minOrNull().orEmpty() }
        return SchoolWeek(current, SchoolWeek.TOTAL_WEEKS, mondays)
    }

    private fun findTermStart(
        events: List<IcsEvent>,
        today: LocalDate,
        semester: String,
        startYear: Int
    ): LocalDate? {
        val wanted = if (semester == "春季学期") "春季学期开始上课" else "秋季学期开始上课"
        val rangeStart = LocalDate.of(startYear, 9, 1)
        val rangeEnd = rangeStart.plusYears(1)
        return events.asSequence()
            .filter { it.title.trim() == wanted }
            .mapNotNull { it.date }
            .filter { date ->
                date >= rangeStart && date < rangeEnd && !date.isAfter(today)
            }
            .maxOrNull()
    }
}
