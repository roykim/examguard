package kr.telelian.examguard.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.telelian.examguard.R
import kr.telelian.examguard.UiState
import kr.telelian.examguard.model.SignalType
import kr.telelian.examguard.ui.theme.AlertRed
import kr.telelian.examguard.ui.theme.Amber
import kr.telelian.examguard.ui.theme.SkyBlue
import kotlin.math.roundToInt

@Composable
fun ExamGuardScreen(
    state: UiState,
    onSelectDevice: (String?) -> Unit,
) {
    val context = LocalContext.current

    // 근접 기기가 생기면 진동 알림
    LaunchedEffect(state.nearDevices.size, state.magneticAlert) {
        if (state.nearDevices.isNotEmpty() || state.magneticAlert) {
            vibrate(context)
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapBackground(lat = state.lat, lng = state.lng)

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            TopBar(state)

            if (state.nearDevices.isNotEmpty() || state.magneticAlert) {
                Spacer(Modifier.padding(top = 6.dp))
                AlertBanner(state)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                RadarView(
                    devices = state.devices,
                    headingDeg = state.headingDeg,
                    nearThresholdDbm = state.nearThresholdDbm,
                    selectedDeviceId = state.selectedDeviceId,
                    modifier = Modifier.fillMaxSize(),
                )
                state.selectedDeviceId?.let { id ->
                    val d = state.devices.firstOrNull { it.id == id }
                    if (d != null) {
                        DirectionHint(
                            name = d.displayName,
                            rssi = d.rssi,
                            hasPeak = d.peakHeadingDeg != null,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }

            DeviceList(
                devices = state.devices,
                nearThresholdDbm = state.nearThresholdDbm,
                selectedDeviceId = state.selectedDeviceId,
                onSelect = onSelectDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .padding(top = 8.dp),
            )

            Text(
                text = stringResource(R.string.copyright),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TopBar(state: UiState) {
    val bleCount = state.devices.count { it.type == SignalType.BLE }
    val wifiCount = state.devices.count { it.type == SignalType.WIFI }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("시험감독관", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "BLE $bleCount · WiFi $wifiCount · ${state.headingDeg.roundToInt()}°",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Explore, null, tint = SkyBlue, modifier = Modifier.padding(end = 4.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${state.magneticUt.roundToInt()} µT",
                    color = if (state.magneticAlert) AlertRed else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "자기장",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun AlertBanner(state: UiState) {
    val msgs = buildList {
        if (state.nearDevices.isNotEmpty()) {
            add("⚠ 근접 기기 ${state.nearDevices.size}대 감지 (≈1m 이내)")
        }
        if (state.magneticAlert) {
            add("⚠ 자기장 이상 +${state.magneticAnomalyUt.roundToInt()}µT (금속/전자기기 의심)")
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AlertRed.copy(alpha = 0.9f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Color.White)
            Spacer(Modifier.padding(start = 6.dp))
            Text("근접 경보", color = Color.White, fontWeight = FontWeight.Bold)
        }
        msgs.forEach {
            Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DirectionHint(
    name: String,
    rssi: Int,
    hasPeak: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("방향 찾기: $name", color = Amber, fontWeight = FontWeight.Bold)
        Text(
            if (hasPeak) "폰을 돌려 세기가 가장 센 방향(흰 화살표)으로 접근하세요"
            else "폰을 천천히 돌리세요 — 세기 최대 방향을 기록합니다",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
        )
        Text("$rssi dBm", color = Color.White, fontFamily = FontFamily.Monospace)
    }
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(200)
    }
}
