package kr.telelian.examguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.telelian.examguard.ui.ExamGuardScreen
import kr.telelian.examguard.ui.theme.ExamGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExamGuardTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: ScanViewModel = viewModel()
                    Root(vm)
                }
            }
        }
    }
}

/** 필요한 런타임 권한 목록 (API 레벨별). */
private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}.toTypedArray()

@Composable
private fun Root(vm: ScanViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.all { it }
        if (granted) vm.start()
    }

    if (granted) {
        // 권한 확보 상태에서 화면 진입 시 스캔 시작 (중복 호출은 내부에서 무시)
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.start() }
        val state by vm.state.collectAsStateWithLifecycle()
        ExamGuardScreen(state = state, onSelectDevice = vm::selectDevice)
    } else {
        PermissionGate(onRequest = { launcher.launch(requiredPermissions()) })
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "시험감독관은 주변 무선 신호(BLE·WiFi) 세기를 탐지합니다.\n\n" +
                "스캔을 위해 블루투스·위치 권한이 필요합니다. " +
                "위치 권한은 Android 정책상 무선 스캔에 필수이며, 위치 정보는 지도 표시 외에 저장·전송하지 않습니다.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 20.dp)) {
            Text("권한 허용하고 시작")
        }
        Text(
            text = stringResource(R.string.copyright),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}
