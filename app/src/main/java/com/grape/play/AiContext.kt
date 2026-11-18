package com.grape.play

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Âm lịch Việt Nam (thuật toán Hồ Ngọc Đức, múi giờ +7). */
object Lunar {
    class D(val day: Int, val month: Int, val year: Int)

    private fun jd(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12; val y = yy + 4800 - a; val m = mm + 12 * a - 3
        var j = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (j < 2299161) j = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return j
    }

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85; val t2 = t * t; val t3 = t2 * t; val dr = PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mp = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c = c - 0.4068 * sin(mp * dr) + 0.0161 * sin(dr * 2 * mp)
        c -= 0.0004 * sin(dr * 3 * mp)
        c = c + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mp))
        c = c - 0.0074 * sin(dr * (m - mp)) + 0.0004 * sin(dr * (2 * f + m))
        c = c - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mp))
        c = c + 0.0010 * sin(dr * (2 * f - mp)) + 0.0005 * sin(dr * (2 * mp + m))
        val dt = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else -0.000278 + 0.000265 * t + 0.000262 * t2
        return jd1 + c - dt
    }

    private fun sunLong(j: Double): Double {
        val t = (j - 2451545.0) / 36525; val t2 = t * t; val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        val dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m) + (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return l
    }

    private fun sunLon(d: Int, tz: Int) = (sunLong(d - 0.5 - tz / 24.0) / PI * 6).toInt()
    private fun nm(k: Int, tz: Int) = (newMoon(k) + 0.5 + tz / 24.0).toInt()

    private fun month11(yy: Int, tz: Int): Int {
        val off = jd(31, 12, yy) - 2415021
        val k = (off / 29.530588853).toInt()
        var n = nm(k, tz)
        if (sunLon(n, tz) >= 9) n = nm(k - 1, tz)
        return n
    }

    private fun leapOff(a11: Int, tz: Int): Int {
        val k = ((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var i = 1; var arc = sunLon(nm(k + i, tz), tz); var last: Int
        do { last = arc; i++; arc = sunLon(nm(k + i, tz), tz) } while (arc != last && i < 14)
        return i - 1
    }

    fun fromSolar(dd: Int, mm: Int, yy: Int, tz: Int = 7): D {
        val dn = jd(dd, mm, yy)
        val k = ((dn - 2415021.076998695) / 29.530588853).toInt()
        var ms = nm(k + 1, tz)
        if (ms > dn) ms = nm(k, tz)
        var a11 = month11(yy, tz); var b11 = a11
        val ly: Int
        if (a11 >= ms) { ly = yy; a11 = month11(yy - 1, tz) } else { ly = yy + 1; b11 = month11(yy + 1, tz) }
        val ld = dn - ms + 1
        val diff = ((ms - a11) / 29.0).toInt()
        var lm = diff + 11
        if (b11 - a11 > 365) { if (diff >= leapOff(a11, tz)) lm = diff + 10 }
        if (lm > 12) lm -= 12
        var y = ly
        if (lm >= 11 && diff < 4) y -= 1
        return D(ld, lm, y)
    }

    fun of(c: Calendar) = fromSolar(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
}

/** Bối cảnh cho gợi ý: thời tiết thực tế tại nơi ở (định vị theo IP), mùa, lễ hội/phong tục, âm lịch. */
object AiContext {
    class Info(val city: String, val country: String, val lat: Double, val lon: Double, val weather: String, val temp: Double?, val isDay: Boolean?, val at: Long)

    val MOODS = listOf("Tự động", "Buồn", "Vui", "Chill", "Thư giãn", "Lãng mạn", "Tập trung", "Sôi động", "Hoài niệm", "Ngủ ngon")

    @Volatile private var cache: Info? = null
    private val web by lazy { Ai.http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build() }

    private fun getJson(url: String): JSONObject? = try {
        web.newCall(Request.Builder().url(url).header("User-Agent", "GRAPE2Play").build()).execute().use { r ->
            if (r.isSuccessful) JSONObject(r.body?.string() ?: "") else null
        }
    } catch (e: Exception) { null }

    private fun wmo(c: Int) = when (c) {
        0 -> "trời quang, nắng"
        1 -> "ít mây, nắng nhẹ"
        2 -> "mây rải rác"
        3 -> "nhiều mây, âm u"
        45, 48 -> "sương mù"
        51, 53, 55, 56, 57 -> "mưa phùn"
        61, 63, 65, 66, 67 -> "mưa"
        71, 73, 75, 77 -> "tuyết rơi"
        80, 81, 82 -> "mưa rào"
        85, 86 -> "mưa tuyết"
        95, 96, 99 -> "giông, sấm sét"
        else -> "không rõ"
    }

    /** Trả về null nếu không có mạng; vẫn dùng được ngày/lễ hội. Cache 30 phút. */
    suspend fun load(force: Boolean = false): Info? = withContext(Dispatchers.IO) {
        val c = cache
        if (!force && c != null && System.currentTimeMillis() - c.at < 30 * 60_000L) return@withContext c
        var city = ""; var country = ""; var lat = Double.NaN; var lon = Double.NaN
        getJson("https://ipwho.is/")?.let { j ->
            if (j.optBoolean("success", true) && j.has("latitude")) {
                city = j.optString("city"); country = j.optString("country"); lat = j.optDouble("latitude"); lon = j.optDouble("longitude")
            }
        }
        if (lat.isNaN()) getJson("https://ipapi.co/json/")?.let { j ->
            if (j.has("latitude")) { city = j.optString("city"); country = j.optString("country_name"); lat = j.optDouble("latitude"); lon = j.optDouble("longitude") }
        }
        if (lat.isNaN()) return@withContext null
        var w = ""; var temp: Double? = null; var day: Boolean? = null
        getJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto")?.optJSONObject("current")?.let { cur ->
            w = wmo(cur.optInt("weather_code", -1)); temp = cur.optDouble("temperature_2m").takeIf { !it.isNaN() }; day = cur.optInt("is_day", 1) == 1
        }
        val i = Info(city, country, lat, lon, w, temp, day, System.currentTimeMillis())
        cache = i; i
    }

    private fun season(m: Int, south: Boolean): String {
        val s = when (m) { 3, 4, 5 -> 0; 6, 7, 8 -> 1; 9, 10, 11 -> 2; else -> 3 }
        return listOf("mùa xuân", "mùa hè", "mùa thu", "mùa đông")[if (south) (s + 2) % 4 else s]
    }

    /** Ngày lễ trong vòng [within] ngày tới (kể cả hôm nay): "tên (hôm nay / n ngày nữa)". */
    private fun festivals(within: Int = 14): List<String> {
        val out = ArrayList<String>()
        val cal = Calendar.getInstance()
        for (d in 0..within) {
            val c = cal.clone() as Calendar; c.add(Calendar.DAY_OF_YEAR, d)
            val dd = c.get(Calendar.DAY_OF_MONTH); val mm = c.get(Calendar.MONTH) + 1
            val l = Lunar.of(c)
            val names = ArrayList<String>()
            when {
                dd == 14 && mm == 2 -> names += "Valentine"
                dd == 8 && mm == 3 -> names += "Quốc tế Phụ nữ 8/3"
                dd == 30 && mm == 4 -> names += "30/4"
                dd == 1 && mm == 5 -> names += "Quốc tế Lao động 1/5"
                dd == 1 && mm == 6 -> names += "Quốc tế Thiếu nhi 1/6"
                dd == 2 && mm == 9 -> names += "Quốc khánh 2/9"
                dd == 20 && mm == 10 -> names += "Phụ nữ Việt Nam 20/10"
                dd == 31 && mm == 10 -> names += "Halloween"
                dd == 20 && mm == 11 -> names += "Ngày Nhà giáo Việt Nam 20/11"
                dd == 24 && mm == 12 -> names += "đêm Giáng sinh"
                dd == 25 && mm == 12 -> names += "Giáng sinh"
                dd == 31 && mm == 12 -> names += "đêm giao thừa dương lịch"
                dd == 1 && mm == 1 -> names += "Tết Dương lịch"
            }
            when {
                l.month == 1 && l.day == 1 -> names += "Tết Nguyên đán"
                l.month == 12 && l.day >= 29 && Lunar.of((c.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, 1) }).month == 1 -> names += "đêm Giao thừa"
                l.month == 12 && l.day == 23 -> names += "ông Công ông Táo"
                l.month == 1 && l.day == 15 -> names += "Rằm tháng Giêng"
                l.month == 3 && l.day == 10 -> names += "Giỗ Tổ Hùng Vương"
                l.month == 4 && l.day == 15 -> names += "Phật Đản"
                l.month == 5 && l.day == 5 -> names += "Tết Đoan Ngọ"
                l.month == 7 && l.day == 15 -> names += "Vu Lan"
                l.month == 8 && l.day == 15 -> names += "Tết Trung Thu"
                l.month == 9 && l.day == 9 -> names += "Tết Trùng Cửu"
            }
            names.forEach { out += it + if (d == 0) " (hôm nay)" else " ($d ngày nữa)" }
        }
        return out
    }

    private fun daysToTet(): Int? {
        val cal = Calendar.getInstance()
        for (d in 0..400) {
            val c = cal.clone() as Calendar; c.add(Calendar.DAY_OF_YEAR, d)
            val l = Lunar.of(c)
            if (l.month == 1 && l.day == 1) return d
        }
        return null
    }

    private fun lunarText(): String { val l = Lunar.of(Calendar.getInstance()); return "${l.day}/${l.month} âm lịch" }

    /** Dòng tóm tắt hiển thị trên màn hình. */
    fun headline(i: Info?): String {
        val parts = ArrayList<String>()
        if (i != null) {
            val place = listOf(i.city, i.country).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotEmpty()) parts += "📍 $place"
            if (i.weather.isNotEmpty()) parts += i.weather + (i.temp?.let { " ${Math.round(it)}°C" } ?: "")
        }
        parts += lunarText()
        daysToTet()?.let { parts += if (it == 0) "hôm nay là Tết" else "$it ngày nữa là Tết" }
        return parts.joinToString("  ·  ")
    }

    /** Văn bản bối cảnh đưa vào lời nhắc cho AI. */
    fun describe(i: Info?, mood: String): String {
        val cal = Calendar.getInstance()
        val sb = StringBuilder()
        sb.append("Ngày ${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)} (${lunarText()}), ${timeOfDay()}.\n")
        val south = (i?.lat ?: 1.0) < 0
        sb.append("Mùa theo lịch: ${season(cal.get(Calendar.MONTH) + 1, south)}.\n")
        if (i != null) {
            val place = listOf(i.city, i.country).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotEmpty()) sb.append("Nơi ở (ước lượng theo IP): $place.\n")
            if (i.weather.isNotEmpty()) sb.append("Thời tiết thực tế lúc này: ${i.weather}${i.temp?.let { ", ${Math.round(it)}°C" } ?: ""}${if (i.isDay == false) ", ban đêm" else ""}.\n")
        } else sb.append("(Không lấy được thời tiết — chỉ dựa vào ngày giờ.)\n")
        val f = festivals()
        if (f.isNotEmpty()) sb.append("Lễ hội / phong tục sắp tới: ${f.joinToString("; ")}.\n")
        daysToTet()?.let { if (it in 1..45) sb.append("Còn $it ngày nữa là Tết Nguyên đán.\n") }
        sb.append("Nếu nơi ở không phải Việt Nam, hãy cân nhắc lễ hội và phong tục của nước đó.\n")
        if (mood.isNotEmpty() && mood != "Tự động") sb.append("Tâm trạng người nghe chọn: $mood (ưu tiên cao nhất).\n")
        return sb.toString()
    }
}
