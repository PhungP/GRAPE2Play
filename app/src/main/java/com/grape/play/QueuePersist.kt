package com.grape.play

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.Executors

/**
 * Lưu và khôi phục hàng đợi phát (xem [QueueStore], [QueueSession]). Chỉ QUAN SÁT các StateFlow của [Playback] và ghi file ở luồng nền riêng;
 * không chạm đường âm thanh. Khôi phục: sau khi cache thư viện đã nạp, chỉ khi người dùng chưa đụng vào hàng đợi, ở trạng thái TẠM DỪNG.
 * Mọi trạng thái của [session] chỉ truy cập trên luồng chính; mọi trạng thái cache/ghi file chỉ trên [exec].
 */
@OptIn(FlowPreview::class)
object QueuePersist {
    private const val TAG = "Queue"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "QueuePersist").apply { isDaemon = true } }
    private var dir: File? = null
    private var started = false
    private val session = QueueSession()
    private val seeks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private fun queueFile() = File(dir, "queue.grq")
    private fun stateFile() = File(dir, "queue_state.grs")

    /** Danh tính bài = nguồn + đường dẫn (đường dẫn đám mây chỉ là id, local/USB là đường dẫn tuyệt đối, NAS có id nguồn trong đường dẫn). */
    private fun key(t: Track) = t.src + "\t" + t.path

    // Chỉ dùng trên [exec]
    private var cachedSrc: Any? = null
    private var cachedKeys: List<String> = emptyList()
    private var cachedCrc = 0L
    private var writtenQueueCrc: Long? = null
    private var lastState: String? = null

    /** Gọi một lần, sau khi Playback.exo đã dựng. */
    fun start(ctx: Context) {
        if (started) return
        started = true
        dir = ctx.applicationContext.filesDir
        scope.launch { restore() }
        scope.launch {
            combine(Playback.queue, Playback.index, Playback.shuffle, Playback.repeat, Playback.playing) { q, _, _, _, _ -> session.observe(q.isNotEmpty()) }
                .debounce(400).collect { save() }
        }
        scope.launch { seeks.debounce(1000).collect { save() } }
        scope.launch { while (true) { delay(10_000); if (Playback.playing.value) save() } }
    }

    /** Người dùng vừa tua: lưu vị trí (debounce 1 s, gộp nhiều lần tua thành một lần ghi file nhỏ). Gọi từ luồng chính. */
    fun touch() { seeks.tryEmit(Unit) }

    /** Chụp trạng thái trên luồng chính, ghi ở luồng nền. */
    private fun save() {
        val q = Playback.queue.value
        val plan = session.plan(q, q.size, Playback.index.value)
        if (plan.action == QueueSession.Action.SKIP) return
        if (plan.action == QueueSession.Action.WRITE_EMPTY) {
            exec.execute {
                try {
                    QueueStore.writeAtomic(queueFile(), QueueStore.encodeQueue(emptyList()))
                    stateFile().delete()
                    cachedSrc = null; writtenQueueCrc = null; lastState = null
                } catch (e: Exception) { AppLog.i(TAG, "không ghi được trạng thái hàng đợi rỗng: ${e.message}") }
            }
            return
        }
        val pos = Playback.position.value.coerceAtLeast(0L)
        val sh = Playback.shuffle.value; val rep = Playback.repeat.value.coerceIn(0, 2)
        exec.execute {
            try {
                val src: Any = if (plan.useBaseline) plan.baselineKeys!! else q
                if (src !== cachedSrc) {
                    cachedKeys = if (plan.useBaseline) plan.baselineKeys!! else q.map { key(it) }
                    cachedCrc = QueueStore.crc(cachedKeys); cachedSrc = src
                }
                if (writtenQueueCrc != cachedCrc) {
                    QueueStore.writeAtomic(queueFile(), QueueStore.encodeQueue(cachedKeys))
                    writtenQueueCrc = cachedCrc
                }
                val st = QueueStore.encodeState(QueueStore.State(plan.index, pos, sh, rep, cachedKeys.size, cachedCrc))
                if (st != lastState) { QueueStore.writeAtomic(stateFile(), st); lastState = st }
            } catch (e: Exception) { AppLog.i(TAG, "không lưu được hàng đợi: ${e.message}") }
        }
    }

    private class Saved(val keys: List<String>, val state: QueueStore.State)

    private fun load(): Saved? {
        val keys = QueueStore.decodeQueue(QueueStore.read(queueFile())) ?: return null
        if (keys.isEmpty()) return null      // rỗng tường minh (người dùng đã xoá) hoặc không có gì để khôi phục
        return Saved(keys, QueueStore.reconcile(QueueStore.decodeState(QueueStore.read(stateFile())), keys))
    }

    private suspend fun restore() {
        val saved = withContext(Dispatchers.IO) { runCatching { load() }.getOrNull() } ?: return
        Library.cacheLoaded.first { it }
        if (!session.canRestore() || Playback.queue.value.isNotEmpty()) return       // người dùng đã tự phát / xoá hàng đợi trong lúc chờ thư viện
        val resolved = withContext(Dispatchers.Default) {
            val byKey = HashMap<String, Track>()
            for (t in Library.tracks.value) byKey[key(t)] = t
            val present = BooleanArray(saved.keys.size) { byKey.containsKey(saved.keys[it]) }
            QueueStore.resolve(present, saved.state.index, saved.state.positionMs)?.let { r -> r to r.kept.map { byKey.getValue(saved.keys[it]) } }
        }
        if (resolved == null) { AppLog.i(TAG, "hàng đợi đã lưu (${saved.keys.size} bài): chưa có bài nào trong thư viện — giữ nguyên bản đã lưu"); return }
        if (!session.canRestore()) return        // (kiểm lại sau khi chuyển luồng)
        val (r, tracks) = resolved
        val cur = tracks[r.index]
        val pos = if (cur.durationMs > 0 && r.positionMs >= cur.durationMs) 0L else r.positionMs
        if (Playback.restoreQueue(tracks, r.index, pos, saved.state.shuffle, saved.state.repeat)) {
            session.restored(tracks, saved.keys, r.kept)   // ngay sau restoreQueue, cùng lượt luồng chính: trước mọi lần save()
            val miss = saved.keys.size - tracks.size
            AppLog.i(TAG, "khôi phục hàng đợi: ${tracks.size}/${saved.keys.size} bài${if (miss > 0) " (còn $miss bài chưa tìm thấy — vẫn giữ trong bản đã lưu)" else ""}, bài #${r.index}${if (r.currentMissing) " (bài đang nghe không còn, chuyển bài kế)" else ""}, vị trí ${pos / 1000}s, tạm dừng")
        }
    }
}
