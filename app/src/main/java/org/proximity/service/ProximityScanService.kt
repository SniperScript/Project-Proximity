package org.proximity.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.proximity.core.LiveScannerState

@SuppressLint("MissingPermission")
class ProximityScanService : Service() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
        private var bleScanner: BluetoothLeScanner? = null

            private val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    val mac = result.device.address
                    val rssi = result.rssi

                    // --- THE BLE SOS INTERCEPTOR ---
                    val sosData = record.getManufacturerSpecificData(0x0505)

                    // Check if Guardian Mode is ON and we haven't manually muted this specific MAC
                    if (sosData != null && LiveScannerState.isGuardianModeEnabled.value && !LiveScannerState.mutedSosMacs.contains(mac)) {

                        // If this is the FIRST time we see this SOS, trigger the Android Notification
                        if (!LiveScannerState.isEmergencyReceived.value) {
                            LiveScannerState.isEmergencyReceived.value = true
                            LiveScannerState.sosTargetMac.value = mac
                            triggerSystemSosNotification()
                        }

                        LiveScannerState.updateDevice(mac, rssi, 1.0f) // Keep the target red on radar
                    } else {
                        // Normal radar tracking
                        LiveScannerState.updateDevice(mac, rssi, 0.2f)
                    }
                }
            }

            override fun onCreate() {
                super.onCreate()
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothAdapter = btManager.adapter
                bleScanner = bluetoothAdapter.bluetoothLeScanner
                startForeground(1001, createServiceNotification())
                startHardwareScan()
            }

            private fun startHardwareScan() {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
                    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                    try { bleScanner?.startScan(null, settings, scanCallback) } catch (e: Exception) {}
            }

            private fun triggerSystemSosNotification() {
                val manager = getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel("sos_alerts", "Critical SOS Alerts", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)

                val notification = NotificationCompat.Builder(this, "sos_alerts")
                .setContentTitle("🚨 EMERGENCY SOS NEARBY 🚨")
                .setContentText("A user in your proximity has triggered a distress beacon! Open Proximity to view radar.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500)) // Violent pulsing vibration
                .build()

                manager.notify(1003, notification)
            }

            private fun createServiceNotification(): Notification {
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("prox_channel", "Proximity Guard", NotificationManager.IMPORTANCE_LOW))
                return NotificationCompat.Builder(this, "prox_channel")
                .setContentTitle("Proximity Shield Active")
                .setContentText("Monitoring local spectrum.")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()
            }

            override fun onBind(intent: Intent?): IBinder? = null

            override fun onDestroy() {
                try { bleScanner?.stopScan(scanCallback) } catch (e: Exception) {}
                super.onDestroy()
            }
}
