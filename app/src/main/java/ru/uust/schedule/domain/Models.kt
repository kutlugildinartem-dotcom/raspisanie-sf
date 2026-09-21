package ru.uust.schedule.domain

import kotlinx.serialization.Serializable

/** Одна пара. [number] — номер слота 1..8, 0 если слот пустой. */
@Serializable
data class Lesson(
    val number: Int,
    val type: String,
    val subject: String,
    val room: String,
    val teacher: String,
    val teacherId: Int? = null,
    /** Минуты от полуночи. -1 если время не удалось разобрать. */
    val startMin: Int = -1,
    val endMin: Int = -1,
) {
    val timeRange: String
        get() = if (startMin < 0) "" else "${fmt(startMin)} - ${fmt(endMin)}"

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    companion object {
        fun parseTime(raw: String): Pair<Int, Int> {
            val m = Regex("""(\d{1,2}):(\d{2})\s*[-–—]\s*(\d{1,2}):(\d{2})""").find(raw)
                ?: return -1 to -1
            val (h1, m1, h2, m2) = m.destructured
            return (h1.toInt() * 60 + m1.toInt()) to (h2.toInt() * 60 + m2.toInt())
        }
    }
}

/** День расписания. [isoDate] в формате yyyy-MM-dd — так он сортируется лексикографически. */
@Serializable
data class DaySchedule(
    val isoDate: String,
    val dayName: String,
    val lessons: List<Lesson>,
) {
    /** Только непустые пары, по порядку. */
    val realLessons: List<Lesson> get() = lessons.filter { it.subject.isNotBlank() }
}

@Serializable
data class WeekSchedule(
    val groupId: Int,
    val weekOffset: Int,
    val days: List<DaySchedule>,
)

/** Группа из справочника сайта. */
@Serializable
data class Group(
    val id: Int,
    val name: String,
    val facultyId: Int,
    val facultyName: String,
)

data class Faculty(val id: Int, val name: String)

val FACULTIES = listOf(
    Faculty(7, "Математика и ИТ"),
    Faculty(4, "Филологический"),
    Faculty(5, "Башкирская и тюркская филология"),
    Faculty(6, "Исторический"),
    Faculty(8, "Естественнонаучный"),
    Faculty(9, "Педагогика и психология"),
    Faculty(10, "Экономический"),
    Faculty(18, "Юридический"),
    Faculty(26, "Колледж"),
    Faculty(27, "Физическая культура"),
    Faculty(127, "Аспирантура"),
)
