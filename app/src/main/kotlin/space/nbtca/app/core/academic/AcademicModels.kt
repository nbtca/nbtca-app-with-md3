package space.nbtca.app.core.academic

import kotlinx.serialization.Serializable

@Serializable
data class AcademicTerm(
    val academicYear: String = "",
    val semester: String = "",
    val yearLabel: String = "",
    val semesterLabel: String = ""
) {
    val key: String get() = "$academicYear:$semester"
    val label: String get() = "$yearLabel · $semesterLabel"
}

@Serializable
data class TimetableCourse(
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val weekday: Int = 0,
    val startPeriod: Int = 0,
    val endPeriod: Int = 0,
    val weeks: List<Int> = emptyList()
) {
    val periodText: String
        get() = if (startPeriod == endPeriod) "第 ${startPeriod} 节" else "第 $startPeriod-$endPeriod 节"
}

@Serializable
data class TimetablePeriod(val start: String, val end: String)

@Serializable
data class TimetableDay(val week: Int, val weekday: Int, val date: String)

@Serializable
data class ExamItem(
    val courseName: String = "",
    val examName: String = "",
    val timeText: String = "",
    val location: String = "",
    val seat: String = "",
    val assessment: String = ""
)

@Serializable
data class GradeItem(
    val courseCode: String = "",
    val courseName: String = "",
    val grade: String = "",
    val credits: String = "",
    val gradePoint: String = "",
    val nature: String = "",
    val remark: String = "",
    val voided: Boolean = false
)

@Serializable
data class AcademicSnapshot(
    val studentId: String = "",
    val termLabel: String = "",
    val timetable: List<TimetableCourse> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val grades: List<GradeItem> = emptyList(),
    val updatedAt: Long = 0L,
    val timetablePeriods: Map<Int, TimetablePeriod> = emptyMap(),
    val timetableDays: List<TimetableDay> = emptyList(),
    val term: AcademicTerm? = null
) {
    val termKey: String get() = term?.key ?: "legacy:$termLabel"

    internal fun matchesTerm(other: AcademicSnapshot): Boolean =
        if (term != null && other.term != null) term.key == other.term.key
        else termLabel.trim() == other.termLabel.trim()
}
