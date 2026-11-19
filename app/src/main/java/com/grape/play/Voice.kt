package com.grape.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateOf

/** Nhận giọng nói ngay trong app (cần quyền micro). Dùng SpeechRecognizer của hệ thống, không mở màn hình ngoài. */
object Voice {
    val listening = mutableStateOf(false)
    val partial = mutableStateOf("")
    private var rec: SpeechRecognizer? = null

    fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun hasPermission(ctx: Context) =
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Gọi trên luồng chính. */
    fun start(ctx: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
        stop()
        val r = try { SpeechRecognizer.createSpeechRecognizer(ctx.applicationContext) } catch (e: Exception) { onError("Không khởi tạo được nhận giọng nói: ${e.message}"); return }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening.value = true; partial.value = "" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial.value = it }
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                finish()
                if (t.isNotBlank()) onResult(t) else onError("Không nghe rõ — thử nói lại.")
            }
            override fun onError(error: Int) {
                finish()
                onError(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy giọng nói — bấm 🎤 và nói lại."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền micro cho app."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nhận giọng nói cần mạng — kiểm tra Wi-Fi."
                    SpeechRecognizer.ERROR_AUDIO -> "Không thu được âm thanh từ micro (TV có thể không có micro — dùng điện thoại)."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận giọng nói đang bận — thử lại sau giây lát."
                    else -> "Lỗi nhận giọng nói (mã $error)."
                })
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.en.value) "en-US" else "vi-VN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try { r.startListening(i); listening.value = true } catch (e: Exception) { finish(); onError("Không bắt đầu nghe được: ${e.message}") }
    }

    private fun finish() { listening.value = false; runCatching { rec?.destroy() }; rec = null }
    fun stop() { if (rec != null) finish() }
}
