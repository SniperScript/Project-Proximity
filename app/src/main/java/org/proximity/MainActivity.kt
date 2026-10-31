package org.proximity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.proximity.core.EmergencyTriggerManager
import org.proximity.core.LiveScannerState
import org.proximity.core.LiveDevice
import org.proximity.core.NecklaceTetherManager
import org.proximity.service.ProximityScanService
import org.proximity.service.WifiAwareMeshService

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private var activeRingtone: Ringtone? = null
        private lateinit var meshService: WifiAwareMeshService
            private lateinit var triggerManager: EmergencyTriggerManager
                private lateinit var tetherManager: NecklaceTetherManager
                    private var bleAdvertiseCallback: AdvertiseCallback? = null

                        private val isGuardActive = mutableStateOf(false)
                        private var isRinging = false

                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            requestVitalPermissions()

                            meshService = WifiAwareMeshService(this)
                            triggerManager = EmergencyTriggerManager(this) { dispatchOfflineSOS() }

                            tetherManager = NecklaceTetherManager(this) {
                                runOnUiThread {
                                    dispatchOfflineSOS()
                                    Toast.makeText(this, "EMERGENCY: Necklace Button Triggered!", Toast.LENGTH_LONG).show()
                                }
                            }
                            tetherManager.startTetherScan()

                            setContent {
                                ProximityApp(
                                    isGuardActive = isGuardActive.value,
                                    onToggleGuard = { toggleGuardState() },
                                             onManualSOS = { dispatchOfflineSOS() },
                                             onCancelSOS = { cancelEmergencyState() },
                                             onTriggerFakeCall = { toggleRealFakeCall() },
                                             onSafeRoute = { triggerSafeRouteMockup() }
                                )
                            }
                        }

                        private fun cancelEmergencyState() {
                            LiveScannerState.cancelEmergency()

                            // Stop BLE Advertising
                            try {
                                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                                bluetoothManager.adapter?.bluetoothLeAdvertiser?.stopAdvertising(bleAdvertiseCallback)
                            } catch (e: Exception) {}

                            // Restart the Mesh service to stop broadcasting but keep listening
                            meshService.shutdown()
                            meshService = WifiAwareMeshService(this)

                            Toast.makeText(this, "Emergency protocols dismissed.", Toast.LENGTH_SHORT).show()
                        }

                        private fun toggleRealFakeCall() {
                            if (isRinging && activeRingtone?.isPlaying == true) {
                                activeRingtone?.stop()
                                isRinging = false
                            } else {
                                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                activeRingtone = RingtoneManager.getRingtone(applicationContext, uri)
                                activeRingtone?.play()
                                isRinging = true
                            }
                        }

                        private fun triggerSafeRouteMockup() {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=police+station")))
                            } catch (e: Exception) {
                                Toast.makeText(this, "Navigation module not found.", Toast.LENGTH_LONG).show()
                            }
                        }

                        private fun dispatchOfflineSOS() {
                            LiveScannerState.isSosActive.value = true

                            try { meshService.initializeMeshPublisher(victimId = 1001, lat = 0f, lon = 0f, threatLevel = 2, batteryRemaining = 85) } catch (e: Exception) {}

                            try {
                                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                                val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser
                                val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build()
                                val data = AdvertiseData.Builder().addManufacturerData(0x0505, byteArrayOf(1, 1)).build()

                                bleAdvertiseCallback = object : AdvertiseCallback() {}
                                advertiser?.startAdvertising(settings, data, bleAdvertiseCallback)
                            } catch (e: Exception) {}

                            Toast.makeText(this, "SOS BROADCASTING LOCALLY!", Toast.LENGTH_SHORT).show()
                        }

                        private fun requestVitalPermissions() {
                            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissions.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                                if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
                        }

                        private fun toggleGuardState() {
                            isGuardActive.value = !isGuardActive.value
                            val serviceIntent = Intent(this, ProximityScanService::class.java)
                            if (isGuardActive.value) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
                            } else {
                                stopService(serviceIntent)
                                triggerManager.abortEmergency()
                            }
                        }

                        override fun onDestroy() {
                            tetherManager.disconnect()
                            meshService.shutdown()
                            activeRingtone?.stop()
                            try { getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeAdvertiser?.stopAdvertising(bleAdvertiseCallback) } catch (e: Exception) {}
                            super.onDestroy()
                        }
}

// UI
enum class ScreenState { DASHBOARD, NEARBY_DEVICES }

@Composable
fun ProximityApp(isGuardActive: Boolean, onToggleGuard: () -> Unit, onManualSOS: () -> Unit, onCancelSOS: () -> Unit, onTriggerFakeCall: () -> Unit, onSafeRoute: () -> Unit) {
    var currentScreen by remember { mutableStateOf(ScreenState.DASHBOARD) }
    MaterialTheme(
        colorScheme = darkColorScheme(background = Color(0xFF0D0D12), surface = Color(0xFF1A1A24), primary = Color(0xFF00FFC4), secondary = Color(0xFF8B5CF6), error = Color(0xFFFF3366))
    ) {
        when (currentScreen) {
            ScreenState.DASHBOARD -> DashboardScreen(isGuardActive, onToggleGuard, onManualSOS, onCancelSOS, onTriggerFakeCall, onSafeRoute) { currentScreen = ScreenState.NEARBY_DEVICES }
            ScreenState.NEARBY_DEVICES -> NearbyDevicesScreen { currentScreen = ScreenState.DASHBOARD }
        }
    }
}
@Composable
fun DashboardScreen(isGuardActive: Boolean, onToggleGuard: () -> Unit, onManualSOS: () -> Unit, onCancelSOS: () -> Unit, onTriggerFakeCall: () -> Unit, onSafeRoute: () -> Unit, onNavigateToLedger: () -> Unit) {
    val isSafeHaven = LiveScannerState.isSafeHavenActive.value
    val isSosActive = LiveScannerState.isSosActive.value
    val isEmergencyReceived = LiveScannerState.isEmergencyReceived.value
    val guardianMode = LiveScannerState.isGuardianModeEnabled.value

    // --- RESTORED ESP32 STATE ---
    val isTethered = LiveScannerState.isTetherConnected.value
    val battery = LiveScannerState.tetherBatteryLevel.intValue

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PROXIMITY", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (guardianMode) "Guardian ON" else "Guardian OFF", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = guardianMode, onCheckedChange = { LiveScannerState.isGuardianModeEnabled.value = it }, modifier = Modifier.scale(0.7f))
            }
        }

        if (isSosActive || isEmergencyReceived) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = if (isSosActive) "SOS BROADCASTING: SEEKING GUARDIANS" else "EMERGENCY NEARBY: TARGET MARKED ON RADAR",
                     color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- ESP32 COMPANION CARD ---
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Companion Pendant (ESP32)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = if (isTethered) "Status: Linked | Battery: ${if (battery > 0) "$battery%" else "98%"}" else "Status: Searching for Pendant...",
                         color = if (isTethered) Color(0xFF00FFC4) else Color.Gray,
                         fontSize = 12.sp
                    )
                }
                Box(modifier = Modifier.size(12.dp).background(if (isTethered) Color(0xFF00FFC4) else Color(0xFFFF3366), shape = RoundedCornerShape(50)))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Radar
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { RadarVisualizer(isActive = isGuardActive, isSafeHaven = isSafeHaven) }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Safe Route", "🗺️", MaterialTheme.colorScheme.secondary, Modifier.weight(1f), onSafeRoute)
                ActionCard("Phantom Call", "📞", Color(0xFF42A5F5), Modifier.weight(1f), onTriggerFakeCall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(if (isSafeHaven) "Haven: ACTIVE" else "Set Safe Haven", "🛡️", if (isSafeHaven) Color.Green else Color.DarkGray, Modifier.weight(1f)) {
                    if (!isSafeHaven) LiveScannerState.markCurrentEnvironmentAsSafe() else LiveScannerState.isSafeHavenActive.value = false
                }

                // THE DYNAMIC KILL SWITCH
                if (isSosActive || isEmergencyReceived) {
                    ActionCard("DISMISS ALERT", "🛑", MaterialTheme.colorScheme.error, Modifier.weight(1f), onCancelSOS)
                } else {
                    ActionCard("SOS ALARM", "🚨", MaterialTheme.colorScheme.error, Modifier.weight(1f), onManualSOS)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onNavigateToLedger, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(54.dp)) { Text("Threat Ledger", color = Color.White, fontWeight = FontWeight.Bold) }
            Button(onClick = onToggleGuard, colors = ButtonDefaults.buttonColors(containerColor = if (isGuardActive) Color(0xFF333340) else MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(54.dp)) { Text(if (isGuardActive) "STOP" else "SCAN", color = if (isGuardActive) Color.White else Color.Black, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
fun ActionCard(title: String, icon: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)), shape = RoundedCornerShape(16.dp), modifier = modifier.height(76.dp).clickable { onClick() }) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 22.sp); Spacer(modifier = Modifier.height(2.dp)); Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun NearbyDevicesScreen(onNavigateBack: () -> Unit) {
    val devices = LiveScannerState.activeDevices.values.toList().sortedByDescending { it.threatScore }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)) { Text("← Back", color = Color.White) }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Threat Ledger", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items(devices) { device -> CleanDeviceCard(device); Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun CleanDeviceCard(device: LiveDevice) {
    val distance = device.getEstimatedDistanceMeters()
    val isSosTarget = (device.mac == LiveScannerState.sosTargetMac.value)
    val color = if (isSosTarget || device.threatScore >= 0.75f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (isSosTarget) "DISTRESS BEACON" else "Tracker Beacon", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("ID: ${device.mac}", color = Color.Gray, fontSize = 12.sp)
            }
            Text("${String.format("%.1f", distance)}m", color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun RadarVisualizer(isActive: Boolean, isSafeHaven: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val radiusRatio by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val maxRadius = size.minDimension / 2
            for (i in 1..4) drawCircle(color = Color.DarkGray.copy(alpha = 0.3f), radius = maxRadius * (i / 4f), center = center, style = Stroke(width = 2f))

                if (isActive && !isSafeHaven) {
                    drawCircle(color = Color(0xFF00FFC4).copy(alpha = 1f - radiusRatio), radius = maxRadius * radiusRatio, center = center, style = Stroke(width = 6f))

                    val hasEmergency = LiveScannerState.isEmergencyReceived.value
                    val targetMac = LiveScannerState.sosTargetMac.value

                    LiveScannerState.activeDevices.values.forEach { device ->
                        val isSosTarget = (device.mac == targetMac)

                        // THE TUNNEL VISION FILTER: If there is an emergency, hide all mundane dots!
                        if (hasEmergency && !isSosTarget) return@forEach

                            val distance = device.getEstimatedDistanceMeters().coerceAtMost(100.0)
                            val radiusAtDistance = maxRadius * (distance / 100.0).toFloat()
                            val angleRad = Math.toRadians(device.angle.toDouble())
                            val x = center.x + (radiusAtDistance * Math.cos(angleRad)).toFloat()
                            val y = center.y + (radiusAtDistance * Math.sin(angleRad)).toFloat()

                            if (isSosTarget) {
                                drawCircle(color = Color.Red, radius = 24f, center = Offset(x, y)) // Huge Red Target
                                drawCircle(color = Color.White, radius = 24f, center = Offset(x, y), style = Stroke(width = 4f)) // White Outline
                            } else {
                                drawCircle(color = Color(0xFF00FFC4), radius = 10f, center = Offset(x, y))
                            }
                    }
                }
                if (isSafeHaven) drawCircle(color = Color.Green.copy(alpha = 0.1f), radius = maxRadius, center = center)
        }
    }
}
