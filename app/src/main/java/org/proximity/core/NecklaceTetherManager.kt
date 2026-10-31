package org.proximity.core

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

@SuppressLint("MissingPermission")
class NecklaceTetherManager(
    private val context: Context,
        private val onEmergencyTriggered: () -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun startTetherScan() {
            val scanner = adapter?.bluetoothLeScanner ?: return
            val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

            scanner.startScan(listOf(filter), settings, object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (result.device.name == "ProximityNecklace" || result.scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true) {
                        scanner.stopScan(this)
                        connectToNecklace(result.device)
                    }
                }
            })
        }

        private fun connectToNecklace(device: BluetoothDevice) {
            bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LiveScannerState.isTetherConnected.value = true
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LiveScannerState.isTetherConnected.value = false
                        startTetherScan() // Automatically retry scan if lost
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(CHAR_UUID)
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(CCCD_UUID)
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    val data = characteristic.getStringValue(0) ?: ""
                    if (data == "EMERGENCY_TRIGGER") {
                        onEmergencyTriggered()
                    } else if (data.startsWith("BAT:")) {
                        val bat = data.removePrefix("BAT:").toIntOrNull() ?: 98
                        LiveScannerState.tetherBatteryLevel.intValue = bat
                    }
                }
            })
        }

        fun disconnect() {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
}
