package kr.telelian.examguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.telelian.examguard.model.DetectedDevice
import kr.telelian.examguard.model.SignalType
import kr.telelian.examguard.model.rssiToRadiusFraction
import kr.telelian.examguard.ui.theme.AlertRed
import kr.telelian.examguard.ui.theme.Amber
import kr.telelian.examguard.ui.theme.SkyBlue

@Composable
fun DeviceList(
    devices: List<DetectedDevice>,
    nearThresholdDbm: Int,
    selectedDeviceId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (devices.isEmpty()) {
            item {
                Text(
                    "감지된 신호 없음 — 스캔 중…",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        items(devices, key = { it.id }) { d ->
            DeviceRow(
                device = d,
                near = d.rssi >= nearThresholdDbm,
                selected = d.id == selectedDeviceId,
                onClick = { onSelect(if (selectedDeviceId == d.id) null else d.id) },
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: DetectedDevice,
    near: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        near -> AlertRed
        device.type == SignalType.WIFI -> Amber
        else -> SkyBlue
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .then(
                if (selected) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (device.type == SignalType.BLE) Icons.Filled.Bluetooth else Icons.Filled.Wifi,
            contentDescription = device.type.name,
            tint = accent,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (near) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AlertRed)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text("근접 ≈1m", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // RSSI 막대
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                val frac = (1f - rssiToRadiusFraction(device.rssi)).coerceIn(0.03f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
            }
        }
        Text(
            "${device.rssi}",
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp),
        )
        Text(
            "dBm",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
