package ru.uust.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.uust.schedule.domain.Lesson

class LessonTimeLabelTest {

    private fun lesson(start: Int, end: Int) = Lesson(
        number = 1, type = "Лек", subject = "Физика", room = "305", teacher = "",
        startMin = start, endMin = end,
    )

    @Test
    fun startOnly() {
        assertEquals("10:10", lesson(10 * 60 + 10, 11 * 60 + 40).timeLabel(range = false))
    }

    @Test
    fun range() {
        assertEquals("08:30–10:00", lesson(8 * 60 + 30, 10 * 60).timeLabel(range = true))
    }

    @Test
    fun unknownTimeIsBlank() {
        assertEquals("", lesson(-1, -1).timeLabel(range = true))
    }

    @Test
    fun missingEndFallsBackToStart() {
        assertEquals("09:00", lesson(9 * 60, -1).timeLabel(range = true))
    }
}
