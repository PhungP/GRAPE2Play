package com.grape.play

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Thiết bị nhận nhạc không dây. kind: "upnp" (MediaRenderer/OpenHome, ví dụ WiiM Ultra) hoặc "cast" (Google Cast). */
data class Renderer(
    val kind: String, val name: String, val id: String, val host: String, val port: Int = 0,
    val avCtrl: String = "", val rcCtrl: String = "",
    /** 2.9.9: metadata từ UPnP device description (chỉ để nhận diện thiết bị trong danh mục; không ảnh hưởng phát nhạc). */
    val maker: String = "", val model: String = "", val modelDesc: String = "",
    /** URL device description đã thấy gần nhất: tải lại trực tiếp (HTTP) khi loa không trả lời SSDP (loa chờ/Wi-Fi tiết kiệm điện). */
    val loc: String = "",
) {
    val label: String get() = name + if (kind == "cast") " · Google Cast" else " · UPnP"
}

object Renderers {
    /** Context ứng dụng, để bộ điều khiển UPnP tự quét lại loa khi địa chỉ đổi (đặt ở Playback.init). */
    @Volatile var app: Context? = null

    /** Quét lại và trả về đúng thiết bị này (theo mã nhận dạng, rồi theo tên) với địa chỉ điều khiển mới nhất; null nếu chưa thấy. */
    suspend fun refresh(ctx: Context, old: Renderer): Renderer? {
        val all = discover(ctx)
        return all.firstOrNull { it.kind == old.kind && it.id == old.id } ?: all.firstOrNull { it.kind == old.kind && it.name == old.name }
    }

    /** Ghi chú lần quét gần nhất (hiện dưới danh sách thiết bị để biết vì sao không thấy loa). */
    @Volatile var report = ""
        private set

    /**
     * Quét độc lập với cách gửi DSD (file gốc hay DoP): không đọc bất kỳ cài đặt âm thanh nào.
     * Google Cast: hợp NsdManager + mDNS tự viết. UPnP: SSDP đa hướng trên mọi card mạng + hỏi thẳng IP.
     * Quét 2 lượt nếu lượt đầu thiếu thiết bị đã biết; thiết bị từng thấy được nhớ lại và kiểm tra bằng kết nối TCP,
     * nên loa đang bận / UDP rơi gói vẫn không biến mất khỏi danh sách.
     */
    suspend fun discover(ctx: Context): List<Renderer> = coroutineScope {
        val app = ctx.applicationContext
        val known = loadKnown(app)
        scanSummary.clear()
        var found = pass(app)
        var passes = 1
        val knownHosts = known.map { it.host }.toSet()
        if (knownHosts.any { h -> found.none { it.host == h } } || found.isEmpty()) { found = merge(found, pass(app)); passes = 2 }
        // Thiết bị đã biết mà quét không thấy: thử kết nối trực tiếp tới cổng của nó
        val back = known.filter { k -> found.none { it.kind == k.kind && (it.id == k.id || it.host == k.host && it.name == k.name) } }
            .map { k -> async(Dispatchers.IO) { revive(k) } }.awaitAll().filterNotNull()
        val all = merge(found, back)
        saveKnown(app, all)
        report = "Quét $passes lượt · UPnP ${all.count { it.kind == "upnp" }} · Google Cast ${all.count { it.kind == "cast" }}" +
            (if (back.isNotEmpty()) " (${back.size} thiết bị nhớ từ lần trước, vẫn trả lời)" else "") + " · SSDP $lastReplies phản hồi" + (if (scanSummary.isEmpty()) "" else "\nChi tiết: " + scanSummary.joinToString(" · ") + " (số phản hồi SSDP theo từng cách quét)")
        all.sortedBy { it.name.lowercase() }
    }

    /**
     * Thiết bị từng thấy mà lần quét này không thấy (loa đang chờ, UDP rơi gói, cổng UPnP đổi sau khi loa khởi động lại):
     * Cast → kiểm tra cổng 8009; UPnP → (1) tải lại device description theo URL đã lưu (HTTP vẫn chạy khi loa im lặng với SSDP),
     * (2) kết nối cổng điều khiển cũ, (3) hỏi SSDP unicast tới đúng IP. Chỉ trả về thiết bị thật sự trả lời.
     */
    private fun revive(k: Renderer): Renderer? {
        if (k.kind == "cast") return if (reachable(k.host, 8009)) k else null
        if (k.loc.isNotEmpty()) tryDescribe(k.loc, "nhớ lại ${k.name}")?.let { return it }
        if (reachable(k.host, k.port)) return k
        return runCatching { probeHost(k.host) }.getOrNull()
    }

    /** Tóm tắt từng card mạng của lần quét gần nhất (hiện dưới danh sách thiết bị để biết quét ra sao). */
    private val scanSummary = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private fun dlog(msg: String) { runCatching { AppLog.i("QuetLoa", msg) } }
    private fun dwarn(msg: String, t: Throwable? = null) { runCatching { AppLog.w("QuetLoa", msg, t) } }

    /** Tải device description; ghi lại lý do nếu lỗi (trước đây lỗi bị nuốt im lặng nên không biết vì sao loa không hiện). */
    private fun tryDescribe(loc: String, who: String): Renderer? {
        for (attempt in 1..2) {
            try {
                val r = describe(loc)
                if (r == null) { dlog("$who: $loc — mô tả không có AVTransport (không phải loa nhận nhạc)"); return null }
                dlog("$who: tìm thấy \"${r.name}\" (${r.maker} ${r.model}) tại ${r.host}:${r.port}")
                return r
            } catch (e: Exception) { if (attempt == 2) dwarn("$who: tải mô tả $loc lỗi: ${e.javaClass.simpleName} ${e.message}") }
        }
        return null
    }

    @Volatile private var lastReplies = 0

    private fun merge(a: List<Renderer>, b: List<Renderer>): List<Renderer> =
        (a + b).distinctBy { it.kind + "|" + it.id }

    private fun reachable(host: String, port: Int): Boolean {
        if (port <= 0) return false
        return runCatching { java.net.Socket().use { it.connect(InetSocketAddress(host, port), 1500); true } }.getOrDefault(false)
    }

    private suspend fun pass(app: Context): List<Renderer> = coroutineScope {
        val c = async(Dispatchers.IO) { runCatching { cast(app) }.getOrDefault(emptyList()) }
        val m = async(Dispatchers.IO) { runCatching { castMdns(app) }.getOrDefault(emptyList()) }
        val u = async(Dispatchers.IO) { runCatching { upnp(app) }.getOrDefault(Triple(emptyList<Renderer>(), 0, 0)) }
        val casts = merge(c.await(), m.await())
        val (ups, replies, _) = u.await()
        lastReplies = replies
        // Loa vừa có Google Cast vừa có UPnP (JBL L75ms…): nếu SSDP đa hướng không thấy, hỏi thẳng địa chỉ IP mà Cast đã báo.
        val missing = casts.map { it.host }.distinct().filter { h -> ups.none { it.host == h } }
        dlog("Cast thấy ${casts.size} thiết bị (${casts.joinToString { it.name + "@" + it.host }}); UPnP thấy ${ups.size} (${ups.joinToString { it.name + "@" + it.host }}); Cast chưa có UPnP → hỏi trực tiếp: ${missing.joinToString().ifEmpty { "không" }}")
        val extra = missing.map { h -> async(Dispatchers.IO) { runCatching { probeHost(h) }.getOrNull() } }.awaitAll().filterNotNull()
        merge(merge(ups, extra), casts)
    }

    private fun castMdns(ctx: Context): List<Renderer> {
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching { wifi?.createMulticastLock("grapeplay-m")?.also { it.setReferenceCounted(false); it.acquire() } }.getOrNull()
        try {
            return Mdns.browse("_googlecast._tcp.local.", 4500).map { h ->
                Renderer("cast", h.txt["fn"].orEmpty().ifEmpty { h.instance }, h.txt["id"].orEmpty().ifEmpty { h.instance }, h.host, h.port, model = h.txt["md"].orEmpty())
            }
        } finally { runCatching { lock?.release() } }
    }

    // ---------------- Nhớ thiết bị đã thấy ----------------
    fun loadKnown(ctx: Context): List<Renderer> = runCatching {
        val arr = org.json.JSONArray(ctx.getSharedPreferences("renderers", Context.MODE_PRIVATE).getString("known", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let {
            Renderer(it.getString("kind"), it.getString("name"), it.getString("id"), it.getString("host"), it.optInt("port"), it.optString("av"), it.optString("rc"), it.optString("mk"), it.optString("md"), it.optString("mdesc"), it.optString("loc"))
        } }
    }.getOrDefault(emptyList())

    private fun saveKnown(ctx: Context, list: List<Renderer>) {
        runCatching {
            val arr = org.json.JSONArray()
            list.take(40).forEach { r -> arr.put(org.json.JSONObject().put("kind", r.kind).put("name", r.name).put("id", r.id).put("host", r.host).put("port", r.port).put("av", r.avCtrl).put("rc", r.rcCtrl).put("mk", r.maker).put("md", r.model).put("mdesc", r.modelDesc).put("loc", r.loc)) }
            ctx.getSharedPreferences("renderers", Context.MODE_PRIVATE).edit().putString("known", arr.toString()).apply()
        }
    }

    // ---------------- UPnP MediaRenderer (SSDP) ----------------
    // Nhiều thẻ tìm kiếm: một số loa chỉ trả lời "ssdp:all" / rootdevice chứ không trả lời MediaRenderer:1.
    private val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "upnp:rootdevice",
        "ssdp:all",
    )

    private fun msearch(st: String, mx: Int = 2): ByteArray =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: $mx\r\nST: $st\r\nUSER-AGENT: Android/8 UPnP/1.1 GRAPEPlay/0.8\r\n\r\n".toByteArray()

    private fun locationOf(reply: String): String? =
        reply.lines().firstOrNull { it.startsWith("location:", true) }?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

    /** Các địa chỉ IPv4 của máy (Wi-Fi, Ethernet…): TV/máy có nhiều card mạng thì gói multicast phải gửi ra từng card. */
    private fun localAddrs(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
            .flatMap { ni -> ni.inetAddresses.toList().filter { it is Inet4Address && !it.isLoopbackAddress } }
    }.getOrDefault(emptyList())

    /** Gửi M-SEARCH ra một card mạng (null = để hệ thống chọn) rồi nghe phản hồi ~4,5 giây; gửi lặp 3 lần vì UDP có thể rơi gói. */
    private fun searchOn(addr: InetAddress?, locs: MutableSet<String>, replies: AtomicInteger) {
        val mine = AtomicInteger(0)
        try { searchOn0(addr, locs, replies, mine) } finally { dlog("multicast ${addr?.hostAddress ?: "mặc định"}: ${mine.get()} phản hồi"); scanSummary.add("multicast ${addr?.hostAddress ?: "?"}: ${mine.get()}") }
    }
    private fun searchOn0(addr: InetAddress?, locs: MutableSet<String>, replies: AtomicInteger, mine: AtomicInteger) {
        val sock: DatagramSocket = if (addr == null) DatagramSocket() else MulticastSocket(InetSocketAddress(addr, 0)).also {
            runCatching { it.networkInterface = NetworkInterface.getByInetAddress(addr) }
            it.timeToLive = 4
        }
        sock.use {
            it.soTimeout = 300
            val grp = InetAddress.getByName("239.255.255.250")
            val end = System.currentTimeMillis() + 4500
            var nextSend = 0L; var round = 0
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < end) {
                if (round < 3 && System.currentTimeMillis() >= nextSend) {
                    for (st in SEARCH_TARGETS) { val m = msearch(st); runCatching { it.send(DatagramPacket(m, m.size, grp, 1900)) } }
                    round++; nextSend = System.currentTimeMillis() + 1200
                }
                try {
                    val p = DatagramPacket(buf, buf.size); it.receive(p)
                    replies.incrementAndGet(); mine.incrementAndGet()
                    locationOf(String(p.data, 0, p.length))?.let { l -> locs += l }
                } catch (e: SocketTimeoutException) { }
            }
        }
    }

    /**
     * Gửi M-SEARCH unicast tới MỌI địa chỉ trong mạng con /24 (hoặc nhỏ hơn) của card mạng [addr] rồi nghe phản hồi ~4,5 giây.
     * Chỉ ~250 gói UDP nhỏ × vài vòng; thiết bị không phải UPnP bỏ qua hoặc trả ICMP, vô hại.
     */
    private fun sweepOn(addr: InetAddress, locs: MutableSet<String>, replies: AtomicInteger) {
        val mine = AtomicInteger(0)
        try { sweepOn0(addr, locs, replies, mine) } finally { dlog("quét unicast ${addr.hostAddress}: ${mine.get()} phản hồi"); scanSummary.add("unicast ${addr.hostAddress}: ${mine.get()}") }
    }
    private fun sweepOn0(addr: InetAddress, locs: MutableSet<String>, replies: AtomicInteger, mine: AtomicInteger) {
        val ni = NetworkInterface.getByInetAddress(addr) ?: return
        val ia = ni.interfaceAddresses.firstOrNull { it.address == addr } ?: return
        val prefix = ia.networkPrefixLength.toInt().coerceIn(24, 30)           // dải rộng hơn /24 chỉ quét /24 chứa máy này
        val me = addr.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        val mask = (-1) shl (32 - prefix)
        val net = me and mask; val last = net or mask.inv()
        val sts = listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "urn:schemas-upnp-org:service:AVTransport:1", "ssdp:all")
        DatagramSocket(InetSocketAddress(addr, 0)).use { sock ->
            sock.soTimeout = 250
            val end = System.currentTimeMillis() + 4500
            var round = 0; var nextSend = 0L
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < end) {
                if (round < 2 && System.currentTimeMillis() >= nextSend) {
                    for (h in net + 1 until last) {
                        if (h == me) continue
                        val ip = InetAddress.getByAddress(byteArrayOf((h ushr 24).toByte(), (h ushr 16).toByte(), (h ushr 8).toByte(), h.toByte()))
                        for (st in sts) { val m = msearch(st, 1); runCatching { sock.send(DatagramPacket(m, m.size, ip, 1900)) } }
                    }
                    round++; nextSend = System.currentTimeMillis() + 1800
                }
                try {
                    val p = DatagramPacket(buf, buf.size); sock.receive(p)
                    replies.incrementAndGet(); mine.incrementAndGet()
                    locationOf(String(p.data, 0, p.length))?.let { l -> locs += l }
                } catch (e: SocketTimeoutException) { }
            }
        }
    }

    /** Trả về (danh sách thiết bị, số phản hồi SSDP, số card mạng đã dùng). */
    private suspend fun upnp(ctx: Context): Triple<List<Renderer>, Int, Int> = coroutineScope {
        val locs = ConcurrentHashMap.newKeySet<String>()
        val replies = AtomicInteger(0)
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching { wifi?.createMulticastLock("grapeplay-r")?.also { it.setReferenceCounted(false); it.acquire() } }.getOrNull()
        val addrs = localAddrs()
        dlog("Card mạng IPv4: " + (addrs.joinToString { a -> val ni = runCatching { NetworkInterface.getByInetAddress(a) }.getOrNull(); "${ni?.name ?: "?"} ${a.hostAddress}/" + (ni?.interfaceAddresses?.firstOrNull { it.address == a }?.networkPrefixLength ?: -1) }.ifEmpty { "KHÔNG CÓ" }))
        try {
            val targets: List<InetAddress?> = addrs.ifEmpty { listOf(null) }
            val multicast = targets.map { a -> async(Dispatchers.IO) { runCatching { searchOn(a, locs, replies) }.onFailure { dwarn("multicast ${a?.hostAddress} lỗi", it) } } }
            // Nhiều loa (JBL L75ms…) KHÔNG trả lời M-SEARCH multicast (AP/router lọc multicast, loa chỉ nghe unicast) nhưng trả lời ngay gói gửi thẳng tới IP:
            // quét unicast cả dải mạng con của từng card mạng song song với multicast.
            val sweep = addrs.map { a -> async(Dispatchers.IO) { runCatching { sweepOn(a, locs, replies) }.onFailure { dwarn("quét unicast ${a.hostAddress} lỗi", it) } } }
            multicast.awaitAll(); sweep.awaitAll()
        } finally { runCatching { lock?.release() } }
        dlog("SSDP: ${replies.get()} phản hồi, ${locs.size} địa chỉ mô tả khác nhau")
        val found = locs.toList().map { l -> async(Dispatchers.IO) { tryDescribe(l, "SSDP") } }.awaitAll().filterNotNull().distinctBy { it.id }
        Triple(found, replies.get(), addrs.size)
    }

    /** Hỏi trực tiếp (unicast) một địa chỉ IP: nhiều loa không trả lời gói multicast nhưng trả lời gói gửi thẳng tới cổng 1900. */
    private fun probeHost(host: String): Renderer? {
        val locs = LinkedHashSet<String>()
        DatagramSocket().use { sock ->
            sock.soTimeout = 400
            val ip = InetAddress.getByName(host)
            val end = System.currentTimeMillis() + 3000
            var round = 0; var nextSend = 0L
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < end) {
                if (round < 3 && System.currentTimeMillis() >= nextSend) {
                    for (st in SEARCH_TARGETS) { val m = msearch(st, 1); runCatching { sock.send(DatagramPacket(m, m.size, ip, 1900)) } }
                    round++; nextSend = System.currentTimeMillis() + 900
                }
                try {
                    val p = DatagramPacket(buf, buf.size); sock.receive(p)
                    locationOf(String(p.data, 0, p.length))?.let { locs += it }
                } catch (e: SocketTimeoutException) { }
            }
        }
        dlog("hỏi trực tiếp $host: ${locs.size} địa chỉ mô tả")
        return locs.firstNotNullOfOrNull { l -> tryDescribe(l, "unicast $host") }
    }

    private fun describe(loc: String): Renderer? {
        val http = Http.client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
        val xml = http.newCall(Request.Builder().url(loc).build()).execute().use { it.body!!.string() }
        val p = Xml.newPullParser(); p.setInput(StringReader(xml))
        var name = ""; var udn = ""; var base = ""; var st = ""; var inSvc = false; var av = ""; var rc = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "friendlyName" -> { val v = p.nextText(); if (name.isEmpty()) name = v }
                    "UDN" -> { val v = p.nextText(); if (udn.isEmpty()) udn = v }
                    "URLBase" -> base = p.nextText()
                    "service" -> { inSvc = true; st = "" }
                    "serviceType" -> { val v = p.nextText(); if (inSvc) st = v }
                    "controlURL" -> {
                        val c = p.nextText().trim()
                        if (inSvc && st.contains("AVTransport") && av.isEmpty()) av = c
                        if (inSvc && st.contains("RenderingControl") && rc.isEmpty()) rc = c
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "service") inSvc = false
            ev = p.next()
        }
        if (av.isEmpty()) return null
        // Metadata nhận diện (hãng/model/mô tả) đọc ĐỘC LẬP bằng regex: khâu đọc chính ở trên giữ nguyên như bản ≤2.9.8, nên một thẻ lạ/không phải văn bản thuần
        // không thể làm hỏng việc phát hiện loa (bản 2.9.9–3.0.2 đọc các thẻ này bằng nextText() trong bộ đọc chính).
        fun tag(n: String) = runCatching { Regex("<$n[^>]*>(.*?)</$n>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xml)?.groupValues?.get(1)?.replace(Regex("<!\\[CDATA\\[|\\]\\]>"), "")?.trim().orEmpty() }.getOrDefault("")
        val mk = tag("manufacturer"); val md = tag("modelName"); val mdesc = tag("modelDescription")
        val root = URL(if (base.isNotEmpty()) base else loc)
        val u = URL(loc)
        return Renderer("upnp", name.ifEmpty { "Thiết bị UPnP" }, udn.ifEmpty { loc }, u.host, u.port,
            URL(root, av).toString(), if (rc.isEmpty()) "" else URL(root, rc).toString(), mk, md, mdesc, loc)
    }

    // ---------------- Google Cast (mDNS qua NsdManager) ----------------
    private suspend fun cast(ctx: Context): List<Renderer> = withContext(Dispatchers.IO) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching { wifi?.createMulticastLock("grapeplay-c")?.also { it.setReferenceCounted(false); it.acquire() } }.getOrNull()
        val found = ConcurrentLinkedQueue<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) { found.add(serviceInfo) }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        val out = ArrayList<Renderer>()
        try {
            nsd.discoverServices("_googlecast._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            delay(3500)
        } finally { runCatching { nsd.stopServiceDiscovery(listener) } }
        try {
            for (s in found.toList().distinctBy { it.serviceName }) {
                val r = resolve(nsd, s) ?: continue
                val host = r.host?.hostAddress ?: continue
                fun attr(k: String) = r.attributes?.get(k)?.let { String(it, Charsets.UTF_8) }.orEmpty()
                val id = attr("id").ifEmpty { r.serviceName }
                out += Renderer("cast", attr("fn").ifEmpty { r.serviceName }, id, host, r.port, model = attr("md"))
            }
        } finally { runCatching { lock?.release() } }
        out.distinctBy { it.id }
    }

    // NsdManager (Android 8) chỉ cho phép resolve từng dịch vụ một.
    private suspend fun resolve(nsd: NsdManager, s: NsdServiceInfo): NsdServiceInfo? = withTimeoutOrNull(2500) {
        suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) { if (cont.isActive) cont.resume(serviceInfo) }
            })
        }
    }
}
