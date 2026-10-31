package org.proximity.core

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.pow

data class LiveDevice(
    val mac: String,
    var smoothedRssi: Float,
    var threatScore: Float,
    val angle: Float = (0..360).random().toFloat()
) {
    fun getEstimatedDistanceMeters(): Double {
        val txPower = -59.0
        val pathLossExponent = 2.5
        return 10.0.pow((txPower - smoothedRssi) / (10.0 * pathLossExponent))
    }
}

object LiveScannerState {
    val activeDevices = mutableStateMapOf<String, LiveDevice>()
    val scannedMacs = mutableSetOf<String>()
    val safeHavenMacs = mutableSetOf<String>()
    val mutedSosMacs = mutableSetOf<String>() // The Blacklist for dismissed cries

    var devicesScanned = mutableIntStateOf(0)
    var maxThreatScore = mutableFloatStateOf(0f)
    var isTetherConnected = mutableStateOf(false)
    var tetherBatteryLevel = mutableIntStateOf(0)
    var isSafeHavenActive = mutableStateOf(false)
    var isGuardianModeEnabled = mutableStateOf(true)

    var isSosActive = mutableStateOf(false)
    var isEmergencyReceived = mutableStateOf(false)
    var sosTargetMac = mutableStateOf<String?>(null)

    fun updateDevice(mac: String, rawRssi: Int, score: Float) {
        if (isSafeHavenActive.value) safeHavenMacs.add(mac)
            val finalScore = if (safeHavenMacs.contains(mac)) 0f else score

            val existing = activeDevices[mac]
            if (existing != null) {
                if (existing.smoothedRssi == 0f) existing.smoothedRssi = rawRssi.toFloat()
                    else existing.smoothedRssi = (existing.smoothedRssi * 0.85f) + (rawRssi.toFloat() * 0.15f)
                        if (finalScore > existing.threatScore) existing.threatScore = finalScore
            } else {
                activeDevices[mac] = LiveDevice(mac, rawRssi.toFloat(), finalScore)
            }
    }

    fun markCurrentEnvironmentAsSafe() {
        isSafeHavenActive.value = true
        safeHavenMacs.addAll(activeDevices.keys)
        activeDevices.values.forEach { it.threatScore = 0f }
    }

    fun cancelEmergency() {
        // If we are dismissing an incoming SOS, add its MAC to the mute list so it stops bothering us!
        sosTargetMac.value?.let { mutedSosMacs.add(it) }

        isSosActive.value = false
        isEmergencyReceived.value = false
        sosTargetMac.value = null
    }

    fun reset() {
        activeDevices.clear(); scannedMacs.clear(); safeHavenMacs.clear(); mutedSosMacs.clear()
        isSafeHavenActive.value = false
        isSosActive.value = false
        isEmergencyReceived.value = false
        sosTargetMac.value = null
    }
}
