package kr.telelian.examguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kr.telelian.examguard.BuildConfig
import kr.telelian.examguard.ui.theme.DarkBg

/**
 * 배경 지도. Maps API 키가 있고 위치를 알면 Google 지도를, 아니면 어두운 그라데이션 폴백을 그린다.
 */
@Composable
fun MapBackground(
    lat: Double?,
    lng: Double?,
    modifier: Modifier = Modifier,
) {
    if (BuildConfig.HAS_MAPS_KEY && lat != null && lng != null) {
        val pos = LatLng(lat, lng)
        val cameraState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(pos, 18f)
        }
        LaunchedEffect(lat, lng) {
            cameraState.position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 18f)
        }
        GoogleMap(
            modifier = modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false,
            ),
            uiSettings = MapUiSettings(
                compassEnabled = false,
                zoomControlsEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                mapToolbarEnabled = false,
            ),
        )
        // 레이더 가독성을 위한 어두운 오버레이
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )
    } else {
        Box(
            modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF14346B), DarkBg, Color(0xFF060F22)),
                    ),
                ),
        )
    }
}
