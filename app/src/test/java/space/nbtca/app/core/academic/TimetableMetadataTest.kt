package space.nbtca.app.core.academic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.nbtca.app.core.parse.SchoolWeek
import java.time.LocalDate

class TimetableMetadataTest {
    @Test
    fun usesCalendarOfSelectedTermForLegacyAndCodedTerms() {
        val calendar = SchoolWeek(termMondays = mapOf(
            "2026-fall" to "2026-09-14",
            "2026-spring" to "2027-03-01"
        ))
        val autumn = LocalDate.of(2026, 9, 14)
        val spring = LocalDate.of(2027, 3, 1)
        assertEquals(autumn, calendar.firstMonday("2026-2027 · 第一学期"))
        assertEquals(autumn, calendar.firstMonday("2026-2027 · 3"))
        assertEquals(spring, calendar.firstMonday("", "2026", "12"))
        assertNull(calendar.firstMonday("", "2026", "16"))
        assertEquals(10, timetableCurrentWeek(LocalDate.of(2026, 11, 19), autumn, 20))
        assertNull(timetableCurrentWeek(LocalDate.of(2026, 11, 19), spring, 20))
    }

    @Test
    fun parsesWeekDatesAndUsesUpdatedBellTimes() {
        val metadata = TimetableMetadata(
            timetable = """
                {
                  "rqazcList": [
                    {"ZC":"1","XQJ":"1","RQ":"2026-09-14"},
                    {"zc":1,"xqj":2,"rq":"2026-09-16"},
                    {"zc":2,"xqj":1,"rq":"2026-09-21"}
                  ],
                  "xqbzxxszList": [
                    {"jcdm":1,"qssj":"8:05","jssj":"08:50"},
                    {"jcdm":2,"qssj":"8:50","jssj":"09:35"}
                  ]
                }
            """.trimIndent(),
            periodResponse = """
                {"jcList":[{"JCDM":"01","QSSJ":"08:00:00","JSSJ":"08:45"},{"jcdm":2,"qssj":"25:00","jssj":"09:35"}]}
            """.trimIndent()
        )

        assertEquals(listOf(TimetableDay(1, 1, "2026-09-14"), TimetableDay(2, 1, "2026-09-21")), metadata.days)
        assertEquals(TimetablePeriod("08:00", "08:45"), metadata.periods[1])
        assertEquals(TimetablePeriod("08:50", "09:35"), metadata.periods[2])
    }

    @Test
    fun usesTermAnchorForLegacyDataAndHonorsDatesAcrossNewYear() {
        val firstMonday = LocalDate.of(2026, 12, 28)
        val days = listOf(TimetableDay(1, 5, "2027-01-01"), TimetableDay(2, 1, "2027-01-04"))
        assertEquals(firstMonday, timetableWeekOne(days, LocalDate.of(2026, 12, 21)))
        assertEquals(firstMonday, timetableWeekOne(emptyList(), firstMonday))
        assertNull(timetableWeekOne(days + TimetableDay(3, 1, "2027-01-18"), firstMonday))
        assertEquals(2, timetableCurrentWeek(LocalDate.of(2027, 1, 5), firstMonday, 10))
        assertNull(timetableCurrentWeek(LocalDate.of(2026, 12, 27), firstMonday, 10))
    }
}
