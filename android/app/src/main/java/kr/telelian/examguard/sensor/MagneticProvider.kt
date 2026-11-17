package kr.telelian.examguard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 지자기 센서로 총 자기장 세기(µT)를 측정한다.
 * 지구 자기장은 보통 25~65µT. 숨은 전자기기/자석이 있으면 수백 µT 로 치솟는다.
 *
 * 콜백은 (현재 세기 µT, 느리게 추적하는 기준선 µT) 를 전달한다.
 * UI 는 현재값과 기준선의 차이로 이상 여부를 판정한다.
 */
class MagneticProvider(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean get() = sensor != null

    private var onReading: ((current: Float, baseline: Float) -> Unit)? = null
    private var smoothed = Float.NaN
    private var baseline = Float.NaN

    fun start(onReading: (current: Float, baseline: Float) -> Unit) {
        this.onReading = onReading
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onReading = null
        smoothed = Float.NaN
        baseline = Float.NaN
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        smoothed = if (smoothed.isNaN()) magnitude else smoothed + SMOOTH * (magnitude - smoothed)
        // 기준선은 아주 느리게 추적 → 급격한 스파이크는 기준선 위로 두드러진다
        baseline = if (baseline.isNaN()) smoothed else baseline + BASELINE * (smoothed - baseline)
        onReading?.invoke(smoothed, baseline)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val SMOOTH = 0.25f   // 현재값 평활
        private const val BASELINE = 0.01f // 기준선 추적(느림)
    }
}
