package com.grape.play

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Mã hoá chuỗi bí mật (khoá API) bằng khoá AES-256-GCM trong Android Keystore (khoá không rời khỏi chip/TEE, không xuất được).
 * Định dạng lưu: "enc1:" + base64(IV 12 byte + bản mã). Chuỗi không có tiền tố là bản cũ (plaintext) — vẫn đọc được để di trú.
 * Nếu Keystore lỗi (một số TV box rẻ): [encrypt] trả null và người gọi PHẢI giữ nguyên bản rõ (không được làm mất khoá của người dùng).
 */
object SecretBox {
    private const val ALIAS = "grape2play_secretbox_v1"
    const val PREFIX = "enc1:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }

    fun isEncrypted(stored: String) = stored.startsWith(PREFIX)

    /** null nếu không mã hoá được HOẶC giải mã lại không ra đúng bản rõ (Keystore lỗi/bất thường): khi đó người gọi giữ nguyên bản rõ, không làm mất bí mật. */
    fun encrypt(plain: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        val out = PREFIX + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        out.takeIf { decrypt(it) == plain }
    }.getOrNull()

    /** Bản rõ; chuỗi không có tiền tố được coi là bản cũ chưa mã hoá. null nếu có tiền tố nhưng không giải mã được (khoá Keystore mất/hỏng). */
    fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return stored
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * Di trú MỘT giá trị SharedPreferences plaintext → mã hoá, theo thứ tự an toàn (gọi trên luồng nền): mã hoá (đã kiểm giải mã lại) → commit() → ĐỌC LẠI từ prefs →
     * giải mã đối chiếu với bản rõ gốc; sai bất kỳ bước nào thì khôi phục đúng bản rõ gốc và ghi nhật ký (không bao giờ để mất bí mật). commit() là thay thế file nguyên tử:
     * tiến trình bị dừng giữa chừng thì prefs còn nguyên bản rõ hoặc đã là bản mã, không có trạng thái ở giữa.
     */
    fun migratePref(sp: android.content.SharedPreferences, name: String): Boolean {
        val stored = sp.getString(name, "").orEmpty()
        if (stored.isEmpty() || isEncrypted(stored)) return true
        val enc = encrypt(stored) ?: run { AppLog.w("Secret", "$name: Keystore không dùng được — giữ nguyên bản rõ"); return false }
        if (!sp.edit().putString(name, enc).commit()) { AppLog.w("Secret", "$name: ghi bản mã hoá thất bại — giữ nguyên bản rõ"); return false }
        val back = sp.getString(name, null)
        if (back != enc || decrypt(back) != stored) {
            sp.edit().putString(name, stored).commit()
            AppLog.w("Secret", "$name: đọc lại sau khi ghi không khớp — đã khôi phục bản rõ gốc")
            return false
        }
        return true
    }
}
