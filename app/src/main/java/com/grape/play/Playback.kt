@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.grape.play

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.io.IOException
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class SinkFmt(val sampleRate: Int, val channels: Int, val encoding: Int)

object Playback {
    lateinit var exo: ExoPlayer
    val level = LevelProcessor()
    val dsp = DspProcessor()
    val queue = MutableStateFlow<List<Track>>(emptyList())
    val index = MutableStateFlow(0)
    val playing = MutableStateFlow(false)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val info = MutableStateFlow<AudioInfo?>(null)
    val error = MutableStateFlow<String?>(null)
    val outName = MutableStateFlow("Mặc định hệ thống")
    private var probeJob: Job? = null
    /** Thiết bị stream không dây đang nhận nhạc (null = phát ra đầu ra của TV). */
    val remote = MutableStateFlow<Renderer?>(null)
    /** Giao diện đang hiện trên màn hình (false khi đã bấm Home/Back): dùng cho nhật ký chẩn đoán chạy nền. */
    @Volatile var uiVisible = true
    /** Lần cuối NGƯỜI DÙNG chủ động tạm dừng (phím media / nút thông báo) — để phân biệt với việc thiết bị UPnP tự tạm dừng khi app rời màn hình. */
    @Volatile var userPauseAt = 0L
    /** Thời điểm giao diện bị ẩn (onStop), 0 = đang hiện — chỉ để chẩn đoán. */
    @Volatile var hiddenAt = 0L
    private var rc: RemoteController? = null
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var appCtx: Context
    /** Context ứng dụng (chỉ đọc) — dùng cho chẩn đoán Bit-perfect. */
    val context: Context get() = appCtx
    /** Định dạng PCM ExoPlayer đưa vào bộ xuất âm thanh (sau giải mã). Chỉ quan sát, không ảnh hưởng luồng âm thanh. */
    val sinkFmt = MutableStateFlow<SinkFmt?>(null)

    private val retried = HashSet<String>()
    /** Số lần thử lại tại chỗ (cùng bài, cùng vị trí) cho lỗi đầu ra/đường truyền thoáng qua; xoá khi bài đã phát ổn định > 8 giây. */
    private val retries = PlayLogic.Retries()
    /** Đang chờ tự phục hồi sau lỗi (thử lại tại chỗ / sao chép từ ổ): người dùng bấm Phát-Tạm dừng lúc này nghĩa là HUỶ phục hồi và tạm dừng, không phải bắt đầu phát. */
    @Volatile private var pendingRecovery = false
    private var recoverJob: Job? = null
    private var errCount = 0
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Đo thời gian chuyển bài: từ lúc bấm đến lúc thật sự có tiếng; quá 3 giây thì báo đường truyền không ổn định ----
    const val SWITCH_LIMIT_MS = 3000L
    val slow = MutableStateFlow<String?>(null)
    @Volatile private var switchAt = 0L
    private fun markSwitch() { switchAt = SystemClock.elapsedRealtime() }
    private fun checkSwitch() {
        val s = switchAt; if (s == 0L) return
        switchAt = 0
        val el = SystemClock.elapsedRealtime() - s
        if (el > SWITCH_LIMIT_MS) {
            slow.value = "Tốc độ đường truyền không ổn định: chuyển bài mất %.1f giây (mong muốn dưới 3 giây)".format(el / 1000.0)
            main.launch { delay(8000); if (slow.value?.startsWith("Tốc độ") == true) slow.value = null }
        } else slow.value = null
    }

    // ---- Chẩn đoán chuyển bài tự động. CHỈ quan sát được hai dấu hiệu từ phía app: (1) bộ xuất âm thanh có nhận lệnh configure() hay không,
    // (2) định dạng PCM đưa vào có đổi hay không. App KHÔNG đo được khoảng lặng thực tế nên không bao giờ kết luận "gapless hoàn toàn". ----
    @Volatile private var sinkCfg = ""           // định dạng PCM vào bộ xuất âm thanh gần nhất
    @Volatile private var sinkCalls = 0          // số lần configure() được gọi
    @Volatile private var sinkFmtChanges = 0     // số lần định dạng PCM thực sự đổi
    @Volatile private var sinkPrev = ""          // định dạng ngay trước lần đổi gần nhất
    private var callsAtMark = 0; private var chgAtMark = 0; private var fmtAtMark = ""
    private fun noteSink(f: Format) {
        val c = "${f.sampleRate}Hz/${f.channelCount}ch/" + when (f.pcmEncoding) { C.ENCODING_PCM_16BIT -> "16bit"; C.ENCODING_PCM_24BIT -> "24bit"; C.ENCODING_PCM_32BIT -> "32bit"; C.ENCODING_PCM_FLOAT -> "float32"; else -> "enc${f.pcmEncoding}" }
        sinkCalls++
        if (f.pcmEncoding != C.ENCODING_INVALID) sinkFmt.value = SinkFmt(f.sampleRate, f.channelCount, f.pcmEncoding)
        if (c != sinkCfg) { sinkPrev = sinkCfg; sinkCfg = c; sinkFmtChanges++ }
    }
    private var gaplessJob: Job? = null
    /** Chuyển bài: [auto] = ExoPlayer tự qua bài (không phải người dùng bấm). Đợi 1 giây để lệnh configure() của bài mới (nếu có) kịp đến rồi mới so sánh. */
    private fun logGaplessResult(auto: Boolean) {
        gaplessJob?.cancel()
        gaplessJob = main.launch {
            delay(1000)
            val calls = sinkCalls - callsAtMark; val chg = sinkFmtChanges - chgAtMark; val was = fmtAtMark
            callsAtMark = sinkCalls; chgAtMark = sinkFmtChanges; fmtAtMark = sinkCfg
            if (!auto || was.isEmpty()) return@launch
            val note = "Chưa đo được khoảng lặng thực tế."
            when (PlayLogic.transitionKind(chg, calls)) {
                2 -> AppLog.i("Player", "Chuyển bài tự động: định dạng PCM đầu ra đổi ($was → $sinkCfg) và bộ xuất âm thanh được cấu hình lại — nhiều khả năng có khoảng lặng ngắn. $note")
                1 -> AppLog.i("Player", "Chuyển bài tự động: bộ xuất âm thanh được cấu hình lại dù định dạng PCM không đổi ($sinkCfg). $note")
                else -> AppLog.i("Player", "Chuyển bài tự động: không phát hiện thay đổi định dạng đầu ra ($sinkCfg) và bộ xuất âm thanh không được cấu hình lại. $note")
            }
        }
    }

    private fun detail(e: Throwable): String {
        val sb = StringBuilder(); var t: Throwable? = e; var n = 0
        while (t != null && n < 3) { sb.append(t.javaClass.simpleName).append(": ").append((t.message ?: "").take(110)).append("  |  "); t = t.cause; n++ }
        return sb.toString()
    }
    /** Sau lỗi: sang bài kế (xem PlayLogic.skipTarget). */
    private fun skipNext() {
        val to = PlayLogic.skipTarget(exo.repeatMode, exo.currentMediaItemIndex, exo.mediaItemCount, exo.nextMediaItemIndex.let { if (it == C.INDEX_UNSET) -1 else it })
        if (to >= 0) { exo.seekTo(to, 0L); exo.prepare(); exo.play() }
    }

    /** Hẹn thử lại cùng bài, cùng vị trí sau [delayMs]. Chỉ tiếp tục nếu người dùng chưa tạm dừng/đổi bài trong lúc chờ. */
    private fun scheduleRetry(d: PlayLogic.Decision, cur: Int, pos: Long, why: String, max: Int) {
        AppLog.w("Player", "$why — thử lại lần ${d.attempt}/$max sau ${d.delayMs}ms tại ${fmt(pos)}")
        error.value = "$why — đang thử lại (${d.attempt}/$max)…"
        pendingRecovery = true
        recoverJob?.cancel()
        recoverJob = main.launch {
            delay(d.delayMs)
            val go = pendingRecovery && rc == null && exo.playWhenReady && exo.playbackState == Player.STATE_IDLE && exo.currentMediaItemIndex == cur && cur < exo.mediaItemCount
            pendingRecovery = false     // luôn kết thúc trạng thái chờ, kể cả khi người dùng đã đổi ý
            if (go) { exo.prepare(); exo.seekTo(cur, pos) }      // giữ nguyên playWhenReady (không ép phát)
        }
    }

    private fun isTransientIo(e: PlaybackException) =
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    private fun copyToCache(t: Track): File {
        val dir = File(appCtx.cacheDir, "nho_play").apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
        val src = File(t.path)
        if (dir.usableSpace < src.length() + 64L * 1024 * 1024) throw IOException("Không đủ dung lượng bộ nhớ đệm")
        val out = File(dir, "${t.path.hashCode()}.${t.ext.ifEmpty { "bin" }}")
        src.inputStream().use { i -> out.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
        return out
    }

    /** ExoPlayer cũng báo lỗi của bài đang nạp trước (bài kế tiếp); tìm đúng chỉ số bài bị lỗi. */
    private fun failingIndex(e: PlaybackException): Int? {
        val mid = (e as? ExoPlaybackException)?.mediaPeriodId ?: return null
        val tl = exo.currentTimeline
        val pi = tl.getIndexOfPeriod(mid.periodUid)
        if (pi < 0) return null
        return tl.getPeriod(pi, Timeline.Period()).windowIndex
    }

    private fun isSinkError(e: PlaybackException) =
        e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED

    /** Sau lỗi bộ phát về trạng thái nghỉ (IDLE) và sẽ không tự chạy lại: mọi thao tác của người dùng phải nạp lại trước khi phát. */
    private fun ensureReady() {
        if (::exo.isInitialized && exo.mediaItemCount > 0 && exo.playbackState == Player.STATE_IDLE) exo.prepare()
    }

    /** Dựng lại toàn bộ danh sách (để các bài nạp trước cũng lấy cấu hình mới) rồi phát tiếp bài [i] từ vị trí [pos]. */
    private fun reloadLocal(i: Int, pos: Long) {
        val q = queue.value
        if (q.isEmpty()) return
        val k = i.coerceIn(0, q.lastIndex)
        exo.setMediaItems(q.map { MediaItem.fromUri(Remote.toUri(it)) }, k, pos)
        if (exo.shuffleModeEnabled) pinShuffle(k)
        index.value = k
        exo.prepare(); exo.play()
    }

    /** Thứ tự xáo trộn của ExoPlayer là ngẫu nhiên hoàn toàn: bài đang nghe có thể nằm giữa thứ tự, khi tắt lặp thì các bài đứng trước nó sẽ KHÔNG BAO GIỜ được phát. Ghim bài đang nghe lên đầu rồi xáo phần còn lại. */
    private var shuffleIdx = IntArray(0)   // bản sao thứ tự xáo trộn đã đặt cho ExoPlayer (để giữ nguyên khi thêm bài)
    private fun applyShuffle(order: IntArray) {
        if (order.size != exo.mediaItemCount || order.size < 2) return
        shuffleIdx = order
        exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, SystemClock.elapsedRealtimeNanos()))
    }
    private fun pinShuffle(first: Int) {
        val n = exo.mediaItemCount
        if (n < 2 || first !in 0 until n) return
        applyShuffle(PlayLogic.pinnedOrder(n, first, java.util.Random()))
    }

    /** Xoá trạng thái lỗi cũ: gọi khi người dùng chủ động chọn bài / đầu ra / thư mục khác để lần thử mới bắt đầu sạch. */
    private fun clearFailure() { error.value = null; errCount = 0; retried.clear(); retries.clear(); pendingRecovery = false; recoverJob?.cancel() }

    /** Lỗi phát: hiện chi tiết; với file trên ổ USB/ổ cứng thì thử sao chép sang bộ nhớ đệm rồi phát lại; tránh vòng lặp lỗi vô tận. */
    private fun handleError(e: PlaybackException) {
        val cur = exo.currentMediaItemIndex
        val fi = failingIndex(e) ?: cur
        val preload = fi != cur
        val t = queue.value.getOrNull(fi)
        val pos = exo.currentPosition
        val where = t?.let { "${it.path.takeLast(70)} @ ${if (preload) "bài kế tiếp" else fmt(pos)}" } ?: ""
        val head = "${e.errorCodeName}\n${detail(e)}\n$where"
        AppLog.e("Player", "$head | đầu ra=${outName.value}", e)
        if (preload) { exo.prepare() } // tiếp tục bài đang nghe (giữ nguyên trạng thái phát/tạm dừng của người dùng), bài lỗi xử lý ngầm
        val sink = isSinkError(e)
        val usb = t != null && t.path.startsWith("/storage/") && !t.path.startsWith("/storage/emulated")
        val d = PlayLogic.decide(preload, sink, t != null && StreamServer.isDsdFile(t), AudioSettings.effectiveDsdRate() > 44100, usb,
            t != null && usb && t.path !in retried, isTransientIo(e), errCount, queue.value.size, t?.path ?: cur.toString(), retries)
        when (d.action) {
            // Đầu ra không nhận tín hiệu (cổng quang/TV không nhận PCM tốc độ cao…): với DSD tự hạ tốc độ chuyển PCM cho riêng đầu ra này rồi phát lại ngay
            PlayLogic.Action.DSD_LOWER -> {
                val from = AudioSettings.effectiveDsdRate()
                val to = AudioSettings.lowerDsdRate()
                if (to != null) {
                    AppLog.w("Player", "Đầu ra ${outName.value} không nhận DSD→PCM ${khz(from)}: hạ xuống ${khz(to)} và phát lại")
                    reloadLocal(cur, pos)
                    error.value = "Đầu ra ${outName.value} không nhận PCM ${khz(from)} — đã tự hạ xuống ${khz(to)} và phát lại."
                }
            }
            // Lỗi AudioTrack thoáng qua (vừa đổi/cắm/rút thiết bị, HDMI đang bắt tay lại): thử lại ĐÚNG bài đang nghe tại vị trí cũ trước khi bỏ qua bài
            PlayLogic.Action.RETRY_SINK -> scheduleRetry(d, cur, pos, "Đầu ra ${outName.value} chưa nhận tín hiệu", 2)
            PlayLogic.Action.SINK_STOP, PlayLogic.Action.SINK_SKIP, PlayLogic.Action.SINK_PRELOAD -> {
                error.value = "Đầu ra ${outName.value} không nhận được tín hiệu của bài này. Hãy chọn bài/đầu ra khác (bấm “Đầu ra” ở góc trên) — app sẽ phát lại ngay."
                errCount++
                if (d.action == PlayLogic.Action.SINK_STOP) exo.stop() else if (d.action == PlayLogic.Action.SINK_SKIP) skipNext()
            }
            PlayLogic.Action.USB_COPY -> {
                retried.add(t!!.path)
                error.value = "Lỗi khi đọc từ ổ — đang sao chép file sang bộ nhớ đệm để thử lại…\n$head"
                pendingRecovery = true
                ioScope.launch {
                    val r = runCatching { copyToCache(t) }
                    withContext(Dispatchers.Main) {
                        val f = r.getOrNull()
                        if (f != null && fi < exo.mediaItemCount) {
                            exo.replaceMediaItem(fi, MediaItem.fromUri(Uri.fromFile(f)))
                            // Người dùng đã tạm dừng/đổi bài trong lúc sao chép thì không tự phát lại
                            if (!preload && pendingRecovery && exo.playWhenReady && exo.currentMediaItemIndex == fi) { exo.prepare(); exo.seekTo(fi, pos) }
                            pendingRecovery = false
                            error.value = "Đã phát lại từ bản sao trong bộ nhớ đệm (lỗi gốc: ${e.errorCodeName})"
                        } else {
                            pendingRecovery = false
                            error.value = "Không sao chép được file từ ổ: ${r.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message } ?: "danh sách đã đổi"}\n$head"
                            if (!preload) skipNext()
                        }
                    }
                }
            }
            // Mất nguồn thoáng qua (NAS/mạng/ổ chập chờn): chờ rồi thử lại đúng chỗ đang nghe trước khi bỏ qua bài
            PlayLogic.Action.RETRY_IO -> scheduleRetry(d, cur, pos, if (d.attempt == 1) "Mất kết nối nguồn nhạc" else "Nguồn nhạc vẫn chưa trả lời", 2)
            PlayLogic.Action.PRELOAD_IGNORE -> error.value = head
            PlayLogic.Action.STOP_MANY -> { errCount++; exo.stop(); error.value = head + "\nĐã dừng vì nhiều file liên tiếp bị lỗi." }
            PlayLogic.Action.SKIP -> { error.value = head; errCount++; skipNext() }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (rc == null) { playing.value = isPlaying; if (isPlaying) checkSwitch() } }
        override fun onMediaItemTransition(m: MediaItem?, reason: Int) {
            // Người gọi (play/loadRemote/...) tự gọi sync(); chỉ xử lý chuyển bài thật. Sửa danh sách không được tính là "bắt đầu nghe" lại.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || rc != null) { if (rc == null) index.value = exo.currentMediaItemIndex; return }
            logGaplessResult(reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            sync()
        }
        override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY && exo.duration > 0) duration.value = exo.duration }
        override fun onShuffleModeEnabledChanged(b: Boolean) { shuffle.value = b }
        override fun onRepeatModeChanged(m: Int) { repeat.value = m }
        override fun onPlayerError(e: PlaybackException) { handleError(e) }
    }

    fun init(ctx: Context) {
        if (::exo.isInitialized) return
        appCtx = ctx.applicationContext
        Renderers.app = appCtx
        AppLog.init(appCtx)
        AudioSettings.init(appCtx)
        AudioSettings.applyStartup()   // đầu ra mặc định khi mở app (nếu người dùng đã chọn)
        DspParams.init(appCtx)
        Lang.init(appCtx)
        DisplayPrefs.init(appCtx)
        Themes.init(appCtx)
        Covers.init(appCtx)
        Favorites.init(appCtx)
        Recents.init(appCtx); UsageStats.init(appCtx); Ai.init(appCtx); UserPlaylists.init(appCtx); TagFix.init(appCtx); AiDj.init(appCtx); AiSaver.init(appCtx); LibNav.init(appCtx); SourceOrder.init(appCtx)
        TvHome.init(appCtx)
        BackgroundAccess.init(appCtx)
        exo = build()
        // Thiết bị đầu ra cắm/rút (USB DAC, HDMI…): áp lại lựa chọn đầu ra ưu tiên.
        runCatching {
            (appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { reapplyDevice() }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    pauseIfSelectedOutputLost(removedDevices)
                    reapplyDevice()
                }
            }, Handler(Looper.getMainLooper()))
        }
        Keepalive.init(appCtx)
        QueuePersist.start(appCtx)   // lưu/khôi phục hàng đợi (chỉ quan sát, không chạm luồng âm thanh)
        main.launch {
            while (true) { if (exo.isPlaying) { position.value = exo.currentPosition; if (position.value > 8000) { errCount = 0; if (!retries.isEmpty()) retries.clear() }; if (position.value > 4000 && error.value != null) error.value = null }; delay(250) }
        }
    }

    private fun build(): ExoPlayer {
        val rf = object : DefaultRenderersFactory(appCtx) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                val base = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(dsp))
                    .build()
                // Đồng hồ VU đo ở cửa vào bộ xuất âm thanh để luôn thấy PCM gốc (kể cả 24-bit bị ExoPlayer hạ xuống 16-bit bên trong)
                return object : ForwardingAudioSink(base) {
                    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
                        level.configure(inputFormat, AudioSettings.hires)
                        noteSink(inputFormat)
                        super.configure(inputFormat, specifiedBufferSize, outputChannels)
                    }
                    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
                        level.tap(buffer)
                        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                    }
                }
            }
        }
        rf.setEnableAudioFloatOutput(AudioSettings.hires)
        val p = ExoPlayer.Builder(appCtx, rf)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DataSourceFactoryHolder.factory(appCtx), DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .apply {
                if (AudioSettings.gapless)
                    setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(60_000, 90_000, 1_500, 3_000).setPrioritizeTimeOverSizeThresholds(true).build())
            }
            .build()
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.addListener(listener)
        AudioSettings.current(appCtx)?.let { runCatching { p.setPreferredAudioDevice(it) } }
        outName.value = AudioSettings.currentName(appCtx)
        return p
    }

    /**
     * Thiết bị đầu ra NGƯỜI DÙNG ĐÃ CHỌN (USB DAC, HDMI…) bị rút khi đang phát cục bộ: tạm dừng thay vì để âm thanh tự chuyển sang loa/đầu ra mặc định
     * (có thể rất lớn hoặc không đúng ý). Chỉ tạm dừng — không đổi đường âm thanh, không đổi định dạng. Gọi TRƯỚC khi áp lại thiết bị ưu tiên.
     * Không làm gì khi: người dùng để mặc định, đang phát tới thiết bị ngoài, đang tạm dừng, hoặc thiết bị bị rút không phải thiết bị đã chọn.
     */
    private fun pauseIfSelectedOutputLost(removed: Array<out AudioDeviceInfo>?) {
        if (!::exo.isInitialized || rc != null || removed.isNullOrEmpty() || !exo.isPlaying) return
        val key = AudioSettings.deviceKey
        if (key.isEmpty() || AudioSettings.match(key, removed.toList()) == null) return
        exo.pause()
        AppLog.w("Player", "Thiết bị đầu ra đã chọn (${AudioSettings.currentName(appCtx)}) bị rút khi đang phát — đã tạm dừng, không tự chuyển sang đầu ra khác")
        error.value = "Thiết bị đầu ra đã chọn bị rút — đã tạm dừng. Cắm lại thiết bị hoặc chọn đầu ra khác rồi bấm phát."
    }

    private fun reapplyDevice() {
        if (!::exo.isInitialized || rc != null) return
        runCatching { exo.setPreferredAudioDevice(AudioSettings.current(appCtx)) }
        outName.value = AudioSettings.currentName(appCtx)
        retryAfterFailure()
    }

    /** Đầu ra vừa đổi sau một lỗi phát: thử lại bài đang chọn ngay trên đầu ra mới, không bắt người dùng bấm thêm. */
    private fun retryAfterFailure() {
        if (rc != null || error.value == null || queue.value.isEmpty() || exo.playbackState != Player.STATE_IDLE) return
        clearFailure()
        AppLog.i("Player", "Đổi đầu ra sang ${outName.value} sau lỗi: thử phát lại")
        exo.prepare(); exo.play()
    }

    /** Áp dụng lại cài đặt âm thanh: dựng lại bộ phát, giữ nguyên danh sách, bài và vị trí đang nghe. */
    fun applySettings() {
        val q = queue.value; val i = exo.currentMediaItemIndex; val pos = if (rc != null) position.value else exo.currentPosition
        val failed = rc == null && error.value != null && exo.playbackState == Player.STATE_IDLE
        val was = if (rc != null) playing.value else (exo.isPlaying || failed); val sh = exo.shuffleModeEnabled; val rep = exo.repeatMode
        if (failed) clearFailure()
        exo.release()
        exo = build()
        exo.repeatMode = rep; exo.shuffleModeEnabled = sh
        if (q.isNotEmpty()) {
            val k = i.coerceIn(0, q.lastIndex)
            exo.setMediaItems(q.map { MediaItem.fromUri(Remote.toUri(it)) }, k, pos)
            if (sh) pinShuffle(k) else shuffleIdx = IntArray(0)
            if (rc == null) { exo.prepare(); if (was) exo.play() }
            index.value = k
        }
        if (rc == null) playing.value = was
        shuffle.value = sh; repeat.value = rep
        remote.value?.let { outName.value = it.label }
    }

    /** Bài [t] bắt đầu được nghe: ghi vào "phát gần đây", làm mới hàng trên trang chủ TV và đọc thông số kỹ thuật của file ở luồng nền. */
    private fun startProbe(t: Track) {
        info.value = null
        Recents.add(t.path); UsageStats.played(t.path); TvHome.schedule(appCtx, 30_000)
        probeJob?.cancel()
        probeJob = ioScope.launch { info.value = Probe.probe(t) }
    }

    private fun sync() {
        val i = exo.currentMediaItemIndex
        index.value = i; position.value = 0
        val t = queue.value.getOrNull(i) ?: return
        duration.value = t.durationMs
        startProbe(t)
    }

    fun play(tracks: List<Track>, start: Int, shuffleOn: Boolean? = null) {
        if (tracks.isEmpty()) return
        val start = start.coerceIn(0, tracks.lastIndex)
        shuffleOn?.let { exo.shuffleModeEnabled = it }
        queue.value = tracks; clearFailure(); markSwitch()
        exo.setMediaItems(tracks.map { MediaItem.fromUri(Remote.toUri(it)) }, start, 0L)
        if (exo.shuffleModeEnabled) pinShuffle(start) else shuffleIdx = IntArray(0)
        AppLog.i("Player", "Phát danh sách ${tracks.size} bài từ #$start · ${tracks.getOrNull(start)?.ext} · đầu ra ${outName.value}")
        if (rc != null) { sync(); loadRemote(start) } else { exo.prepare(); exo.play(); sync() }
    }
    /**
     * Khôi phục hàng đợi đã lưu sau khi app bị tắt: TẠM DỪNG, không prepare ExoPlayer (không mở đầu ra âm thanh cho đến khi người dùng bấm phát),
     * không ghi "phát gần đây"/thống kê. Chỉ thực hiện khi chưa có hàng đợi nào. Trả true nếu đã khôi phục.
     */
    fun restoreQueue(tracks: List<Track>, start: Int, posMs: Long, shuffleOn: Boolean, repeatMode: Int): Boolean {
        if (tracks.isEmpty() || queue.value.isNotEmpty() || rc != null || exo.mediaItemCount > 0) return false
        val k = start.coerceIn(0, tracks.lastIndex)
        exo.playWhenReady = false
        exo.shuffleModeEnabled = shuffleOn
        exo.repeatMode = repeatMode.coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
        queue.value = tracks
        exo.setMediaItems(tracks.map { MediaItem.fromUri(Remote.toUri(it)) }, k, posMs)
        if (shuffleOn) pinShuffle(k) else shuffleIdx = IntArray(0)
        index.value = k; position.value = posMs; duration.value = tracks[k].durationMs
        info.value = null
        probeJob?.cancel()
        probeJob = ioScope.launch { info.value = Probe.probe(tracks[k]) }
        return true
    }
    /** Đang phát tới thiết bị ngoài (UPnP/Cast) — âm lượng trong app không áp dụng. */
    fun isRemote() = rc != null
    /** Thêm bài vào cuối danh sách đang phát (AI DJ). */
    fun append(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val oldN = exo.mediaItemCount
        queue.value = queue.value + tracks
        runCatching { exo.addMediaItems(tracks.map { MediaItem.fromUri(Remote.toUri(it)) }) }
        // ExoPlayer chèn bài mới vào VỊ TRÍ NGẪU NHIÊN của thứ tự xáo trộn (có thể trước bài đang nghe → không bao giờ phát khi tắt lặp): nối vào cuối thứ tự
        val keep = PlayLogic.appendedOrder(shuffleIdx, oldN, tracks.size)
        if (keep != null) applyShuffle(keep) else if (exo.shuffleModeEnabled) pinShuffle(exo.currentMediaItemIndex)
        updateNext()
    }
    fun playShuffled(tracks: List<Track>) = play(tracks, tracks.indices.random(), true)
    /** Trộn bài: xáo thứ tự cả danh sách rồi phát từ đầu (danh sách hiện đúng thứ tự mới). */
    fun playMixed(tracks: List<Track>) = play(tracks.shuffled(), 0, false)
    /** Trộn danh sách đang phát: giữ bài đang nghe ở đầu, xáo các bài còn lại, không ngắt tiếng. */
    fun shuffleQueue() {
        val q = queue.value; if (q.size < 2) return
        val i = index.value.coerceIn(0, q.lastIndex)
        val rest = q.filterIndexed { k, _ -> k != i }.shuffled()
        runCatching {
            if (exo.mediaItemCount > i + 1) exo.removeMediaItems(i + 1, exo.mediaItemCount)
            if (i > 0) exo.removeMediaItems(0, i)
            exo.addMediaItems(rest.map { MediaItem.fromUri(Remote.toUri(it)) })
        }
        queue.value = listOf(q[i]) + rest
        index.value = 0
        if (exo.shuffleModeEnabled) pinShuffle(0) else shuffleIdx = IntArray(0)
        updateNext()
        AppLog.i("Player", "Trộn danh sách phát ${q.size} bài")
    }
    fun jump(i: Int) { if (i !in queue.value.indices || i >= exo.mediaItemCount) return; markSwitch(); clearFailure(); if (rc != null) loadRemote(i) else { ensureReady(); exo.seekTo(i, 0L); exo.play() } }
    fun toggle() {
        val c = rc
        if (c != null) {
            if (playing.value) { playing.value = false; c.pause() }
            // Thiết bị vừa báo lỗi, hoặc chế độ DSD vừa đổi khi đang tạm dừng: nạp lại bài từ vị trí hiện tại (chỉ gửi lệnh Play thì thiết bị báo 701 "Transition not available")
            else if (error.value != null || remoteStale) loadRemote(index.value, position.value)
            else { playing.value = true; c.play() }
        }
        else if (pendingRecovery) { pendingRecovery = false; recoverJob?.cancel(); exo.pause(); error.value = null }   // đang chờ tự phục hồi: bấm = huỷ và tạm dừng
        else if (exo.isPlaying) exo.pause() else { if (exo.playbackState == Player.STATE_IDLE) clearFailure(); ensureReady(); exo.play() }
    }
    fun next() {
        markSwitch()
        clearFailure()
        if (rc != null) { val n = exo.nextMediaItemIndex; if (n != C.INDEX_UNSET) loadRemote(n) } else { ensureReady(); exo.seekToNextMediaItem(); exo.play() }
    }
    fun prev() {
        if (position.value <= 3000) { markSwitch(); clearFailure() }
        if (rc != null) {
            if (position.value > 3000) { rc?.seek(0); position.value = 0 }
            else { val p = exo.previousMediaItemIndex; if (p != C.INDEX_UNSET) loadRemote(p) else { rc?.seek(0); position.value = 0 } }
        } else { ensureReady(); if (exo.currentPosition > 3000) exo.seekTo(0) else { exo.seekToPreviousMediaItem(); exo.play() } }
    }
    /** Tua tới vị trí tuyệt đối (ms) — dùng cho thanh tua cảm ứng trên điện thoại/máy tính bảng. */
    fun seekTo(ms: Long) {
        val c = rc
        val d = if (duration.value > 0) duration.value else Long.MAX_VALUE
        val p = ms.coerceIn(0L, d)
        if (c != null) { position.value = p; c.seek(p) }
        else { exo.seekTo(p); position.value = p }
        QueuePersist.touch()
    }
    fun seekBy(ms: Long) {
        val c = rc
        if (c != null) {
            val d = if (duration.value > 0) duration.value else Long.MAX_VALUE
            val p = (position.value + ms).coerceIn(0L, d); position.value = p; c.seek(p); QueuePersist.touch()
            return
        }
        val d = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + ms).coerceIn(0L, d)); position.value = exo.currentPosition
        QueuePersist.touch()
    }

    // ---------- Stream không dây ----------
    /** Nạp bài thứ i lên thiết bị từ xa. ExoPlayer chỉ giữ danh sách/thứ tự (shuffle, repeat), không phát. */
    @Volatile private var remoteStale = false
    private fun loadRemote(i: Int, startMs: Long = 0L) {
        val c = rc ?: return
        remoteStale = false
        val t = queue.value.getOrNull(i) ?: return
        if (i >= exo.mediaItemCount) return
        if (exo.currentMediaItemIndex != i) exo.seekTo(i, 0L)
        updateNext()
        index.value = i; position.value = startMs; duration.value = t.durationMs; error.value = null
        startProbe(t)
        playing.value = true; markSwitch()
        c.load(t, startMs)
    }

    /** Đổi cách gửi DSD khi đang phát tới thiết bị từ xa: nạp lại bài đang nghe ngay theo chế độ mới, giữ nguyên vị trí. */
    fun reloadRemoteDsd() {
        if (rc == null) return
        val i = index.value
        val t = queue.value.getOrNull(i) ?: return
        if (!StreamServer.isDsdFile(t)) return
        if (playing.value) loadRemote(i, position.value) else remoteStale = true
    }

    // ---- Gapless cho thiết bị UPnP: nạp sẵn bài kế tiếp (SetNextAVTransportURI); thiết bị tự qua bài, app chỉ cập nhật hiển thị ----
    @Volatile private var nextIdx = -1
    private fun updateNext() {
        nextIdx = if (rc == null) -1 else (exo.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: -1)
        (rc as? UpnpController)?.refreshNext()   // loa đã nạp sẵn bài kế cũ: cập nhật nếu thứ tự đổi
    }
    private fun advanceRemote(t: Track) {
        if (rc == null) return
        val q = queue.value
        val i = if (q.getOrNull(nextIdx)?.path == t.path) nextIdx else q.indexOfFirst { it.path == t.path }
        if (i < 0) return
        if (exo.currentMediaItemIndex != i) exo.seekTo(i, 0L)
        index.value = i; position.value = 0; duration.value = t.durationMs; error.value = null
        startProbe(t)
        playing.value = true
        updateNext()
    }

    private fun remoteEnded() {
        if (rc == null) return
        val cur = index.value
        when {
            exo.repeatMode == Player.REPEAT_MODE_ONE -> loadRemote(cur)
            exo.nextMediaItemIndex != C.INDEX_UNSET -> loadRemote(exo.nextMediaItemIndex)
            else -> { playing.value = false; position.value = 0 }
        }
    }

    /** Chọn thiết bị phát: null = quay lại đầu ra của TV. Bài đang nghe được chuyển sang, giữ nguyên vị trí. */
    fun setTarget(r: Renderer?) {
        val wasPlaying = playing.value; val pos = position.value; val i = index.value
        AppLog.i("Player", "Chọn đầu ra: ${r?.label ?: "TV/thiết bị này"}")
        clearFailure(); RemoteStats.health.value = RemoteHealth()
        rc?.let { runCatching { it.stop() }; it.close() }
        rc = null
        if (r == null) {
            remote.value = null
            outName.value = AudioSettings.currentName(appCtx)
            if (queue.value.isNotEmpty()) {
                exo.playWhenReady = wasPlaying     // đang tạm dừng trên loa ngoài thì quay lại TV cũng tạm dừng (không tự phát)
                exo.prepare(); exo.seekTo(i.coerceIn(0, queue.value.lastIndex), pos)
            }
            return
        }
        exo.playWhenReady = false; exo.stop()      // luồng cục bộ dừng hẳn, tránh phát trùng với thiết bị ngoài
        val c = if (r.kind == "cast") CastController(r) else UpnpController(r)
        c.onState = { p, ps, d -> playing.value = p; position.value = ps; if (d > 0) duration.value = d; if (p && ps > 0) main.launch { checkSwitch() } }
        c.onEnded = { main.launch { if (rc === c) remoteEnded() } }
        c.onError = { m -> main.launch { if (rc === c) { error.value = m; playing.value = false } } }
        rc = c; remote.value = r; outName.value = r.label
        if (c is UpnpController) {
            c.nextProvider = { queue.value.getOrNull(nextIdx) }
            c.onAdvanced = { t -> main.launch { if (rc === c) advanceRemote(t) } }
        }
        if (queue.value.isNotEmpty()) loadRemote(i, pos)
    }
    fun toggleShuffle() {
        val on = !exo.shuffleModeEnabled
        exo.shuffleModeEnabled = on
        if (on) pinShuffle(exo.currentMediaItemIndex)   // bài đang nghe lên đầu thứ tự xáo: không bài nào bị bỏ sót khi tắt lặp
        updateNext()
    }
    fun cycleRepeat() {
        exo.repeatMode = when (exo.repeatMode) { Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF; else -> Player.REPEAT_MODE_ALL }
        updateNext()
    }
}

object DataSourceFactoryHolder {
    fun factory(ctx: Context): DataSource.Factory {
        val base = DefaultDataSource.Factory(ctx, DataSource.Factory { NasDataSource() })
        return DataSource.Factory { DecodeDataSource(base.createDataSource()) }
    }
}
