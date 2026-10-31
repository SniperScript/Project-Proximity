package org.proximity.core

import kotlin.math.abs
import kotlin.math.pow

data class BleScanObservation(
    val macAddress: String,
    val rssi: Int,
    val timestampMs: Long,
    val manufacturerData: ByteArray?,
    val latitude: Double,
    val longitude: Double
)

data class DeviceTrackRecord(
    var macAddress: String,
    var firstSeenMs: Long,
    var lastSeenMs: Long,
    var lastRssi: Int,
    val rssiHistory: ArrayDeque<Int> = ArrayDeque(20),
    val observedLocations: MutableList<Pair<Double, Double>> = mutableListOf(),
    var isKnownTrackerPayload: Boolean = false,
    var threatScore: Float = 0.0f
)

class TrackerHeuristicsEngine {

    companion object {
        private const val RSSI_STABILITY_VARIANCE_MAX = 25.0
        private const val SPATIAL_DISTANCE_THRESHOLD_METERS = 100.0
        private const val MAC_HANDOFF_WINDOW_MS = 3000L
        private const val MAC_HANDOFF_RSSI_TOLERANCE_DB = 6
        private const val CRITICAL_THREAT_THRESHOLD = 0.75f

        // Apple FindMy Company ID (0x004C) Byte signature for AirTag beacons
        private val APPLE_AIRTAG_HEADER = byteArrayOf(0x12, 0x19, 0x00)
    }

    private val trackedDevices = HashMap<String, DeviceTrackRecord>()

    fun processObservation(obs: BleScanObservation): DeviceTrackRecord {
        val currentTime = obs.timestampMs
        val isAirtag = isAppleTracker(obs.manufacturerData)

        // Step 1: Detect MAC Randomization Handoff
        val resolvedMac = correlateMacRotation(obs.rssi, currentTime) ?: obs.macAddress

        val record = trackedDevices.getOrPut(resolvedMac) {
            DeviceTrackRecord(
                macAddress = resolvedMac,
                firstSeenMs = currentTime,
                lastSeenMs = currentTime,
                lastRssi = obs.rssi,
                isKnownTrackerPayload = isAirtag
            )
        }

        // Update Record State
        record.lastSeenMs = currentTime
        record.lastRssi = obs.rssi
        if (record.rssiHistory.size >= 20) record.rssiHistory.removeFirst()
        record.rssiHistory.addLast(obs.rssi)

        if (isAirtag) record.isKnownTrackerPayload = true

        // Track distinct locations separated by threshold distance
        if (record.observedLocations.isEmpty() || 
            haversineDistance(record.observedLocations.last(), Pair(obs.latitude, obs.longitude)) > SPATIAL_DISTANCE_THRESHOLD_METERS) {
            record.observedLocations.add(Pair(obs.latitude, obs.longitude))
        }

        // Step 2: Compute Threat Score
        record.threatScore = computeThreatScore(record)
        return record
    }

    private fun correlateMacRotation(rssi: Int, now: Long): String? {
        for ((mac, record) in trackedDevices) {
            val timeDelta = now - record.lastSeenMs
            val rssiDelta = abs(rssi - record.lastRssi)
            // If an existing device went silent right when this one appeared at the same signal strength
            if (timeDelta in 1..MAC_HANDOFF_WINDOW_MS && rssiDelta <= MAC_HANDOFF_RSSI_TOLERANCE_DB) {
                return mac
            }
        }
        return null
    }

    private fun computeThreatScore(record: DeviceTrackRecord): Float {
        var score = 0.0f

        // 1. Spatial Transition Weight (Max 0.40)
        val distinctLocations = record.observedLocations.size
        if (distinctLocations >= 3) score += 0.40f
        else if (distinctLocations == 2) score += 0.20f

        // 2. RSSI Stability / Proximity Weight (Max 0.30)
        if (record.rssiHistory.size >= 5) {
            val variance = calculateVariance(record.rssiHistory)
            val avgRssi = record.rssiHistory.average()
            if (variance < RSSI_STABILITY_VARIANCE_MAX && avgRssi in -60.0..-35.0) {
                score += 0.30f
            }
        }

        // 3. Known Hardware Payload Signature (Max 0.30)
        if (record.isKnownTrackerPayload) {
            score += 0.30f
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    private fun isAppleTracker(manufacturerData: ByteArray?): Boolean {
        if (manufacturerData == null || manufacturerData.size < 4) return false
        // Apple 0x004C payload prefix check
        return manufacturerData[0] == APPLE_AIRTAG_HEADER[0] && 
               manufacturerData[1] == APPLE_AIRTAG_HEADER[1]
    }

    private fun calculateVariance(history: Collection<Int>): Double {
        val mean = history.average()
        return history.sumOf { (it - mean).pow(2) } / history.size
    }

    private fun haversineDistance(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val r = 6371e3
        val lat1 = Math.toRadians(p1.first)
        val lat2 = Math.toRadians(p2.first)
        val dLat = Math.toRadians(p2.first - p1.first)
        val dLon = Math.toRadians(p2.second - p1.second)
        val a = Math.sin(dLat / 2).pow(2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2).pow(2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}