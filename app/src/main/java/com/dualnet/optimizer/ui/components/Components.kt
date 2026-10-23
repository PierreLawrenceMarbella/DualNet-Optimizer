package com.dualnet.optimizer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.data.TrafficSample
import com.dualnet.optimizer.utils.speed
import kotlin.math.*

@Composable
fun NeonCard(modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val border by animateColorAsState(if (selected) Blue else Outline, label = "card border")
    Column(modifier.then(if (selected) Modifier.shadow(12.dp, RoundedCornerShape(17.dp), ambientColor = Blue, spotColor = Blue) else Modifier)
        .clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(if (selected) Color(0xFF0B2048) else CardBlue, Color(0xFF06121E))))
        .border(if (selected) 1.5.dp else 0.8.dp, border, RoundedCornerShape(17.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp), content = content)
}

@Composable
fun GlowIcon(icon: ImageVector, color: Color = Blue, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(Brush.radialGradient(listOf(color.copy(alpha = .16f), Color.Transparent)), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * .75f))
    }
}

@Composable
fun ScreenHeader(title: String, back: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back, modifier = Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = White) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
        action?.invoke()
    }
}

@Composable
fun Note(text: String, actionLabel: String? = null, onClick: () -> Unit = {}) {
    NeonCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Outlined.Info, null, tint = Blue, modifier = Modifier.size(20.dp))
            Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (actionLabel != null) TextButton(onClick = onClick, contentPadding = PaddingValues(top = 4.dp)) { Text(actionLabel, color = Cyan) }
    }
}

@Composable
fun SegmentTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardBlue).border(1.dp, Outline, RoundedCornerShape(24.dp)).padding(3.dp)) {
        labels.forEachIndexed { index, label ->
            val color by animateColorAsState(if (index == selected) Blue else Color.Transparent, label = "tab color")
            Box(Modifier.weight(1f).clip(RoundedCornerShape(21.dp)).background(color).selectableTab(index == selected) { onSelect(index) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, color = if (index == selected) White else Muted)
            }
        }
    }
}
private fun Modifier.selectableTab(selected: Boolean, click: () -> Unit): Modifier = this.semantics { this.selected = selected; role = Role.Tab }.clickable(onClick = click)

@Composable
fun PowerRing(active: Boolean, connecting: Boolean, time: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "power ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "ring rotation")
    val pulse by transition.animateFloat(.55f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "connected pulse")
    Box(Modifier.size(252.dp).semantics(mergeDescendants = true) { contentDescription = if (active) "Disconnect" else "Connect"; role = Role.Button }
        .clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .405f
            drawCircle(Brush.radialGradient(listOf(Green.copy(alpha = .12f), Blue.copy(alpha = .05f), Color.Transparent)))
            for (i in 6 downTo 1) drawCircle(Blue.copy(alpha = .012f * i), radius + i * 2.8.dp.toPx(), style = Stroke(9.dp.toPx()))
            rotate(180f + if (active || connecting) rotation else 0f) {
                drawCircle(Brush.sweepGradient(listOf(Green, Cyan, Blue, Purple, Blue, Green)), radius, style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
                if (connecting) drawArc(White.copy(alpha = .8f), 0f, 28f, false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.PowerSettingsNew, null, tint = White, modifier = Modifier.size(53.dp))
            Text(if (connecting) "Connecting" else if (active) "Connected" else "Disconnected", color = if (active) Green.copy(alpha = pulse) else White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(time, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
fun TrafficGraph(history: List<TrafficSample>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(80.dp)) {
        val values = history.takeLast(40)
        val maximum = max(1.0, values.maxOfOrNull { max(it.wifiDown ?: 0.0, it.mobileDown ?: 0.0) } ?: 1.0)
        repeat(8) { i -> drawLine(Outline.copy(alpha = .35f), Offset(size.width * i / 7, 0f), Offset(size.width * i / 7, size.height)) }
        fun graph(data: List<Double?>, color: Color) {
            if (data.size < 2) return
            val path = Path()
            data.forEachIndexed { i, value ->
                val x = size.width * i / (data.size - 1)
                val y = size.height - ((value ?: 0.0) / maximum * size.height * .85f).toFloat() - 2
                if (i == 0) path.moveTo(x, y) else {
                    val px = size.width * (i - 1) / (data.size - 1)
                    val py = size.height - ((data[i - 1] ?: 0.0) / maximum * size.height * .85f).toFloat() - 2
                    path.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
                }
            }
            val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .4f), Color.Transparent)))
            drawPath(path, color.copy(alpha = .13f), style = Stroke(6.dp.toPx()))
            drawPath(path, color, style = Stroke(1.5.dp.toPx()))
        }
        graph(values.map { it.wifiDown }, Blue)
        graph(values.map { it.mobileDown }, Green)
    }
}

@Composable
fun SpeedGauge(value: Double) {
    val animated by animateFloatAsState(value.toFloat(), tween(450), label = "measured speed")
    val maximum = max(100f, 10f.pow(ceil(log10(max(1f, animated)))))
    Box(Modifier.fillMaxWidth().height(255.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val radius = min(size.width / 2 - 14.dp.toPx(), size.height - 25.dp.toPx())
            val origin = Offset(size.width / 2, size.height - 12.dp.toPx())
            val tl = Offset(origin.x - radius, origin.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            val gradient = Brush.linearGradient(listOf(Purple, Blue, Cyan, Green), Offset(0f, size.height), Offset(size.width, 0f))
            drawArc(gradient, 180f, 180f, false, tl, arcSize, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
            drawArc(gradient, 180f, 180f, false, tl, arcSize, alpha = .08f, style = Stroke(30.dp.toPx(), cap = StrokeCap.Round))
            repeat(11) { i ->
                val angle = Math.PI + Math.PI * i / 10
                val a = Offset(origin.x + cos(angle).toFloat() * (radius - 16.dp.toPx()), origin.y + sin(angle).toFloat() * (radius - 16.dp.toPx()))
                val b = Offset(origin.x + cos(angle).toFloat() * (radius - 26.dp.toPx()), origin.y + sin(angle).toFloat() * (radius - 26.dp.toPx()))
                drawLine(if (i < 5) Blue else Green, a, b, 1.5.dp.toPx())
            }
            if (animated > 0f) {
                val angle = Math.PI + Math.PI * (animated / maximum).coerceIn(0f, 1f)
                val dot = Offset(origin.x + cos(angle).toFloat() * radius, origin.y + sin(angle).toFloat() * radius)
                drawCircle(White, 4.dp.toPx(), dot)
            }
        }
        Column(Modifier.padding(bottom = 19.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(speed(animated.toDouble()), fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
            Text("Mbps", fontSize = 18.sp, color = Muted)
        }
    }
}

@Composable
fun Donut(fraction: Float?, modifier: Modifier = Modifier) {
    Box(modifier.size(134.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            drawArc(Outline, -90f, 360f, false, style = Stroke(18.dp.toPx()))
            if (fraction != null) {
                drawArc(Green, -90f, 360f, false, style = Stroke(18.dp.toPx()))
                drawArc(Brush.linearGradient(listOf(Blue, Cyan)), -90f, fraction * 360, false, style = Stroke(18.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—", color = Cyan, fontWeight = FontWeight.Bold)
            Text(fraction?.let { "${((1 - it) * 100).roundToInt()}%" } ?: "—", color = Green, fontWeight = FontWeight.Bold)
        }
    }
}
