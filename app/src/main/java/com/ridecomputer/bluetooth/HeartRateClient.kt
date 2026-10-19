package com.ridecomputer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

sealed class HrEvent {
    data class Measurement(val bpm: Int) : HrEvent()
    data class StatusChanged(val status: String) : HrEvent()
}

class HeartRateClient(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var gatt: BluetoothGatt? = null

    @Volatile
    private var isConnectingOrConnected = false

    @SuppressLint("MissingPermission")
    fun scanAndConnect(): Flow<HrEvent> = callbackFlow {
        val manager = bluetoothManager
        if (manager == null) {
            Log.e(TAG, "BluetoothManager is not available on this device")
            trySend(HrEvent.StatusChanged("No Bluetooth"))
            close()
            return@callbackFlow
        }

        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter is disabled")
            trySend(HrEvent.StatusChanged("Bluetooth Off"))
            close()
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner is null")
            trySend(HrEvent.StatusChanged("Scanner Error"))
            close()
            return@callbackFlow
        }

        trySend(HrEvent.StatusChanged("Searching..."))
        Log.d(TAG, "Starting BLE scan for Heart Rate Service...")

        var scanCallbackRef: ScanCallback? = null

        fun startScanning(scanCb: ScanCallback) {
            try {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                scanner.startScan(null, settings, scanCb)
                Log.d(TAG, "Unfiltered BLE scan started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BLE scan: ${e.message}", e)
                trySend(HrEvent.StatusChanged("Scan Failed"))
            }
        }

        fun stopScanning(scanCb: ScanCallback?) {
            if (scanCb == null) return
            try {
                scanner.stopScan(scanCb)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        trySend(HrEvent.StatusChanged("Connected"))
                        Log.d(TAG, "Connected to ${g.device.name ?: g.device.address}, discovering services...")
                        g.discoverServices()
                    } else {
                        Log.w(TAG, "Connection failed with status=$status, cleaning up")
                        trySend(HrEvent.StatusChanged("Conn Error ($status)"))
                        cleanUpGatt(g)
                        scanCallbackRef?.let {
                            trySend(HrEvent.StatusChanged("Searching..."))
                            startScanning(it)
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT")
                    trySend(HrEvent.StatusChanged("Disconnected"))
                    cleanUpGatt(g)
                    scanCallbackRef?.let {
                        trySend(HrEvent.StatusChanged("Searching..."))
                        startScanning(it)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                Log.d(TAG, "onServicesDiscovered: status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Service discovery failed with status $status")
                    cleanUpGatt(g)
                    scanCallbackRef?.let { startScanning(it) }
                    return
                }

                val service = g.getService(HR_SERVICE_UUID)
                if (service == null) {
                    Log.w(TAG, "Device ${g.device.name ?: g.device.address} does NOT have Heart Rate Service (0x180D)")
                    trySend(HrEvent.StatusChanged("No HR Service"))
                    cleanUpGatt(g)
                    scanCallbackRef?.let { startScanning(it) }
                    return
                }

                for (c in service.characteristics) {
                    Log.d(TAG, "HR Service (0x180D) characteristic: ${c.uuid}, properties=${c.properties}")
                }

                val characteristic = service.getCharacteristic(HR_MEASUREMENT_UUID)
                if (characteristic == null) {
                    Log.w(TAG, "HR Measurement Characteristic (0x2A37) not found")
                    return
                }

                val setNotifySuccess = g.setCharacteristicNotification(characteristic, true)
                Log.d(TAG, "setCharacteristicNotification success=$setNotifySuccess")

                val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
                if (descriptor == null) {
                    Log.w(TAG, "CCC Descriptor (0x2902) not found")
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val writeRes = g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    Log.d(TAG, "writeDescriptor (Tiramisu+) result=$writeRes")
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    val writeRes = g.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (Legacy) result=$writeRes")
                }
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                Log.d(TAG, "onDescriptorWrite: status=$status, uuid=${descriptor.uuid}")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    trySend(HrEvent.StatusChanged("Connected"))

                    // On devices like Mi Band / Amazfit, write to Control Point (0x2A39) to start continuous streaming
                    try {
                        val service = g.getService(HR_SERVICE_UUID)
                        val controlPoint = service?.getCharacteristic(HR_CONTROL_POINT_UUID)
                        if (controlPoint != null) {
                            Log.d(TAG, "Found HR Control Point (0x2A39), sending start continuous measurement command...")
                            val cmd = byteArrayOf(0x15, 0x01, 0x01)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                g.writeCharacteristic(controlPoint, cmd, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                            } else {
                                @Suppress("DEPRECATION")
                                controlPoint.value = cmd
                                @Suppress("DEPRECATION")
                                g.writeCharacteristic(controlPoint)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not write to HR Control Point: ${e.message}")
                    }
                }
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                Log.d(TAG, "onCharacteristicWrite: uuid=${characteristic.uuid}, status=$status")
            }

            @Deprecated("Deprecated in API 33, kept for minSdk 26 compat")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                    @Suppress("DEPRECATION")
                    val bpm = parseHeartRate(characteristic.value)
                    Log.d(TAG, "HR measurement received (legacy): $bpm bpm")
                    if (bpm != null) trySend(HrEvent.Measurement(bpm))
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                    val bpm = parseHeartRate(value)
                    Log.d(TAG, "HR measurement received: $bpm bpm")
                    if (bpm != null) trySend(HrEvent.Measurement(bpm))
                }
            }
        }

        fun connectToDevice(device: BluetoothDevice) {
            if (isConnectingOrConnected) return
            isConnectingOrConnected = true
            stopScanning(scanCallbackRef)
            trySend(HrEvent.StatusChanged("Connecting..."))
            Log.d(TAG, "Connecting to GATT at ${device.address} (${device.name ?: "Unknown"})...")
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        // Check if already bonded / paired device has HR name
        try {
            val bonded = adapter.bondedDevices
            if (bonded != null) {
                for (d in bonded) {
                    val name = d.name ?: ""
                    Log.d(TAG, "Checking bonded device: '$name' (${d.address})")
                    if (isLikelyHeartRateDevice(name)) {
                        Log.d(TAG, "Bonded device matches HR keyword! Attempting direct connection to $name")
                        connectToDevice(d)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking bonded devices: ${e.message}")
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (isConnectingOrConnected) return

                val device = result.device
                val deviceName = device.name ?: result.scanRecord?.deviceName ?: ""
                val isHrService = hasHeartRateService(result)
                val isHrName = isLikelyHeartRateDevice(deviceName)

                Log.d(
                    TAG,
                    "Discovered: '${deviceName.ifBlank { "<no name>" }}' [${device.address}], " +
                    "RSSI=${result.rssi}, isHrService=$isHrService, isHrName=$isHrName"
                )

                if (isHrService || isHrName) {
                    Log.d(TAG, "==> Target HR device identified! Connecting to '$deviceName' [${device.address}]")
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with errorCode=$errorCode")
                trySend(HrEvent.StatusChanged("Scan Error ($errorCode)"))
            }
        }
        scanCallbackRef = scanCallback

        if (!isConnectingOrConnected) {
            startScanning(scanCallback)
        }

        awaitClose {
            Log.d(TAG, "Closing HR flow, stopping scan and disconnecting GATT")
            stopScanning(scanCallbackRef)
            cleanUpGatt(gatt)
        }
    }

    private fun cleanUpGatt(g: BluetoothGatt?) {
        isConnectingOrConnected = false
        try {
            g?.disconnect()
            g?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up GATT: ${e.message}")
        }
        if (gatt == g) {
            gatt = null
        }
    }

    companion object {
        private const val TAG = "CyclocompHR"
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val HR_CONTROL_POINT_UUID: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

        fun hasHeartRateService(result: ScanResult): Boolean {
            val uuids = result.scanRecord?.serviceUuids
            if (uuids != null) {
                for (p in uuids) {
                    val s = p.uuid.toString().lowercase()
                    if (s.startsWith("0000180d") || s == HR_SERVICE_UUID.toString().lowercase()) {
                        return true
                    }
                }
            }
            val serviceData = result.scanRecord?.serviceData
            if (serviceData != null) {
                for (k in serviceData.keys) {
                    if (k.uuid.toString().lowercase().startsWith("0000180d")) {
                        return true
                    }
                }
            }
            // Check raw advertisement bytes for 16-bit 0x180D (stored little-endian: 0x0D, 0x18)
            val raw = result.scanRecord?.bytes
            if (raw != null) {
                for (i in 0 until raw.size - 1) {
                    if (raw[i] == 0x0D.toByte() && raw[i + 1] == 0x18.toByte()) {
                        return true
                    }
                }
            }
            return false
        }

        fun isLikelyHeartRateDevice(name: String): Boolean {
            if (name.isBlank()) return false
            val lower = name.lowercase()
            return lower.contains("hr") ||
                   lower.contains("heart") ||
                   lower.contains("polar") ||
                   lower.contains("garmin") ||
                   lower.contains("wahoo") ||
                   lower.contains("magene") ||
                   lower.contains("coospo") ||
                   lower.contains("igpsport") ||
                   lower.contains("xoss") ||
                   lower.contains("tickr") ||
                   lower.contains("strap") ||
                   lower.contains("band") ||
                   lower.contains("pulse") ||
                   lower.contains("coros") ||
                   lower.contains("suunto")
        }

        fun parseHeartRate(value: ByteArray?): Int? {
            if (value == null || value.isEmpty()) return null
            val flags = value[0].toInt() and 0xFF
            val is16Bit = (flags and 0x01) != 0
            return if (is16Bit && value.size >= 3) {
                (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            } else if (value.size >= 2) {
                value[1].toInt() and 0xFF
            } else {
                null
            }
        }
    }
}
