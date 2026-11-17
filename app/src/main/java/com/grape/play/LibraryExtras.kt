package com.grape.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Hộp thoại chọn nguồn nào hiện ở Thư viện, nguồn nào ẩn (không dùng) cho đỡ rối. Nhạc không bị xoá, bật lại là thấy ngay. */
@Composable
fun SourceVisibilityDialog(sources: List<Source>, onClose: () -> Unit) {
    val hidden by SourceVisibility.hidden
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val order by SourceOrder.order
    val base = listOf("local" to (if (Device.tv) "Bộ nhớ TV & USB" else "Bộ nhớ máy & USB")) + sources.map { it.id to it.name }
    val ids = base.map { it.first }
    val rows = SourceOrder.ordered(ids).also { order.size }.map { id -> base.first { it.first == id } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.dlgFrac(0.5f).clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T("NGUỒN HIỂN THỊ & THỨ TỰ ƯU TIÊN", 11, C.Muted, spaced = true)
            T("Bấm vào từng nguồn để hiện hoặc ẩn; ▲ ▼ để đổi thứ tự ưu tiên (nguồn ở trên hiện trước). Nguồn bị ẩn không xuất hiện trong Thư viện và không lẫn vào mục “Tất cả”.", 12, C.Muted, lines = 3)
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { i, (id, name) ->
                    val vis = id !in hidden
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Focusable({ SourceVisibility.toggle(id) }, Modifier.weight(1f), RoundedCornerShape(8.dp), selected = vis, requester = if (i == 0) first else null) {
                            Row(Modifier.padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                T("${i + 1}. $name", 14, if (vis) C.Gold else C.Muted, Modifier.weight(1f), lines = 2)
                                T(if (vis) "● Hiện" else "○ Ẩn", 13, if (vis) C.Gold else C.Muted, bold = true)
                            }
                        }
                        Focusable({ SourceOrder.move(id, true, ids) }, shape = RoundedCornerShape(8.dp)) { T("▲", 15, C.Gold, Modifier.padding(12.dp, 6.dp), bold = true) }
                        Focusable({ SourceOrder.move(id, false, ids) }, shape = RoundedCornerShape(8.dp)) { T("▼", 15, C.Gold, Modifier.padding(12.dp, 6.dp), bold = true) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Focusable(onClose, Modifier.width(130.dp)) { T("Đóng", 14, C.Gold, Modifier.align(Alignment.Center).padding(12.dp, 6.dp), bold = true) }
            }
        }
    }
}
