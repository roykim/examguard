package kr.telelian.examguard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kr.telelian.examguard.model.DetectedDevice
import kr.telelian.examguard.model.SignalType
import kr.telelian.examguard.model.rssiToRadiusFraction
import kr.telelian.examguard.ui.theme.AlertRed
import kr.telelian.examguard.ui.theme.Amber
import kr.telelian.examguard.ui.theme.SkyBlue
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 레이더 뷰. 중심=나, 바깥=약한 신호.
 * 나침반 방위에 맞춰 방위 글자(N/E/S/W)가 회전하여 항상 실제 북쪽을 가리킨다.
 * 블립 각도는 기기 ID 기반 고정값(전파로는 방향을 알 수 없으므로)이며,
 * 선택한 기기는 '방향 찾기'로 기록된 방위(peakHeadingDeg)로 화살표를 표시한다.
 */
@Composable
fun RadarView(
    devices: List<DetectedDevice>,
    headingDeg: Float,
    nearThresholdDbm: Int,
    selectedDeviceId: String?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepAngle",
    )

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = min(size.width, size.height) / 2f * 0.9f

            drawRings(center, maxR)
            drawSweep(center, maxR, sweep)
            drawCompass(center, maxR, headingDeg, textMeasurer)

            // 블립
            for (d in devices) {
                val angle = blipAngle(d, headingDeg)
                val r = rssiToRadiusFraction(d.rssi) * maxR
                val pos = Offset(
                    center.x + r * sin(Math.toRadians(angle.toDouble())).toFloat(),
                    center.y - r * cos(Math.toRadians(angle.toDouble())).toFloat(),
                )
                val near = d.rssi >= nearThresholdDbm
                val selected = d.id == selectedDeviceId
                val color = when {
                    near -> AlertRed
                    d.type == SignalType.WIFI -> Amber
                    else -> SkyBlue
                }
                if (near) {
                    drawCircle(color.copy(alpha = 0.25f), radius = 22f, center = pos)
                }
                drawCircle(color, radius = if (selected) 12f else 8f, center = pos)
                if (selected) {
                    drawCircle(Color.White, radius = 15f, center = pos, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
            }

            // 선택 기기 방향 화살표
            selectedDeviceId?.let { id ->
                devices.firstOrNull { it.id == id }?.peakHeadingDeg?.let { peak ->
                    drawDirectionArrow(center, maxR, peak - headingDeg)
                }
            }

            // 중심(나)
            drawCircle(Color.White, radius = 6f, center = center)
        }
    }
}

private fun DrawScope.drawRings(center: Offset, maxR: Float) {
    val ringColor = SkyBlue.copy(alpha = 0.35f)
    for (i in 1..3) {
        drawCircle(
            color = ringColor,
            radius = maxR * i / 3f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
        )
    }
    // 십자선
    drawLine(ringColor, Offset(center.x - maxR, center.y), Offset(center.x + maxR, center.y), 1f)
    drawLine(ringColor, Offset(center.x, center.y - maxR), Offset(center.x, center.y + maxR), 1f)
}

private fun DrawScope.drawSweep(center: Offset, maxR: Float, sweepDeg: Float) {
    rotate(degrees = sweepDeg, pivot = center) {
        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                0.9f to Color.Transparent,
                1f to SkyBlue.copy(alpha = 0.35f),
                center = center,
            ),
            radius = maxR,
            center = center,
        )
    }
}

private fun DrawScope.drawCompass(
    center: Offset,
    maxR: Float,
    headingDeg: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
    val style = TextStyle(color = SkyBlue, fontSize = 14.sp)
    for ((label, deg) in labels) {
        // 방위 글자는 (실제방위 - 폰방위) 위치에 그려 실제 방향을 가리킨다
        val a = Math.toRadians((deg - headingDeg).toDouble())
        val x = center.x + (maxR + 2f) * sin(a).toFloat()
        val y = center.y - (maxR + 2f) * cos(a).toFloat()
        val layout = textMeasurer.measure(label, style)
        drawText(
            layout,
            topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
        )
    }
}

private fun DrawScope.drawDirectionArrow(center: Offset, maxR: Float, relDeg: Float) {
    val a = Math.toRadians(relDeg.toDouble())
    val tip = Offset(
        center.x + maxR * 0.95f * sin(a).toFloat(),
        center.y - maxR * 0.95f * cos(a).toFloat(),
    )
    drawLine(Color.White, center, tip, strokeWidth = 3f)
    drawCircle(Color.White, radius = 7f, center = tip)
}

/** 블립 각도: ID 해시 기반 고정값(전파로 방향 산출 불가). 레이더 북쪽 기준 회전 보정. */
private fun blipAngle(d: DetectedDevice, headingDeg: Float): Float {
    val base = (d.id.hashCode().toLong() and 0xFFFF) % 360L
    return (base - headingDeg)
}
