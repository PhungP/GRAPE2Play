package com.grape.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.util.TreeMap

private class Node(val name: String) {
    val dirs = TreeMap<String, Node>(String.CASE_INSENSITIVE_ORDER)
    val tracks = ArrayList<Track>()
    fun all(): List<Track> = tracks + dirs.values.flatMap { it.all() }
    val count: Int by lazy { tracks.size + dirs.values.sumOf { it.count } }
    /** Bài đại diện để lấy ảnh bìa cho ô thư mục: ưu tiên bài nằm ngay trong thư mục, rồi mới tới thư mục con. */
    fun first(): Track? = tracks.firstOrNull() ?: dirs.values.firstNotNullOfOrNull { it.first() }
}

private fun build(all: List<Track>): Node {
    val root = Node("")
    for (t in all) {
        val s = segs(t); var n = root
        for (i in 0 until s.size - 1) n = n.dirs.getOrPut(s[i]) { Node(s[i]) }
        n.tracks += t
    }
    return root
}

/** Kiểu thư mục theo tên: màu nền tối dịu, biểu tượng trắng và chữ cái lớn. Bolero, Jazz, Pop, 24bit, DSD có kiểu riêng; thư mục khác lấy màu/biểu tượng theo tên. */
private class FolderLook(val color: Color, val icon: Int, val letter: String)
private val FOLDER_COLORS = listOf(0xFF3F6B5B, 0xFF8F5B2F, 0xFF4A4F8F, 0xFF6B3F4F, 0xFF2F6F7F, 0xFF6F6F2F, 0xFF7F3F2F, 0xFF4F7F3F).map { Color(it) }
// icon: 0 guitar, 1 sax, 2 mmicro, 3 tai nghe, 4 đĩa than, 5 nốt nhạc, 6 album
private fun folderLook(name: String): FolderLook {
    val n = name.lowercase().replace(" ", "").replace("-", "").replace("_", "")
    return when {
        "bolero" in n -> FolderLook(Color(0xFF7A2E2E), 0, "B")
        "jazz" in n -> FolderLook(Color(0xFF9A6B2A), 1, "J")
        n == "pop" || n.startsWith("pop") || n.endsWith("pop") -> FolderLook(Color(0xFFA8446F), 2, "P")
        "24bit" in n || n.startsWith("24") -> FolderLook(Color(0xFF5B3F8F), 3, "2")
        "dsd" in n -> FolderLook(Color(0xFF2F5F8F), 4, "DSD")
        else -> {
            val h = Math.floorMod(name.hashCode(), 1000)
            FolderLook(FOLDER_COLORS[h % FOLDER_COLORS.size], 5 + (h / 8) % 2, name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#")
        }
    }
}

@Composable
private fun FolderGlyph(icon: Int, bg: Color, modifier: Modifier) {
    when (icon) {
        2 -> Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = modifier)
        3 -> Icon(Icons.Filled.Headphones, null, tint = Color.White, modifier = modifier)
        5 -> Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = modifier)
        6 -> Icon(Icons.Filled.Album, null, tint = Color.White, modifier = modifier)
        else -> Canvas(modifier) {
            val u = size.minDimension / 100f
            val w = Color.White
            when (icon) {
                0 -> rotate(-40f, Offset(50f * u, 50f * u)) {   // guitar
                    drawCircle(w, 14f * u, Offset(50f * u, 52f * u)); drawCircle(w, 20f * u, Offset(50f * u, 74f * u))
                    drawCircle(bg, 6f * u, Offset(50f * u, 66f * u))
                    drawRect(w, Offset(47f * u, 6f * u), Size(6f * u, 44f * u)); drawRect(w, Offset(44f * u, 0f), Size(12f * u, 12f * u))
                }
                1 -> {   // kèn sax
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(70f * u, 8f * u); lineTo(54f * u, 24f * u); lineTo(54f * u, 66f * u)
                        quadraticBezierTo(54f * u, 90f * u, 36f * u, 90f * u); quadraticBezierTo(20f * u, 90f * u, 20f * u, 72f * u)
                    }
                    drawPath(p, w, style = androidx.compose.ui.graphics.drawscope.Stroke(9f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawCircle(w, 11f * u, Offset(20f * u, 68f * u)); drawCircle(bg, 5f * u, Offset(20f * u, 68f * u))
                }
                else -> {   // đĩa than
                    drawCircle(w, 46f * u, Offset(50f * u, 50f * u))
                    drawCircle(bg, 34f * u, Offset(50f * u, 50f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * u))
                    drawCircle(bg, 24f * u, Offset(50f * u, 50f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * u))
                    drawCircle(bg, 13f * u, Offset(50f * u, 50f * u)); drawCircle(w, 3.5f * u, Offset(50f * u, 50f * u))
                }
            }
        }
    }
}

/** Ô thư mục: vuông 1:1, nền màu phẳng tối dịu, ở giữa một biểu tượng trắng và một chữ cái lớn; không viền, không chữ nhỏ, không đổ bóng. Tên thư mục ở dưới. */
@Composable
internal fun FolderTile(name: String, count: Int, cover: Track?, unit: String = "bài", onClick: () -> Unit) {
    val look = remember(name) { folderLook(name) }
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(look.color)) {
                val w = maxWidth
                if (cover != null) CoverImage(cover, Modifier.fillMaxSize(), 0)
                else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    FolderGlyph(look.icon, look.color, Modifier.size(w * 0.36f))
                    Spacer(Modifier.height(w * 0.04f))
                    androidx.compose.material3.Text(look.letter, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1,
                        fontSize = (w.value * (if (look.letter.length > 1) 0.2f else 0.3f)).sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            T(name, 19, bold = true, lines = 2)
            T("$count $unit", 15, color = C.Muted)
        }
    }
}

/** Duyệt theo cây thư mục thật: ổ/nguồn ▸ thư mục ▸ thư mục con ▸ bài hát. Thư mục hiện dạng ô có ảnh bìa; bài hát nằm ngay trong thư mục hiện thành danh sách bên dưới. */
@Composable
fun FolderBrowser(all: List<Track>, onNav: (Screen) -> Unit) {
    val compact = LocalCompact.current
    val root = remember(all) { build(all) }
    var crumbs by LibNav.crumbs
    var node = root
    for (c in crumbs) node = node.dirs[c] ?: break
    BackHandler(enabled = crumbs.isNotEmpty()) { crumbs = crumbs.dropLast(1) }
    val direct = remember(node) { node.tracks.sortedWith(compareBy({ if (it.no > 0) it.no else Int.MAX_VALUE }, { it.path.lowercase() })) }
    val dirs = remember(node, crumbs, SourceOrder.order.value) { if (crumbs.isEmpty()) node.dirs.values.sortedBy { SourceOrder.rankOfName(it.name) } else node.dirs.values.toList() }
    val cur = Playback.queue.collectAsState().value.getOrNull(Playback.index.collectAsState().value)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(if (crumbs.isEmpty()) "Tất cả ổ & nguồn" else crumbs.joinToString("  ›  "), if (compact) 18 else 24, C.Gold, Modifier.weight(1f), lines = 2)
            if (crumbs.isNotEmpty()) Focusable({ crumbs = crumbs.dropLast(1) }) { T("◂ Lên một cấp", if (compact) 15 else 18, C.Text, Modifier.padding(16.dp, 8.dp)) }
            if (node.count > 0 && crumbs.isNotEmpty()) {
                Focusable({ Playback.play(node.all(), 0, false); onNav(Screen.NOW) }) { T("▶ Phát cả thư mục", if (compact) 15 else 18, C.Gold, Modifier.padding(16.dp, 8.dp)) }
                Focusable({ Playback.playMixed(node.all()); onNav(Screen.NOW) }) { T("⤮ Trộn", if (compact) 15 else 18, C.Gold, Modifier.padding(16.dp, 8.dp)) }
                if (!compact) Focusable({ Playback.playShuffled(node.all()); onNav(Screen.NOW) }) { T("🎲 Ngẫu nhiên", 18, C.Gold, Modifier.padding(16.dp, 8.dp)) }
            }
        }
        if (crumbs.isEmpty()) UnplayedHint(all)
        LazyVerticalGrid(
            if (compact) GridCells.Adaptive(130.dp) else GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp),
        ) {
            items(dirs, key = { "d:" + it.name }) { d -> FolderTile(d.name, d.count, d.first()) { crumbs = crumbs + d.name } }
            itemsIndexed(direct, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, t ->
                TrackRow(i, t, t.path == cur?.path) { Playback.play(direct, i, false); onNav(Screen.NOW) }
            }
        }
    }
}
