package kr.telelian.examguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SkyBlue = Color(0xFF38BDF8)   // 레이더/강조 시안블루
val BlueDim = Color(0xFF1E40AF)   // 라이트 테마 강조
val DarkBg = Color(0xFF0A1F3C)    // 딥 네이비 배경
val AlertRed = Color(0xFFF43F5E)
val Amber = Color(0xFFFBBF24)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    secondary = Amber,
    background = DarkBg,
    surface = Color(0xFF0F294A),
    error = AlertRed,
)

private val LightColors = lightColorScheme(
    primary = BlueDim,
    secondary = Amber,
    error = AlertRed,
)

@Composable
fun ExamGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
