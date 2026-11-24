@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.grape.play

import android.Manifest
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var perm by mutableStateOf(false)
    private var permDone by mutableStateOf(false)   // đã xử lý xong hộp thoại quyền bộ nhớ (cho phép hoặc từ chối)
    private var openNow by mutableStateOf(false)    // mở màn Đang phát (khi bấm bài từ trang chủ TV)
    private val isTv: Boolean by lazy {
        (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    /** Android 13+ tách quyền đọc nhạc thành READ_MEDIA_AUDIO. */
    private val storagePerm: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private val req = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        perm = ok; permDone = true
        if (ok) lifecycleScope.launch { Library.load(applicationContext) }
    }
    private val reqNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { BackgroundAccess.requestBattery(this) }

    /** Bật chạy ngầm: giữ dịch vụ nền (có hiệu lực ngay) rồi xin thêm quyền thông báo / miễn tối ưu pin.
     *  Trả về false nếu TV không có màn hình cấp quyền pin nào để mở (dịch vụ nền vẫn đã bật). */
    fun askBackground(): Boolean {
        BackgroundAccess.asked = true
        BackgroundAccess.keep = true
        Keepalive.ensure(this)
        if (BackgroundAccess.needsNotifPerm(this)) { reqNotif.launch(Manifest.permission.POST_NOTIFICATIONS); return true }
        return BackgroundAccess.requestBattery(this)
    }

    /** Thoát hẳn: dừng nhạc, tắt dịch vụ nền / chạy ngầm, đóng app và gỡ khỏi danh sách gần đây. */
    fun exitApp() {
        BackgroundAccess.keep = false
        Playback.userPauseAt = android.os.SystemClock.elapsedRealtime()
        runCatching { if (Playback.remote.value == null) Playback.exo.pause() else if (Playback.playing.value) Playback.toggle() }
        stopService(Intent(this, PlaybackService::class.java))
        finishAndRemoveTask()
    }

    /** Bấm một bài trên hàng GRAPEPlay ở trang chủ TV: phát bài đó (trong danh sách yêu thích / phát gần đây). */
    private fun handleLaunch(i: Intent?) {
        val path = i?.getStringExtra(TvHome.EXTRA_PATH) ?: return
        val list = i.getStringExtra(TvHome.EXTRA_LIST) ?: ""
        i.removeExtra(TvHome.EXTRA_PATH)
        lifecycleScope.launch {
            var waited = 0
            while (Library.tracks.value.isEmpty() && waited < 20_000) { delay(250); waited += 250 }
            val all = Library.tracks.value
            val q = when (list) { "fav" -> Favorites.tracks(all); "recent" -> Recents.tracks(all); else -> emptyList() }
            val k = q.indexOfFirst { it.path == path }
            if (k >= 0) { Playback.play(q, k, false); openNow = true }
            else all.firstOrNull { it.path == path }?.let { Playback.play(listOf(it), 0, false); openNow = true }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Device.tv = isTv
        SourceStore.init(applicationContext)
        BurnIn.init(applicationContext)
        SourceVisibility.init(applicationContext)
        Playback.init(applicationContext)
        watchSystem()
        perm = ContextCompat.checkSelfPermission(this, storagePerm) == PackageManager.PERMISSION_GRANTED
        permDone = perm
        if (isTv) TvHome.schedule(applicationContext, 15_000)   // làm mới hàng trên trang chủ TV sau khi thư viện nạp xong
        handleLaunch(intent)
        if (perm) { if (Library.tracks.value.isEmpty()) lifecycleScope.launch { Library.load(applicationContext) } }
        else req.launch(storagePerm)
        val dm = resources.displayMetrics
        // Mi Box và nhiều box Android TV chạy giao diện 1080p dù xuất ra TV 4K: hệ thống báo cho app chỉ 1920×1080.
        // Độ phân giải thật của màn hình/cổng HDMI nằm trong Display.Mode → dùng để chế độ "Tự động" nhận ra 4K.
        val physH = runCatching { windowManager.defaultDisplay.mode.physicalHeight }.getOrDefault(0).coerceAtLeast(dm.heightPixels)
        AppLog.i("Display", "UI ${dm.widthPixels}×${dm.heightPixels} · màn hình thật ${physH}p · chế độ ${DisplayPrefs.mode.value}")
        val tv = isTv
        setContent {
            val cfg = LocalConfiguration.current
            // TV: 1dp = 1px của canvas thiết kế (16:9). Canvas rộng 1560 (FHD, chữ to) hoặc 1920 (4K, gọn); tự co giãn theo màn hình thật 720p / 1080p / 4K.
            // Điện thoại / máy tính bảng: dùng mật độ thật của máy, chữ co nhỏ hơn một chút trên màn hình hẹp.
            val cw = DisplayPrefs.canvasWidth(physH)
            val scale = minOf(dm.widthPixels / cw, dm.heightPixels / (cw * 9f / 16f)).coerceAtLeast(0.4f)
            val density = if (tv) Density(scale, 1f) else LocalDensity.current
            val textScale = if (tv || cfg.screenWidthDp >= 600) 1f else 0.8f
            CompositionLocalProvider(LocalDensity provides density, LocalCompact provides !tv, LocalTextScale provides textScale, LocalCanvasW provides (if (tv) cw else 1560f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    var screen by remember { mutableStateOf(Screen.LIB) }
                    var askBg by remember { mutableStateOf(!BackgroundAccess.asked && !BackgroundAccess.ignoringBattery(applicationContext)) }
                    LaunchedEffect(openNow) { if (openNow) { screen = Screen.NOW; openNow = false } }
                    Box(Modifier.fillMaxSize().background(C.Bg).then(if (Themes.style.value == 5) Modifier.sfBackdrop() else if (GlassTheme.on) Modifier.glassBackdrop() else if (Themes.style.value in 6..15) Modifier.brandFace() else Modifier)) { BurnIn.Guard {
                        if (!tv) MobileApp(perm, { req.launch(storagePerm) }, screen, { screen = it }, { screen = Screen.SOURCES }, { screen = Screen.OUTPUT })
                        else {
                        val pages: @Composable () -> Unit = { when (screen) {
                            Screen.NOW -> if (Themes.style.value == 0 || Themes.style.value == 4 || GlassTheme.on || Themes.style.value in 5..15) NowPlaying({ screen = it }, { screen = Screen.SOURCES }, { screen = Screen.OUTPUT }, alu = Themes.style.value == 4, sf = Themes.style.value in 5..15)
                                else SymNow(Themes.style.value, { screen = it }) { screen = Screen.OUTPUT }
                            Screen.LIB -> LibraryScreen(perm, { req.launch(storagePerm) }, { screen = it }, { screen = Screen.SOURCES }, { screen = Screen.OUTPUT })
                            Screen.SOUND -> SoundScreen({ screen = it }, { screen = Screen.SOURCES }, { screen = Screen.OUTPUT })
                            Screen.SOURCES -> SourcesPage { screen = it }
                            Screen.OUTPUT -> OutputPage { screen = it }
                        } }
                        if (Themes.style.value == 4 && screen != Screen.NOW)
                            Box(Modifier.fillMaxSize().brushedAluminum().padding(horizontal = 28.dp, vertical = 24.dp)) {
                                Box(Modifier.fillMaxSize().aluScreen(dark = true).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))) { pages() }
                            }
                        else if (Themes.style.value in 6..15 && screen != Screen.NOW)
                            Box(Modifier.fillMaxSize().brandFace().padding(horizontal = 28.dp, vertical = 24.dp)) {
                                val bz = Brands.cur
                                Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape((bz?.radius ?: 8).dp)).border(3.dp, bz?.bezel ?: C.Line, androidx.compose.foundation.shape.RoundedCornerShape((bz?.radius ?: 8).dp))) { pages() }
                            }
                        else pages()
                        }
                        val slowMsg by Playback.slow.collectAsState()
                        slowMsg?.let { m ->
                            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                                Box(Modifier.padding(bottom = 56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(C.Dlg)
                                    .border(1.dp, C.Gold, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) { T(m, 14, C.Gold, lines = 2) }
                            }
                        }
                        if (permDone) UpdateDialog()
                        if (Overlay.spectrum.value) SpectrumDialog { Overlay.spectrum.value = false }
                        AiDjEffect()
                        AiSaverHost()
                        if (BitPerfectUi.open.value) BitPerfectDialog()
                        if (Overlay.ai.value) AiHubDialog(onClose = { Overlay.ai.value = false }, onPlayed = { screen = Screen.NOW })
                        if (Overlay.exit.value) ExitDialog(onStay = { Overlay.exit.value = false }, onExit = { Overlay.exit.value = false; exitApp() })
                        if (permDone && askBg) BackgroundDialog(onAllow = { askBg = false; askBackground() }, onLater = { askBg = false; BackgroundAccess.asked = true })
                    } }
                }
            }
        }
    }

    // ---- Theo dõi vòng đời để chẩn đoán nhạc phát tới thiết bị UPnP/Cast bị ngắt khi rời app ----
    private fun playingRemote() = Playback.remote.value != null && Playback.playing.value
    override fun onStart() { super.onStart(); Playback.uiVisible = true; Playback.hiddenAt = 0L }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playingRemote()) Keepalive.ensure(this)   // chắc chắn dịch vụ nền đang chạy trước khi rời app
    }
    override fun onStop() {
        super.onStop()
        Playback.uiVisible = false; Playback.hiddenAt = android.os.SystemClock.elapsedRealtime()
        AppLog.i("App", "onStop · phát từ xa=${Playback.remote.value != null} · đang phát=${Playback.playing.value} · đóng=${isFinishing}")
    }
    // ---- Chẩn đoán (chỉ ghi nhật ký): sự kiện hệ thống quanh lúc bấm Home — phím Home, cắm/rút HDMI, thiết bị âm thanh thay đổi ----
    private var sysRx: android.content.BroadcastReceiver? = null
    private var devCb: android.media.AudioDeviceCallback? = null
    private fun watchSystem() {
        runCatching {
            sysRx = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    AppLog.i("System", "${i.action} ${i.getStringExtra("reason") ?: ""}".trim())
                }
            }
            val f = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS); addAction("android.media.action.HDMI_AUDIO_PLUG")
                addAction("android.intent.action.HDMI_PLUGGED"); addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            registerReceiver(sysRx, f)
        }
        runCatching {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            devCb = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(d: Array<out android.media.AudioDeviceInfo>) { AppLog.i("System", "Thiết bị âm thanh thêm: " + d.joinToString { "${it.productName}(loại ${it.type})" }) }
                override fun onAudioDevicesRemoved(d: Array<out android.media.AudioDeviceInfo>) { AppLog.i("System", "Thiết bị âm thanh bị gỡ: " + d.joinToString { "${it.productName}(loại ${it.type})" }) }
            }
            am.registerAudioDeviceCallback(devCb, null)
        }
    }
    override fun onDestroy() {
        runCatching { sysRx?.let { unregisterReceiver(it) } }
        runCatching { devCb?.let { (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).unregisterAudioDeviceCallback(it) } }
        super.onDestroy(); AppLog.i("App", "onDestroy · phát từ xa=${Playback.remote.value != null} · đang phát=${Playback.playing.value}") }

    // Ghi nhận lần thao tác cuối (cho chế độ tự giảm sáng chống lưu ảnh OLED)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (AiSaver.showing.value) {
            if (AiSaver.passKey(event.keyCode)) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) AiSaver.hide()
            return true   // phím đầu tiên chỉ để thoát màn hình chờ
        }
        BurnIn.touch(); return super.dispatchKeyEvent(event)
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (AiSaver.showing.value) { if (ev.action == MotionEvent.ACTION_DOWN) AiSaver.hide(); return true }
        BurnIn.touch(); return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { Playback.userPauseAt = android.os.SystemClock.elapsedRealtime(); Playback.toggle(); true }
        KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!Playback.playing.value) Playback.toggle(); true }
        KeyEvent.KEYCODE_MEDIA_PAUSE -> { Playback.userPauseAt = android.os.SystemClock.elapsedRealtime(); if (Playback.playing.value) Playback.toggle(); true }
        KeyEvent.KEYCODE_MEDIA_NEXT -> { Playback.next(); true }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { Playback.prev(); true }
        // Nút micro trên điều khiển TV: mở trợ lý AI và nghe ngay (nếu hệ thống chuyển phím này cho app)
        KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST ->
            if (Ai.hasKey.value) { Overlay.aiStart = "ask"; Overlay.ai.value = true; Overlay.aiListen.value = true; true } else super.onKeyDown(keyCode, event)
        else -> super.onKeyDown(keyCode, event)
    }
}
