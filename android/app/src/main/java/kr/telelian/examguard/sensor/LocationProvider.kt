package kr.telelian.examguard.sensor

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult

/** 현재 위치(위도/경도)를 주기적으로 제공한다. 권한은 호출 측에서 보장. */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private var onLocation: ((lat: Double, lng: Double) -> Unit)? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocation?.invoke(it.latitude, it.longitude) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(onLocation: (lat: Double, lng: Double) -> Unit) {
        this.onLocation = onLocation
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        try {
            client.requestLocationUpdates(request, callback, null)
            client.lastLocation.addOnSuccessListener { loc ->
                loc?.let { onLocation(it.latitude, it.longitude) }
            }
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        client.removeLocationUpdates(callback)
        onLocation = null
    }
}
