package com.grape.play

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Công bố trạng thái phát qua MediaSession (framework, không thêm thư viện) để launcher / hệ thống đọc được:
 * bài đang phát (tên, nghệ sĩ, album, thời lượng, ảnh bìa), trạng thái + vị trí + tốc độ, hàng đợi và các hành động điều khiển.
 * Chỉ ĐỌC các StateFlow của Playback và gọi lại Playback.toggle/next/prev/seekTo — không sửa luồng phát. Phát tới UPnP/Cast cũng được phản ánh
 * vì dữ liệu lấy từ Playback chứ không từ riêng ExoPlayer.
 */
object MediaSessionBridge {
    private const val QUEUE_MAX = 100          // chỉ công bố tối đa ~100 bài quanh bài hiện tại (giới hạn kích thước Binder)
    private const val COVER_PX = 300           // ảnh bìa trong metadata: cạnh dài tối đa 300 px (giữ dưới giới hạn Binder)
    private const val SEEK_JUMP_MS = 1500L     // lệch hơn mức này so với vị trí dự kiến mới coi là tua

    private var session: MediaSession? = null
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var coverJob: Job? = null

    // Trạng thái đã công bố lần gần nhất — chỉ gửi lại khi có thay đổi thật sự
    private var lastKey = ""; private var lastPlaying: Boolean? = null; private var lastHasTrack = false
    private var lastQueueKey = ""; private var lastDur = -1L
    private var lastPos = 0L; private var lastPosAt = 0L
    private var coverKey = ""; private var cover: Bitmap? = null

    val token: MediaSession.Token? get() = session?.sessionToken

    /** Gọi nhiều lần cũng được (chỉ khởi tạo một lần). Phải gọi sau Playback.init. */
    @Synchronized fun init(ctx: Context) {
        if (session != null) return
        app = ctx.applicationContext
        val s = MediaSession(app, "GRAPE2Play")
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { if (!Playback.playing.value) Playback.toggle() }
            override fun onPause() { if (Playback.playing.value) Playback.toggle() }
            override fun onStop() { if (Playback.playing.value) Playback.toggle() }
            override fun onSkipToNext() { Playback.next() }
            override fun onSkipToPrevious() { Playback.prev() }
            override fun onSeekTo(pos: Long) { Playback.seekTo(pos) }
        })
        s.setSessionActivity(PendingIntent.getActivity(app, 2, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        session = s
        // Gom mọi thay đổi liên quan; mỗi nhánh tự so sánh với lần công bố trước nên không gửi thừa.
        scope.launch {
            combine(Playback.queue, Playback.index, Playback.playing, Playback.duration) { _, _, _, _ -> }.collect { refresh() }
        }
        scope.launch { Playback.position.collect { onPosition(it) } }
        refresh()
    }

    private fun track(): Track? = Playback.queue.value.getOrNull(Playback.index.value)

    /** Cập nhật metadata, hàng đợi và trạng thái khi bài/danh sách/trạng thái phát/thời lượng đổi. */
    private fun refresh() {
        val s = session ?: return
        val t = track()
        val playing = Playback.playing.value
        val dur = if (Playback.duration.value > 0) Playback.duration.value else (t?.durationMs ?: 0L)   // nguồn từ xa chưa biết thời lượng: dùng thẻ tag, cập nhật ngay khi player biết
        val key = (t?.path ?: "") + "|" + Playback.index.value
        if (t == null) {
            if (lastHasTrack) { s.isActive = false; s.setQueue(null); s.setMetadata(null); setState(s, false, true); lastHasTrack = false; lastKey = ""; lastQueueKey = "" }
            return
        }
        lastHasTrack = true
        if (!s.isActive) s.isActive = true
        val trackChanged = key != lastKey
        if (trackChanged || dur != lastDur) {
            val changedTrack = trackChanged
            lastKey = key; lastDur = dur
            if (changedTrack) { cover = null; coverKey = t.path; loadCover(t) }
            publishMetadata(s, t, dur)
        }
        publishQueue(s)
        if (lastPlaying != playing || trackChanged) { lastPlaying = playing; setState(s, playing, false); PlaybackService.refreshNotification(app) }
    }

    private fun publishMetadata(s: MediaSession, t: Track, dur: Long) {
        val b = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, t.path)
            .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, t.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, t.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, dur.coerceAtLeast(0L))
        cover?.takeIf { coverKey == t.path }?.let { b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it); b.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it) }
        s.setMetadata(b.build())
    }

    /** Ảnh bìa lấy bất đồng bộ (có thể phải đọc file/mạng); xong thì gắn lại metadata của đúng bài đó. */
    private fun loadCover(t: Track) {
        coverJob?.cancel()
        coverJob = scope.launch {
            val bmp = runCatching { Covers.get(t) }.getOrNull() ?: return@launch
            val k = COVER_PX.toFloat() / maxOf(bmp.width, bmp.height)
            val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
            if (track()?.path != t.path) return@launch      // đã sang bài khác
            cover = small; coverKey = t.path
            session?.let { publishMetadata(it, t, lastDur) }
        }
    }

    /** Hàng đợi: cắt cửa sổ ≤ QUEUE_MAX bài quanh bài hiện tại; activeQueueItemId = chỉ số bài trong danh sách đầy đủ. */
    private fun publishQueue(s: MediaSession) {
        val q = Playback.queue.value; val i = Playback.index.value
        val from = QueueWindow.from(q.size, i, 10, QUEUE_MAX)
        val to = QueueWindow.to(q.size, i, 10, QUEUE_MAX)
        val key = "${q.size}|$from|$to|" + (q.getOrNull(from)?.path ?: "") + "|" + (q.getOrNull(to - 1)?.path ?: "")
        if (key != lastQueueKey) {
            lastQueueKey = key
            s.setQueue((from until to).map { n ->
                val t = q[n]
                MediaSession.QueueItem(MediaDescription.Builder().setMediaId(t.path).setTitle(t.title).setSubtitle(t.artist).setDescription(t.album).build(), n.toLong())
            })
        }
    }

    private const val ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP

    private fun setState(s: MediaSession, playing: Boolean, none: Boolean) {
        val pos = Playback.position.value.coerceAtLeast(0L)
        val st = when { none -> PlaybackState.STATE_NONE; playing -> PlaybackState.STATE_PLAYING; else -> PlaybackState.STATE_PAUSED }
        s.setPlaybackState(PlaybackState.Builder().setActions(if (none) 0L else ACTIONS).setState(st, pos, if (playing) 1f else 0f, SystemClock.elapsedRealtime())
            .setActiveQueueItemId(Playback.index.value.toLong()).build())   // framework: bài đang phát khai báo trong PlaybackState (không có setActiveQueueItemId trên MediaSession)
        lastPos = pos; lastPosAt = SystemClock.elapsedRealtime()
    }

    /** Vị trí đổi liên tục khi đang phát (client tự ngoại suy từ vị trí+tốc độ) → chỉ công bố lại khi vị trí nhảy bất thường (tua). */
    private fun onPosition(p: Long) {
        val s = session ?: return
        if (!lastHasTrack) return
        val playing = Playback.playing.value
        val expected = lastPos + if (playing) (SystemClock.elapsedRealtime() - lastPosAt) else 0L
        if (abs(p - expected) > SEEK_JUMP_MS) setState(s, playing, false)
    }
}
