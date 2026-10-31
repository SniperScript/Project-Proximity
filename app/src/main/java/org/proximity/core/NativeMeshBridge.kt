package org.proximity.core

object NativeMeshBridge {

    init {
        System.loadLibrary("proximity-native")
    }

    /**
     * Packs telemetry into a 14-byte raw buffer via native C++.
     */
    external fun packSOSPayload(
        victimId: Int,
        latitude: Float,
        longitude: Float,
        threatLevel: Int,
        batteryRemaining: Int
    ): ByteArray
}