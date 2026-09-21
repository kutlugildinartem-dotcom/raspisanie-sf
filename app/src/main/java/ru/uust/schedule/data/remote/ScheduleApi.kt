package ru.uust.schedule.data.remote

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.domain.Group
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.domain.WeekSchedule
import java.util.concurrent.TimeUnit

/**
 * Клиент внутреннего API edu.str.uust.ru.
 *
 * Сайт отдаёт HTML-фрагменты, а не JSON, поэтому ответы разбираются Jsoup.
 * Разметка стабильная: каждый день — div.day, внутри ровно 8 слотов li.lesson,
 * пустой слот содержит только div.number с пробелом.
 */
class ScheduleApi(
    private val client: OkHttpClient = defaultClient(),
    private val base: String = BASE,
) {

    /** Расписание группы на неделю. [weekOffset]: 0 — текущая, -1 — прошлая, 1 — следующая. */
    fun getWeek(groupId: Int, weekOffset: Int): WeekSchedule {
        val html = post(
            "$base/php/getShedule.php",
            mapOf("type" to "2", "id" to groupId.toString(), "week" to weekOffset.toString()),
        )
        return WeekSchedule(groupId, weekOffset, parseDays(html))
    }

    /** Справочник групп факультета. */
    fun getGroups(facultyId: Int, facultyName: String): List<Group> {
        val html = get("$base/php/getList.php?faculty=$facultyId")
        return Jsoup.parse(html).select("li").mapNotNull { li ->
            val a = li.selectFirst("a") ?: return@mapNotNull null
            val name = a.text().trim()
            // id лежит в скрытом div рядом со ссылкой, а также в onClick — берём первое доступное
            val id = li.selectFirst("div")?.text()?.trim()?.toIntOrNull()
                ?: GROUP_ID_IN_ONCLICK.find(a.attr("onClick"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            if (name.isEmpty() || id <= 0) null
            else Group(id = id, name = name, facultyId = facultyId, facultyName = facultyName)
        }.distinctBy { it.id }
    }

    /** Поиск id группы по точному имени. Возвращает null, если сайт ответил 0. */
    fun findGroupId(groupName: String): Int? =
        post("$base/php/getIdGroup.php", mapOf("group_name" to groupName))
            .trim().toIntOrNull()?.takeIf { it > 0 }

    // --- разбор HTML ---

    private fun parseDays(html: String): List<DaySchedule> =
        Jsoup.parse(html).select("div.day").mapNotNull { day ->
            val header = day.selectFirst("h2")?.text()?.trim().orEmpty()
            val m = DAY_HEADER.find(header) ?: return@mapNotNull null
            val dayName = m.groupValues[1]
            val (d, mo, y) = m.destructured.toList().drop(1)
            val iso = "%04d-%02d-%02d".format(y.toInt(), mo.toInt(), d.toInt())
            DaySchedule(
                isoDate = iso,
                dayName = dayName.replaceFirstChar { it.uppercase() },
                lessons = day.select("li.lesson").map(::parseLesson),
            )
        }

    private fun parseLesson(li: Element): Lesson {
        val number = li.selectFirst("div.number")?.text()?.trim()
            ?.removeSuffix(".")?.toIntOrNull() ?: 0
        val subject = li.selectFirst("div.name")?.text()?.trim().orEmpty()
        if (subject.isEmpty()) return Lesson(0, "", "", "", "")

        // div.type содержит вложенный скрытый div с id занятия — нужен только собственный текст
        val type = li.selectFirst("div.type")?.ownText()?.trim().orEmpty()
        val room = li.select("div.cab li").joinToString(" / ") { cleanRoom(it.text()) }
        val teacherLinks = li.select("div.prep a")
        val teacher = teacherLinks.joinToString(" / ") { it.text().trim() }
        val teacherId = teacherLinks.firstOrNull()
            ?.let { TEACHER_ID_IN_ONCLICK.find(it.attr("onClick"))?.groupValues?.get(1)?.toIntOrNull() }
        val (start, end) = Lesson.parseTime(li.selectFirst("div.time")?.text().orEmpty())

        return Lesson(number, type, subject, room, teacher, teacherId, start, end)
    }

    /** Сайт дописывает к аудитории эмодзи-пиктограммы (🖥️ и т.п.) — они мешают в узком виджете. */
    private fun cleanRoom(raw: String): String =
        raw.replace(Regex("""[\p{So}\p{Cn}️]"""), "").trim()

    // --- транспорт ---

    private fun get(url: String): String = execute(Request.Builder().url(url).build())

    private fun post(url: String, fields: Map<String, String>): String {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private fun execute(request: Request): String =
        client.newCall(request.newBuilder().header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) throw ScheduleException("Сервер ответил ${r.code}")
            r.body?.string().orEmpty()
        }

    companion object {
        const val BASE = "https://edu.str.uust.ru"
        private const val UA = "UustSchedule/1.0 (Android)"
        private val DAY_HEADER = Regex("""([А-Яа-яЁё]+)\s+(\d{2})/(\d{2})/(\d{4})""")
        private val GROUP_ID_IN_ONCLICK = Regex("""getShedule\(\s*\d+\s*,\s*\d+\s*,\s*(\d+)""")
        private val TEACHER_ID_IN_ONCLICK = Regex("""getShedule\(\s*\d+\s*,\s*\d+\s*,\s*(\d+)""")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

class ScheduleException(message: String, cause: Throwable? = null) : Exception(message, cause)
