package com.grape.play

import android.content.Context
import android.media.MediaMetadataRetriever as MMR
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object Library {
    /** Tăng khi thêm trường metadata mới → bản ghi trong cache cũ được đọc lại một lần (nếu đọc lỗi vẫn giữ thẻ cũ). */
    const val META_VER = 2
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val scanning = MutableStateFlow(false)
    /** true khi cache của mọi nguồn đã được nạp (trước khi quét lại) — để khôi phục hàng đợi đã lưu. */
    val cacheLoaded = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lists = HashMap<String, List<Track>>()
    private val active = AtomicInteger(0)
    private val localBusy = AtomicBoolean(false)
    /** Điều phối quét: một lần quét/nguồn, hủy theo thế hệ, khoá xác nhận kết quả/xoá nguồn (xem [ScanGuard]). */
    private val guard = ScanGuard()
    /** Dừng lần quét NAS/DLNA/đám mây đang chạy (giữ lại những gì đã tìm được). */
    fun cancelScan() { guard.cancelAll() }

    private fun begin() { active.incrementAndGet(); scanning.value = true }
    private fun end() { if (active.decrementAndGet() <= 0) scanning.value = false }
    /** Gộp các nguồn thành một danh sách (một lần cấp phát đúng kích thước) rồi áp sửa tag. Gọi trong khoá. */
    private fun rebuild() {
        var n = 0; for (v in lists.values) n += v.size
        val all = ArrayList<Track>(n); for (v in lists.values) all.addAll(v)
        tracks.value = TagFix.apply(all)
    }
    @Synchronized private fun publish(id: String, l: List<Track>) { if (lists[id] === l) return; lists[id] = l; rebuild() }
    @Synchronized private fun drop(id: String) { lists.remove(id); rebuild() }
    /** Áp lại các sửa tên nghệ sĩ/album (hoặc hoàn tác) lên toàn thư viện. */
    @Synchronized fun reapply() { rebuild() }
    @Synchronized private fun cached(id: String): List<Track> = lists[id].orEmpty()
    private fun cacheFile(ctx: Context, id: String) = File(ctx.filesDir, if (id == "local") "library_v4.json" else "src_$id.json")
    private fun bakFile(f: File) = File(f.path + ".bak")

    /** Đọc cache; nếu file chính hỏng/thiếu thì thử bản sao lưu .bak. Trả null nếu không có gì dùng được. */
    private fun readCache(ctx: Context, id: String): List<Track>? {
        val f = cacheFile(ctx, id)
        for (c in listOf(f, bakFile(f))) {
            if (!c.exists()) continue
            val r = runCatching { fromJson(c.readText()) }
            if (r.isSuccess) { if (c !== f) AppLog.i("Library", "cache $id: dùng bản sao lưu"); return r.getOrNull() }
            AppLog.i("Library", "cache $id (${c.name}) không đọc được: ${r.exceptionOrNull()?.message}")
        }
        return null
    }

    /** Ghi cache an toàn: ghi file tạm → sao lưu bản cũ (.bak) → đổi tên. Không bao giờ làm hỏng bản cũ nếu ghi dở. */
    private fun writeCache(ctx: Context, id: String, l: List<Track>) {
        val f = cacheFile(ctx, id); val tmp = File(f.path + ".tmp")
        try {
            tmp.writeText(toJson(l))
            if (f.exists()) runCatching { f.copyTo(bakFile(f), overwrite = true) }
            if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            AppLog.i("Library", "không ghi được cache $id: ${e.message}")
        }
    }


    /**
     * File bị di chuyển/đổi tên: bài cũ biến mất và đúng một bài mới xuất hiện với cùng kích thước, thời lượng, tên, nghệ sĩ, album
     * → chuyển Yêu thích / Gần đây / thống kê nghe sang đường dẫn mới. Mơ hồ (nhiều ứng viên) thì không làm gì. Không xóa gì cả.
     */
    private fun relink(old: Collection<Track>, fresh: List<Track>) {
        val newPaths = fresh.mapTo(HashSet()) { it.path }; val oldPaths = old.mapTo(HashSet()) { it.path }
        val gone = old.filter { it.path !in newPaths && it.size > 0 }
        val added = fresh.filter { it.path !in oldPaths && it.size > 0 }
        if (gone.isEmpty() || added.isEmpty()) return
        fun key(t: Track) = "${t.size}|${t.durationMs / 1000}|${t.title.lowercase()}|${t.artist.lowercase()}|${t.album.lowercase()}"
        val g = gone.groupBy(::key); val a = added.groupBy(::key)
        val map = HashMap<String, String>()
        for ((k, gl) in g) { val al = a[k] ?: continue; if (gl.size == 1 && al.size == 1) map[gl[0].path] = al[0].path }
        if (map.isEmpty()) return
        Favorites.remap(map); Recents.remap(map); UsageStats.remap(map); UserPlaylists.remap(map); PlayHistory.remap(map)
        AppLog.i("Library", "đã nối lại ${map.size} bài bị di chuyển/đổi tên")
    }

    /** Báo cáo nhóm bài có vẻ trùng (cùng tên + nghệ sĩ + album + thời lượng làm tròn giây). Chỉ để xem — không xóa, không gộp. */
    fun duplicateGroups(): List<List<Track>> =
        tracks.value.filter { it.durationMs > 0 }
            .groupBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.album.trim().lowercase()}|${it.durationMs / 1000}" }
            .values.filter { g -> g.size > 1 && g.map { it.path }.distinct().size > 1 }

    /** Dò mọi ổ đọc được: bộ nhớ trong, thư mục app trên từng ổ (cách chuẩn để thấy USB), và thư mục gốc của từng ổ trong thư mục storage. */
    private fun roots(ctx: Context): List<File> {
        val r = LinkedHashMap<String, File>()
        fun add(f: File?) { if (f != null) r.putIfAbsent(f.path, f) }
        add(Environment.getExternalStorageDirectory())
        runCatching {
            ctx.getExternalFilesDirs(null).filterNotNull().forEach { d ->
                val i = d.path.indexOf("/Android/"); if (i > 0) add(File(d.path.substring(0, i)))
            }
        }
        File("/storage").listFiles()?.forEach { if (it.name != "self" && it.name != "emulated" && it.isDirectory) add(it) }
        return r.values.toList()
    }
    private fun rootLabel(f: File) = if (f.path.startsWith("/storage/emulated") || f.path.startsWith("/sdcard")) "TV" else "USB ${f.name}"

    private val cacheMutex = Mutex()
    private val startup = StartupScan()

    /** Nạp cache của mọi nguồn đúng một lần (an toàn khi gọi đồng thời từ LibraryProvider và Activity). */
    private suspend fun ensureCacheLoaded(ctx: Context) {
        cacheMutex.withLock {
            if (cacheLoaded.value) return
            withContext(Dispatchers.IO) {
                for (id in listOf("local") + SourceStore.list.value.map { it.id })
                    readCache(ctx, id)?.let { publish(id, it) }
                cacheLoaded.value = true
            }
        }
    }

    /** Chỉ nạp cache (không quét) — dành cho LibraryProvider khi tiến trình vừa được launcher đánh thức. */
    suspend fun loadCacheOnly(ctx: Context) = ensureCacheLoaded(ctx)

    /** Nạp cache (nếu chưa) rồi quét lại bộ nhớ TV/USB. Nguồn NAS chỉ quét khi người dùng bấm. */
    suspend fun load(ctx: Context) {
        ensureCacheLoaded(ctx)
        rescanLocal(ctx)
    }

    /**
     * Quét khởi động: gọi mỗi lần Activity khởi tạo; chỉ chạy đúng một lần mỗi tiến trình, KỂ CẢ khi cache đã được nạp trước (xem [StartupScan]).
     * Chạy trên scope của Library (IO, sống lâu hơn Activity) nên không chặn UI và không bị huỷ khi Activity bị tạo lại; lỗi/huỷ giữa chừng cho phép thử lại lần sau.
     */
    fun startupScanAsync(ctx: Context) {
        if (!startup.tryBegin()) return
        val app = ctx.applicationContext
        scope.launch {
            var completed = false
            try { load(app); completed = true } finally { startup.finished(completed) }
        }
    }

    private val localPending = AtomicBoolean(false)

    suspend fun rescanLocal(ctx: Context) = withContext(Dispatchers.IO) {
        if (!localBusy.compareAndSet(false, true)) { localPending.set(true); return@withContext } // yêu cầu quét lại được xếp hàng
        val job = coroutineContext[Job]
        val ep = guard.epoch()      // hủy theo thế hệ: yêu cầu hủy sau thời điểm này dừng cả vòng quét lại đang xếp hàng
        val active = { job?.isActive != false && !guard.cancelledSince(ep) }
        try {
            do { localPending.set(false); scanLocalOnce(ctx, active) } while (localPending.get() && active())
        } finally { localBusy.set(false) }
    }

    private fun storageGranted(ctx: Context): Boolean {
        val p = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Dừng quét cục bộ giữa chừng (nhánh walk/đọc thẻ) — chỉ để thoát vòng lặp, không phải lỗi. */
    private class ScanCancelled : RuntimeException()

    /** Thông tin quét của MỘT ổ: để đánh giá độ tin cậy riêng từng ổ ([ScanPolicy.judgeRoot]). */
    private class RootScan(val root: File, val readable: Boolean) { var errors = 0; var found = 0 }

    private fun scanLocalOnce(ctx: Context, active: () -> Boolean) {
        begin()
        val rep = ScanResult.Report("local", System.currentTimeMillis())
        val oldList = cached("local")
        try {
            status.value = "Đang quét bộ nhớ TV & USB…"
            val old = oldList.associateBy { it.path }
            val view = IncrementalView(oldList, { it.path }, 1000L, System.currentTimeMillis())
            val out = ArrayList<Track>()
            val report = ArrayList<String>()
            val rs = roots(ctx)
            val seen = HashSet<String>()
            val infos = ArrayList<RootScan>()
            var reread = 0; var failedRead = 0
            scan@ for (root in rs) {
                if (!active()) { rep.cancelled = true; break }
                val readable = root.listFiles() != null
                val info = RootScan(root, readable); infos += info
                if (!readable) { info.errors++; rep.unreadable(root.path + "/") }
                val files = if (!readable) emptyList() else root.walkTopDown()
                    .onEnter { d -> !d.name.startsWith(".") && d.name != "Android" && d.name != "LOST.DIR" }
                    .onFail { f, _ -> info.errors++; rep.unreadable(f.path + "/") }       // thư mục con không đọc được: KHÔNG coi bài trong đó là đã xoá
                    .takeWhile { active() }
                    .filter { it.isFile && isAudioName(it.name) }.toList()
                info.found = files.size
                report += "${rootLabel(root)}: " + if (!readable) "không đọc được" else "${files.size}"
                for (f in files) {
                    if (!active()) { rep.cancelled = true; break@scan }
                    if (!seen.add(f.path)) continue
                    val size = f.length(); val mt = f.lastModified()
                    val o = old[f.path]
                    val t = when {
                        o != null && o.ver >= META_VER && o.mtime == mt && o.size == size -> o  // không đổi và đã có thẻ đầy đủ: giữ nguyên
                        else -> {
                            reread++
                            val disp = Recycle.displayDir(f.path)
                            val n = readMeta(f.path, "local", disp).let { if (disp.isNotEmpty()) it.copy(dir = disp) else it }
                            // đọc lỗi (không ra thời lượng) mà trước đó đã có thẻ tốt → giữ thẻ cũ, không ghi đè bằng dữ liệu rỗng
                            if (o != null && n.durationMs == 0L && o.durationMs > 0L) { failedRead++; o.copy(size = size, mtime = mt) }
                            else n.copy(size = size, mtime = mt)
                        }
                    }
                    out += t
                    // Bản xem tăng dần = danh sách cũ + bản đã thấy: không bao giờ thu hẹp; chỉ publish khi có thay đổi thật và tối đa ~1 lần/giây
                    view.offer(t)
                    val now = System.currentTimeMillis()
                    if (view.due(now)) publish("local", view.snapshot(now))
                }
            }
            if (!active()) rep.cancelled = true
            val res = rep.result(out.size, oldList.size, System.currentTimeMillis())
            // Đánh giá TỪNG ổ: ổ không đọc được / có lỗi / rỗng bất thường mà trước đó có bài → giữ bài cũ của ổ đó; ổ khác vẫn áp dụng thay đổi bình thường.
            // Bài thuộc ổ đang không có mặt (USB rút ra) không thuộc ổ nào trong danh sách → luôn giữ.
            val prefixes = infos.map { it.root.path + "/" }
            val oldCounts = IntArray(infos.size)
            for (t in oldList) { val i = ScanMerge.rootIndex(t.path, prefixes); if (i >= 0) oldCounts[i]++ }
            val granted = storageGranted(ctx)
            val states = infos.mapIndexed { i, ri ->
                val mounted = granted && runCatching { Environment.getExternalStorageState(ri.root) == Environment.MEDIA_MOUNTED }.getOrDefault(false)
                val other = ri.readable && runCatching { ri.root.list()?.isNotEmpty() == true }.getOrDefault(false)
                ScanPolicy.judgeRoot(ri.root.path, ri.readable, ri.errors, mounted, other, ri.found, oldCounts[i])
            }
            val rec = ScanMerge.reconcileRoots(oldList, out, { it.path }, res, states) { t -> val f = File(t.path); !f.exists() && f.parentFile?.list() != null }    // chỉ xoá khi file thật sự không còn và thư mục cha đọc được
            publish("local", rec.items)
            if (rec.persist) writeCache(ctx, "local", rec.items)
            if (res.outcome == ScanResult.Outcome.COMPLETE || res.outcome == ScanResult.Outcome.PARTIAL)
                runCatching { relink(old.values, rec.items) }.onFailure { AppLog.i("Library", "relink lỗi: ${it.message}") }
            val kept = rec.items.size - out.size
            if (kept > 0) report += "giữ $kept bài chưa xác nhận được"
            if (reread > 0 || failedRead > 0) AppLog.i("Library", "quét local: đọc lại $reread file, $failedRead file đọc lỗi (giữ thẻ cũ)")
            AppLog.i("Library", "quét local: ${res.outcome} · ${out.size} bài (cache ${res.previous}) · ${res.durationMs} ms · ${res.failures} lỗi/vùng không đọc được" + (if (rec.note.isNotEmpty()) " · ${rec.note}" else ""))
            status.value = (if (res.outcome == ScanResult.Outcome.COMPLETE) "Đã quét: " else "Quét ${when (res.outcome) { ScanResult.Outcome.CANCELLED -> "đã dừng"; else -> "chưa đầy đủ" }}: ") + report.joinToString(" · ") + (if (rec.note.isNotEmpty()) " · ${rec.note}" else "")
        } catch (e: Exception) {
            publish("local", oldList)       // khôi phục bản hợp lệ trước đó (bản xem tăng dần chỉ thêm, nhưng giữ đúng nguyên trạng)
            AppLog.i("Library", "quét local lỗi: ${e.message ?: e.javaClass.simpleName} — giữ nguyên dữ liệu cũ")
            status.value = "Lỗi quét bộ nhớ: ${e.message ?: e.javaClass.simpleName} — giữ nguyên dữ liệu cũ"
        } finally { end() }
    }

    /** Nguồn còn trong danh sách nguồn không (nguồn có thể bị xoá trong lúc đang quét). */
    private fun sourceExists(id: String) = SourceStore.get(id) != null

    /** Publish (+ ghi cache nếu [persist]) CHỈ khi nguồn còn tồn tại; kiểm tra và ghi nằm cùng khoá với [removeSource]. Trả false nếu nguồn đã bị xoá. */
    private fun commitSource(ctx: Context, id: String, items: List<Track>, persist: Boolean): Boolean =
        guard.commitIfPresent(id, { sourceExists(id) }) { publish(id, items); if (persist) writeCache(ctx, id, items) }

    /** Mỗi nguồn chỉ một lần quét tại một thời điểm: yêu cầu quét thứ hai khi lần đầu còn chạy bị BỎ QUA (không chạy song song, không đụng tới trạng thái hủy của lần đầu). */
    suspend fun scanSource(ctx: Context, s: Source) = withContext(Dispatchers.IO) {
        if (!guard.tryAcquire(s.id)) {
            AppLog.i("Library", "quét ${s.name}: nguồn này đang được quét — bỏ qua yêu cầu quét thêm")
            return@withContext
        }
        try { scanSourceExclusive(ctx, s) } finally { guard.release(s.id) }
    }

    private suspend fun scanSourceExclusive(ctx: Context, s: Source) {
        begin()
        val ep = guard.epoch()
        /** Dừng khi người dùng bấm "Dừng quét" SAU lúc lần quét này bắt đầu, hoặc khi nguồn đã bị xoá. */
        fun stopped() = guard.cancelledSince(ep) || !sourceExists(s.id)
        val oldList = cached(s.id)
        val rep = ScanResult.Report(s.id, System.currentTimeMillis())
        try {
            status.value = "Đang quét ${s.name}…"
            val old = oldList.associateBy { it.path }
            val view = IncrementalView(oldList, { it.path }, 1000L, System.currentTimeMillis())
            fun tick() { val n = System.currentTimeMillis(); if (view.due(n)) commitSource(ctx, s.id, view.snapshot(n), false) }
            var note = ""
            val result: List<Track> = if (s.type == "dlna") {
                val acc = ArrayList<Track>()
                note = Upnp.crawl(s, { stopped() }, { f, t, w -> status.value = "Đang quét ${s.name}: $t bài · $f thư mục · $w" }, rep) {
                    acc += it; view.offer(it); tick()
                }
                acc
            } else if (Cloud.isCloud(s.type)) {
                val files = ArrayList<CloudFile>()
                val failed = Cloud.walk(s, { stopped() }, { f, n -> status.value = "Đang liệt kê ${s.name}: $n file nhạc · $f thư mục" }, rep) { files += it }
                val acc = Collections.synchronizedList(ArrayList<Track>())
                val sem = Semaphore(4); val done = AtomicInteger(0)
                coroutineScope {
                    files.map { cf ->
                        async {
                            sem.withPermit {
                                if (!stopped()) {
                                    val t = old[cf.path]?.takeIf { it.ver >= META_VER } ?: readCloudMeta(cf, s.id).let { n -> old[cf.path]?.takeIf { n.durationMs == 0L && it.durationMs > 0 } ?: n }
                                    acc.add(t); view.offer(t)
                                    val d = done.incrementAndGet()
                                    if (d % 20 == 0) status.value = "Đọc thẻ nhạc ${s.name}: $d/${files.size}"
                                    tick()
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (stopped()) note = "đã dừng"
                if (failed > 0) note += (if (note.isEmpty()) "" else " · ") + "$failed thư mục lỗi"
                synchronized(acc) { acc.toList() }
            } else {
                val paths = ArrayList<String>()
                try {
                    // Hủy được ngay cả trong lúc liệt kê thư mục (lỗi đọc thư mục vẫn là ngoại lệ → quét THẤT BẠI, giữ nguyên dữ liệu cũ)
                    if (s.type == "smb") Remote.walkSmb(s) { if (stopped()) throw ScanCancelled(); paths += "smb://${s.id}/$it" }
                    else Remote.walkDav(s) { if (stopped()) throw ScanCancelled(); paths += "dav://${s.id}/$it" }
                } catch (e: ScanCancelled) { rep.cancelled = true }
                val acc = Collections.synchronizedList(ArrayList<Track>())
                val sem = Semaphore(4)
                coroutineScope {
                    paths.map { p ->
                        async {
                            sem.withPermit {
                                if (!stopped()) {
                                    val t = old[p]?.takeIf { it.ver >= META_VER } ?: readMeta(p, s.id).let { n -> old[p]?.takeIf { n.durationMs == 0L && it.durationMs > 0 } ?: n }
                                    acc.add(t); view.offer(t); tick()
                                }
                            }
                        }
                    }.awaitAll()
                }
                synchronized(acc) { acc.toList() }
            }
            if (stopped()) rep.cancelled = true
            // Kết quả RỖNG của nguồn từ xa không bao giờ được tin (thư mục chia sẻ rỗng khi ổ bị tháo, DLNA đang lập chỉ mục, token hết quyền…): giữ nguyên cache cũ
            rep.emptyVerified = ScanPolicy.remoteEmptyVerified(s.type)
            val res = rep.result(result.size, oldList.size, System.currentTimeMillis())
            val rec = ScanMerge.reconcile(oldList, result, { it.path }, res, null, null)
            // Nguồn bị xoá trong lúc quét: không publish, không tạo lại cache, và gỡ mọi bản ghi đã lỡ publish
            val committed = commitSource(ctx, s.id, rec.items, rec.persist)
            if (!committed) { dropIfRemoved(s.id); AppLog.i("Library", "quét ${s.name}: nguồn đã bị xoá trong lúc quét — bỏ kết quả") }
            AppLog.i("Library", "quét ${s.name}: ${res.outcome} · ${result.size} bài (cache ${res.previous}) · ${res.durationMs} ms · ${res.failures} lỗi" +
                (if (res.timedOut) " · quá thời gian" else "") + (if (res.limitReached) " · đạt giới hạn" else "") + (if (rec.note.isNotEmpty()) " · ${rec.note}" else ""))
            if (rec.note.isNotEmpty() && note.isNotEmpty() && !rec.note.contains(note)) note += " · ${rec.note}" else if (rec.note.isNotEmpty() && note.isEmpty()) note = rec.note
            status.value = "Đã quét ${s.name}: ${rec.items.size} bài" + (if (note.isNotEmpty()) " · $note" else "")
        } catch (e: Exception) {
            if (!commitSource(ctx, s.id, oldList, false)) dropIfRemoved(s.id)      // khôi phục bản hợp lệ trước đó; cache không bị đụng tới
            AppLog.i("Library", "quét ${s.name} lỗi: ${e.message ?: e.javaClass.simpleName} — giữ nguyên dữ liệu cũ")
            status.value = "Lỗi quét ${s.name}: ${e.message ?: e.javaClass.simpleName} — giữ nguyên dữ liệu cũ"
            if (e is CancellationException) throw e
        } finally { end() }
    }

    /** Gỡ bản ghi của nguồn đã bị xoá khỏi thư viện trong RAM (an toàn nếu đã gỡ; chỉ khi nguồn thật sự không còn). */
    private fun dropIfRemoved(id: String) = guard.withLock(id) { if (!sourceExists(id)) drop(id) }

    fun scanSourceAsync(ctx: Context, s: Source) { scope.launch { scanSource(ctx.applicationContext, s) } }
    fun rescanAllAsync(ctx: Context) { scope.launch { rescanLocal(ctx.applicationContext); SourceStore.list.value.forEach { scanSource(ctx.applicationContext, it) } } }
    /** Chạy giao dịch xoá nguồn trên luồng nền của Library (Dispatchers.IO), không bao giờ trên luồng gọi. */
    private val removeExec = java.util.concurrent.Executor { r -> scope.launch { r.run() } }

    /**
     * Xoá nguồn: trả về NGAY (an toàn gọi từ luồng UI). Toàn bộ giao dịch (gỡ khỏi kho nguồn, gỡ khỏi thư viện, xoá cache) chạy nền trong khoá của nguồn,
     * cùng khoá với [commitSource]: kết quả quét đang chờ publish sẽ thấy nguồn đã bị xoá và không thể đưa nó trở lại hay tạo lại cache,
     * còn kết quả vừa được xác nhận ngay trước đó bị giao dịch này dọn. Yêu cầu xoá lặp lại khi chưa xong bị bỏ qua.
     */
    fun removeSource(ctx: Context, id: String) {
        val appCtx = ctx.applicationContext
        val name = SourceStore.name(id)
        status.value = "Đang xoá nguồn $name…"
        guard.requestRemove(id, removeExec, {
            SourceStore.remove(id); drop(id); cacheFile(appCtx, id).let { it.delete(); bakFile(it).delete() }
        }) { err ->
            runCatching { Smb.reset(id) }
            if (err == null) status.value = "Đã xoá nguồn $name"
            else { AppLog.w("Library", "xoá nguồn $name lỗi: ${err.message}", err); status.value = "Lỗi khi xoá nguồn $name: ${err.message ?: err.javaClass.simpleName}" }
        }
    }

    /** FLAC: đọc thẻ + thông số thẳng từ header (Android 8 / MediaMetadataRetriever hay thiếu thẻ và thời lượng). */
    private fun flacMeta(path: String, src: String, name: String, album: String): Track? = try {
        val rd = Remote.reader(path)
        try {
            val m = FlacPcm.readMeta(ReaderSource(rd), false)
            val dur = m.durationMs()
            val kb = if (dur > 0 && rd.size > 0) (rd.size * 8 / dur).toInt() else 0
            Track(path, m.title.ifEmpty { name }, m.artist.ifEmpty { m.albumArtist }.ifEmpty { "Không rõ nghệ sĩ" },
                m.album.ifEmpty { album }, m.genre.ifEmpty { "Không rõ thể loại" }, dur, m.track, src, "flac", "", "",
                m.sampleRate, m.bps, m.channels, kb, year = m.year, disc = m.disc, albumArtist = m.albumArtist, composer = m.composer, ver = META_VER)
        } finally { rd.close() }
    } catch (e: Exception) { null }

    /** DSD (DSF/DFF): đọc tốc độ, số kênh, thời lượng và thẻ ID3 từ header. */
    private fun dsdMeta(path: String, src: String, name: String, album: String, ext: String): Track? = try {
        val rd = Remote.reader(path)
        try {
            val m = DsdPcm.readMeta(ReaderSource(rd))
            val dur = m.durationMs()
            val kb = if (dur > 0 && rd.size > 0) (rd.size * 8 / dur).toInt() else 0
            Track(path, m.title.ifEmpty { name }, m.artist.ifEmpty { "Không rõ nghệ sĩ" }, m.album.ifEmpty { album }, m.genre.ifEmpty { "Không rõ thể loại" },
                dur, 0, src, ext, "", "", m.rate, 1, m.channels, kb, ver = META_VER)
        } finally { rd.close() }
    } catch (e: Exception) { null }

    /** File trên đám mây: đường dẫn chỉ là id, nên tên file và thư mục phải truyền riêng. */
    private fun readCloudMeta(f: CloudFile, src: String): Track {
        val t = readMeta(f.path, src, f.dir.ifEmpty { SourceStore.name(src) }, f.name)
        return t.copy(dir = f.dir, format = t.format.ifEmpty { Cloud.extOf(f.name) })
    }

    fun readMeta(path: String, src: String, dispDir: String = "", fileName: String? = null): Track {
        val name = (fileName ?: path.substringAfterLast('/')).substringBeforeLast('.')
        val album = dispDir.substringAfterLast('/').ifEmpty { dirLabel(path.substringBeforeLast('/')) }
        val low = (fileName ?: path).lowercase()
        if (low.endsWith(".flac")) flacMeta(path, src, name, album)?.let { return it }
        if (low.endsWith(".dsf") || low.endsWith(".dff")) dsdMeta(path, src, name, album, low.substringAfterLast('.'))?.let { return it }
        return try {
            Media.withMmr(path) { r ->
                fun g(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
                Track(
                    path, g(MMR.METADATA_KEY_TITLE) ?: name,
                    g(MMR.METADATA_KEY_ARTIST) ?: g(MMR.METADATA_KEY_ALBUMARTIST) ?: "Không rõ nghệ sĩ",
                    g(MMR.METADATA_KEY_ALBUM) ?: album, g(MMR.METADATA_KEY_GENRE) ?: "Không rõ thể loại",
                    g(MMR.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    g(MMR.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0, src,
                    year = g(MMR.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()?.takeIf { it in 1000..2999 } ?: 0,
                    disc = g(MMR.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0,
                    albumArtist = g(MMR.METADATA_KEY_ALBUMARTIST) ?: "", composer = g(MMR.METADATA_KEY_COMPOSER) ?: "", ver = META_VER,
                )
            }
        } catch (e: Exception) { Track(path, name, "Không rõ nghệ sĩ", album, "Không rõ thể loại", 0L, 0, src, ver = META_VER) }
    }

    private fun toJson(l: List<Track>): String {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("p", it.path).put("t", it.title).put("a", it.artist).put("b", it.album).put("g", it.genre).put("d", it.durationMs).put("n", it.no)
            .put("s", it.src).put("f", it.format).put("r", it.art).put("dr", it.dir).put("sr", it.sr).put("bt", it.bits).put("ch", it.ch).put("kb", it.kbps).put("mi", it.mime).put("sz", it.size).put("mt", it.mtime)
            .put("yr", it.year).put("dc", it.disc).put("aa", it.albumArtist).put("cp", it.composer).put("v", it.ver)) }
        return a.toString()
    }
    /** Đọc từng bản ghi riêng: bản ghi hỏng bị bỏ qua (có log) chứ không làm mất cả cache. Khóa mới đều tùy chọn → cache cũ đọc bình thường. */
    private fun fromJson(s: String): List<Track> {
        val a = JSONArray(s)
        val out = ArrayList<Track>(a.length()); var bad = 0
        for (i in 0 until a.length()) {
            val t = runCatching {
                val o = a.getJSONObject(i)
                val p = o.getString("p").also { require(it.isNotEmpty()) }
                Track(p, o.optString("t").ifEmpty { p.substringAfterLast('/').substringBeforeLast('.') },
                    o.optString("a").ifEmpty { "Không rõ nghệ sĩ" }, o.optString("b"), o.optString("g").ifEmpty { "Không rõ thể loại" },
                    o.optLong("d"), o.optInt("n"),
                    o.optString("s", "local"), o.optString("f"), o.optString("r"), o.optString("dr"), o.optInt("sr"), o.optInt("bt"), o.optInt("ch"), o.optInt("kb"),
                    o.optString("mi"), o.optLong("sz"), o.optLong("mt"),
                    o.optInt("yr"), o.optInt("dc"), o.optString("aa"), o.optString("cp"), o.optInt("v"))
            }.getOrNull()
            if (t != null) out += t else bad++
        }
        if (bad > 0) AppLog.i("Library", "cache: bỏ qua $bad bản ghi hỏng")
        return out
    }

    /** Đĩa → số bài → tên tự nhiên (xem [TrackOrder]): "2" trước "10", đĩa 2 không xen lẫn đĩa 1. */
    private fun sorted(v: List<Track>) = v.sortedWith { x, y -> TrackOrder.compare(x.disc, x.no, x.path, y.disc, y.no, y.path) }

    fun groups(tab: Tab, all: List<Track>): List<Group> = when (tab) {
        Tab.ALBUM -> all.groupBy { it.album }.map { (k, v) ->
            val ar = v.map { it.artist }.distinct()
            Group(k, k, if (ar.size == 1) ar[0] else "Nhiều nghệ sĩ", sorted(v))
        }
        Tab.ARTIST -> all.groupBy { it.artist }.map { (k, v) -> Group(k, k, "${v.map { it.album }.distinct().size} album · ${v.size} bài", sorted(v)) }
        Tab.GENRE -> all.groupBy { it.genre }.map { (k, v) -> Group(k, k, "${v.size} bài", sorted(v)) }
        Tab.FOLDER -> all.groupBy { it.folder }.map { (k, v) ->
            val t = if (v[0].dir.isNotEmpty()) v[0].dir.substringAfterLast('/') else k.substringAfterLast('/')
            Group(k, t, "${v.size} bài · ${v[0].sourceName}", sorted(v))
        }
        Tab.ALBUMARTIST -> all.groupBy { it.albumArtist.ifEmpty { it.artist } }.map { (k, v) -> Group(k, k, "${v.map { it.album }.distinct().size} album · ${v.size} bài", sorted(v)) }.sortedBy { it.title.lowercase() }
        Tab.YEAR -> all.groupBy { it.year }.map { (y, v) -> Group(y.toString(), if (y > 0) y.toString() else "Không rõ năm", "${v.map { it.album }.distinct().size} album · ${v.size} bài", sorted(v)) }.sortedByDescending { it.key.toInt().let { y -> if (y > 0) y else -1 } }
        Tab.SONG, Tab.FAV, Tab.HABIT, Tab.RECENT, Tab.HISTORY, Tab.PLAYLIST -> emptyList()
    }.sortedBy { it.title.lowercase() }
}
