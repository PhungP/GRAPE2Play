#!/usr/bin/env bash
# Kiểm tra điều kiện cài đè (OTA) giữa APK cũ (đang phát hành) và APK mới. CHỈ ĐỌC APK; không dùng/ghi keystore hay mật khẩu.
# Dùng: scripts/verify_ota.sh <apk_cu> <apk_moi>
# Thoát 0 = đạt mọi điều kiện; 1 = có điều kiện không đạt.
set -u
OLD="${1:-}"; NEW="${2:-}"
[ -f "$OLD" ] && [ -f "$NEW" ] || { echo "Dùng: $0 <apk_cu> <apk_moi>"; exit 2; }
BT="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"/build-tools/* 2>/dev/null | sort -V | tail -1)"
APKSIGNER="$(command -v apksigner || echo "$BT/apksigner")"; AAPT="$(command -v aapt2 || echo "$BT/aapt2")"
[ -x "$APKSIGNER" ] && [ -x "$AAPT" ] || { echo "Thiếu apksigner/aapt2 (Android build-tools)"; exit 2; }
fail=0
cert() { "$APKSIGNER" verify --print-certs "$1" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -1; }
field() { "$AAPT" dump badging "$1" 2>/dev/null | grep -m1 '^package:' | grep -o " $2='[^']*'" | head -1 | sed "s/^ $2='//; s/'\$//"; }
for f in "$OLD" "$NEW"; do "$APKSIGNER" verify "$f" >/dev/null 2>&1 && echo "OK   chữ ký hợp lệ: $(basename "$f")" || { echo "FAIL chữ ký không hợp lệ: $f"; fail=1; }; done
CO="$(cert "$OLD")"; CN="$(cert "$NEW")"
echo "cert cũ : $CO"; echo "cert mới: $CN"
[ -n "$CO" ] && [ "$CO" = "$CN" ] && echo "OK   cùng chứng chỉ ký" || { echo "FAIL chứng chỉ khác nhau → Android sẽ từ chối cài đè"; fail=1; }
PO="$(field "$OLD" name)"; PN="$(field "$NEW" name)"
[ -n "$PO" ] && [ "$PO" = "$PN" ] && echo "OK   cùng applicationId: $PN" || { echo "FAIL applicationId khác ($PO ≠ $PN)"; fail=1; }
VO="$(field "$OLD" versionCode)"; VN="$(field "$NEW" versionCode)"
echo "versionCode: $VO → $VN; versionName: $(field "$OLD" versionName) → $(field "$NEW" versionName)"
[ -n "$VO" ] && [ -n "$VN" ] && [ "$VN" -gt "$VO" ] && echo "OK   versionCode tăng" || { echo "FAIL versionCode phải lớn hơn bản cũ"; fail=1; }
[ "$(field "$OLD" versionName)" != "$(field "$NEW" versionName)" ] && echo "OK   versionName đã đổi" || { echo "FAIL versionName chưa đổi"; fail=1; }
exit $fail
