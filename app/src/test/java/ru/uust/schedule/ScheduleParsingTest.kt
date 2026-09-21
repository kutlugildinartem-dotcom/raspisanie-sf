package ru.uust.schedule

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.uust.schedule.data.remote.ScheduleApi
import ru.uust.schedule.data.remote.VersionCompare
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.Lesson
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Парсер проверяется на HTML, снятом с живого edu.str.uust.ru,
 * а не на выдуманной разметке — иначе тест разошёлся бы с реальностью сайта.
 */
class ScheduleParsingTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ScheduleApi

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ScheduleApi(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `разбирает неделю из шести учебных дней`() {
        server.enqueue(MockResponse().setBody(fixture("schedule_pmi21.html")))

        val week = api.getWeek(groupId = 13, weekOffset = 0)

        assertEquals(6, week.days.size)
        assertEquals("2026-09-21", week.days.first().isoDate)
        assertEquals("2026-09-26", week.days.last().isoDate)
        assertEquals("Понедельник", week.days.first().dayName)
        // Сайт всегда отдаёт восемь слотов на день, включая пустые.
        assertTrue(week.days.all { it.lessons.size == 8 })
    }

    @Test
    fun `разбирает поля пары полностью`() {
        server.enqueue(MockResponse().setBody(fixture("schedule_pmi21.html")))

        val monday = api.getWeek(13, 0).days.first()
        val first = monday.realLessons.first()

        assertEquals(2, first.number)
        assertEquals("Базы данных", first.subject)
        assertEquals("Лек", first.type)
        assertEquals("Хусаинова Г.Я.", first.teacher)
        assertEquals(10 * 60 + 10, first.startMin)
        assertEquals(11 * 60 + 40, first.endMin)
        assertEquals("10:10 - 11:40", first.timeRange)
    }

    @Test
    fun `убирает эмодзи-пиктограммы из номера аудитории`() {
        server.enqueue(MockResponse().setBody(fixture("schedule_pmi21.html")))

        val rooms = api.getWeek(13, 0).days
            .flatMap { it.realLessons }
            .map { it.room }
            .filter { it.isNotBlank() }

        assertTrue(rooms.isNotEmpty())
        // В исходном HTML аудитории приходят как "206-ФМИТ &#128421;&#65039;".
        assertTrue(rooms.any { it == "206-ФМИТ" })
        assertFalse(rooms.any { it.contains("🖥") || it.contains("️") })
    }

    @Test
    fun `пустые слоты не попадают в список пар`() {
        server.enqueue(MockResponse().setBody(fixture("schedule_pmi21.html")))

        val monday = api.getWeek(13, 0).days.first()

        assertEquals(8, monday.lessons.size)
        assertEquals(4, monday.realLessons.size)
        assertTrue(monday.realLessons.all { it.subject.isNotBlank() && it.number > 0 })
    }

    @Test
    fun `разбирает справочник групп с идентификаторами`() {
        server.enqueue(MockResponse().setBody(fixture("groups_fac7.html")))

        val groups = api.getGroups(facultyId = 7, facultyName = "ФМиИТ")

        assertTrue(groups.size > 10)
        val pmi21 = groups.firstOrNull { it.name == "ПМИ21" }
        assertNotNull(pmi21)
        assertEquals(13, pmi21!!.id)
        assertEquals(7, pmi21.facultyId)
        assertTrue(groups.all { it.id > 0 && it.name.isNotBlank() })
    }

    @Test
    fun `следующая неделя разбирается той же разметкой`() {
        server.enqueue(MockResponse().setBody(fixture("schedule_week1.html")))

        val week = api.getWeek(13, 1)

        assertEquals(6, week.days.size)
        assertEquals("2026-09-28", week.days.first().isoDate)
        assertEquals("2026-10-03", week.days.last().isoDate)
    }

    @Test
    fun `время пары разбирается из разных тире`() {
        assertEquals(610 to 700, Lesson.parseTime("10:10 - 11:40"))
        assertEquals(610 to 700, Lesson.parseTime("10:10–11:40"))
        assertEquals(-1 to -1, Lesson.parseTime("по расписанию"))
    }
}

class DayLogicTest {

    @Test
    fun `до часа переключения показывается сегодня`() {
        val now = LocalDateTime.of(2026, 9, 21, 9, 30) // понедельник
        assertEquals(LocalDate.of(2026, 9, 21), DayLogic.defaultDate(now, switchHour = 17))
    }

    @Test
    fun `после часа переключения показывается завтра`() {
        val now = LocalDateTime.of(2026, 9, 21, 17, 0)
        assertEquals(LocalDate.of(2026, 9, 22), DayLogic.defaultDate(now, switchHour = 17))
    }

    @Test
    fun `вечер субботы перескакивает через воскресенье на понедельник`() {
        val saturdayEvening = LocalDateTime.of(2026, 9, 26, 19, 0)
        assertEquals(LocalDate.of(2026, 9, 28), DayLogic.defaultDate(saturdayEvening, 17))
    }

    @Test
    fun `воскресенье само по себе показывает понедельник`() {
        val sundayMorning = LocalDateTime.of(2026, 9, 27, 8, 0)
        assertEquals(LocalDate.of(2026, 9, 28), DayLogic.defaultDate(sundayMorning, 17))
    }

    @Test
    fun `листание вперёд пропускает воскресенье`() {
        val saturday = LocalDate.of(2026, 9, 26)
        assertEquals(LocalDate.of(2026, 9, 28), DayLogic.shift(saturday, 1))
    }

    @Test
    fun `листание назад пропускает воскресенье`() {
        val monday = LocalDate.of(2026, 9, 28)
        assertEquals(LocalDate.of(2026, 9, 26), DayLogic.shift(monday, -1))
    }

    @Test
    fun `листание на несколько дней остаётся в учебной неделе`() {
        val monday = LocalDate.of(2026, 9, 21)
        // 6 учебных дней от понедельника — это следующий понедельник, а не воскресенье.
        assertEquals(LocalDate.of(2026, 9, 28), DayLogic.shift(monday, 6))
    }

    @Test
    fun `обратный отсчёт до пары читается по-русски`() {
        assertEquals("через 45 мин", DayLogic.formatCountdown(45))
        assertEquals("через 1 ч", DayLogic.formatCountdown(60))
        assertEquals("через 2 ч 15 мин", DayLogic.formatCountdown(135))
        assertEquals("идёт", DayLogic.formatCountdown(-5))
    }
}

/**
 * Сравнение версий: строковое сравнение здесь даёт неверный результат,
 * поэтому проверяем именно числовую логику по сегментам.
 */
class VersionCompareTest {

    @Test
    fun `большая минорная версия новее`() {
        assertTrue(VersionCompare.isNewer("1.1", "1.0"))
        assertFalse(VersionCompare.isNewer("1.0", "1.1"))
    }

    @Test
    fun `двузначный сегмент не проигрывает однозначному`() {
        // Лексикографически "1.10" < "1.9", численно — наоборот.
        assertTrue(VersionCompare.isNewer("1.10", "1.9"))
        assertFalse(VersionCompare.isNewer("1.9", "1.10"))
    }

    @Test
    fun `одинаковые версии не считаются новее`() {
        assertFalse(VersionCompare.isNewer("1.0", "1.0"))
        assertFalse(VersionCompare.isNewer("v1.0", "1.0"))
    }

    @Test
    fun `недостающие сегменты считаются нулями`() {
        assertFalse(VersionCompare.isNewer("1.0", "1.0.0"))
        assertTrue(VersionCompare.isNewer("1.0.1", "1.0"))
        assertTrue(VersionCompare.isNewer("2", "1.9.9"))
    }

    @Test
    fun `префикс v игнорируется`() {
        assertTrue(VersionCompare.isNewer("v2.0", "1.9"))
    }
}
