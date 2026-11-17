package kr.telelian.examguard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager

/**
 * 회전 벡터 센서로 폰이 향한 방위(azimuth, 0=북, 시계방향)를 제공한다.
 * 저역통과 필터로 떨림을 완화한다.
 */
class CompassProvider(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Suppress("DEPRECATION")
    private val display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

    val isAvailable: Boolean get() = rotationSensor != null

    private var onHeading: ((Float) -> Unit)? = null
    private var smoothed = Float.NaN

    private val rotationMatrix = FloatArray(9)
    private val adjusted = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start(onHeading: (Float) -> Unit) {
        this.onHeading = onHeading
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onHeading = null
        smoothed = Float.NaN
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // 화면 회전 보정
        val (axisX, axisY) = when (display?.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjusted)
        SensorManager.getOrientation(adjusted, orientation)

        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f

        smoothed = if (smoothed.isNaN()) azimuth else lowPass(azimuth, smoothed)
        onHeading?.invoke((smoothed + 360f) % 360f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 각도 저역통과 (0/360 경계 보정). */
    private fun lowPass(target: Float, prev: Float): Float {
        var delta = target - prev
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return prev + ALPHA * delta
    }

    companion object {
        private const val ALPHA = 0.15f
    }
}
