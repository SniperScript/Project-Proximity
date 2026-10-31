package org.proximity.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.proximity.core.LiveScannerState

@SuppressLint("MissingPermission")
class WifiAwareMeshService(private val context: Context) {

    private val wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager?
    private var awareSession: WifiAwareSession? = null
        private var discoverySession: DiscoverySession? = null

            // --- The Anti-Spam Memory Matrix ---
            private val processedAlerts = mutableMapOf<String, Long>()
            private val ALERT_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes of silence for duplicate alerts

            init {
                // Automatically attach to the Wi-Fi Aware interface upon creation
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
                    wifiAwareManager?.attach(object : AttachCallback() {
                        override fun onAttached(session: WifiAwareSession) {
                            awareSession = session
                            startListeningForSOS()
                        }
                        override fun onAttachFailed() {}
                    }, Handler(Looper.getMainLooper()))
                }
            }

            // --- 1. THE CRIER (The Victim) ---
            fun initializeMeshPublisher(victimId: Int, lat: Float, lon: Float, threatLevel: Int, batteryRemaining: Int) {
                val config = PublishConfig.Builder()
                .setServiceName("ProximitySOS")
                .setServiceSpecificInfo("SOS_HELP_$victimId".toByteArray())
                .build()

                awareSession?.publish(config, object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        discoverySession = session
                    }
                }, null)
            }

            // --- 2. THE BYSTANDER (The Rescuer) ---
            private fun startListeningForSOS() {
                val config = SubscribeConfig.Builder()
                .setServiceName("ProximitySOS")
                .build()

                awareSession?.subscribe(config, object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                        discoverySession = session
                    }

                    override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray, matchFilter: List<ByteArray>) {
                        val payload = String(serviceSpecificInfo)

                        // Only process if it's an SOS and Guardian Mode is ON
                        if (payload.startsWith("SOS_HELP") && LiveScannerState.isGuardianModeEnabled.value) {
                            val currentTime = System.currentTimeMillis()
                            val lastSeenTime = processedAlerts[payload] ?: 0L

                            // Only trigger the alarm if this specific victim hasn't been processed recently
                            if (currentTime - lastSeenTime > ALERT_COOLDOWN_MS) {

                                processedAlerts[payload] = currentTime

                                Handler(Looper.getMainLooper()).post {
                                    // FIXED: Using the new Receiver state!
                                    LiveScannerState.isEmergencyReceived.value = true
                                    Toast.makeText(context, "⚠️ MESH ALERT: EMERGENCY NEARBY! ⚠️", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }, null)
            }

            fun shutdown() {
                discoverySession?.close()
                awareSession?.close()
            }
}
