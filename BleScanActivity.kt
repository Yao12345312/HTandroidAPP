package com.example.bloothtomapapplication

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class BleScanActivity : AppCompatActivity() {

    private val TAG = "BleScanActivity"
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private lateinit var bleScanner: BluetoothLeScanner
    private lateinit var listView: ListView
    private lateinit var connectionStatus: TextView
    private lateinit var receivedData: TextView
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var adapter: BleDeviceAdapter
    private val requestPermissionCode = 100
    private var bluetoothGatt: BluetoothGatt? = null
    private var pendingDevice: BluetoothDevice? = null
    private var latestData: String = "无数据"
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scan)

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        connectionStatus = findViewById(R.id.connection_status)
        receivedData = findViewById(R.id.received_data)
        listView = findViewById(R.id.ble_list_view)
        adapter = BleDeviceAdapter(this, devices)
        listView.adapter = adapter

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startBleScan()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = devices[position]
            pendingDevice = device
            if (!hasConnectPermission()) {
                requestConnectPermission()
            } else {
                connectToDevice(device)
            }
        }

        startDataRefresh()
    }

    private fun hasPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestPermissionCode)
    }

    private fun requestConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), requestPermissionCode)
        }
    }

    private fun startBleScan() {
        if (!hasPermissions()) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "无法获取 BLE 扫描器", Toast.LENGTH_SHORT).show()
            return
        }

        bleScanner = scanner
        try {
            bleScanner.startScan(scanCallback)
            Toast.makeText(this, "开始扫描BLE设备...", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "权限异常，无法扫描BLE", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasConnectPermission()) return

            if (!devices.contains(result.device)) {
                devices.add(result.device)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasConnectPermission()) return

        try {
            bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        runOnUiThread {
                            connectionStatus.text = "蓝牙状态: 已连接"
                            Toast.makeText(this@BleScanActivity, "已连接到设备", Toast.LENGTH_SHORT).show()
                        }
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        runOnUiThread {
                            connectionStatus.text = "蓝牙状态: 已断开"
                            Toast.makeText(this@BleScanActivity, "设备已断开连接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        for (service in gatt.services) {
                            for (characteristic in service.characteristics) {
                                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    setCharacteristicNotification(gatt, characteristic, true)
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "服务发现失败: $status")
                    }
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Descriptor 写入成功: ${descriptor.uuid}")
                    } else {
                        Log.e(TAG, "Descriptor 写入失败: ${descriptor.uuid} 状态=$status")
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    val data = characteristic.value
                    latestData = data?.joinToString(" ") { String.format("0x%02X", it) } ?: "无数据"
                    Log.d(TAG, "接收到数据: $latestData")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "连接蓝牙设备失败：权限不足", e)
        }
    }

    private fun setCharacteristicNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ) {
        if (!hasConnectPermission()) return

        try {
            val result = gatt.setCharacteristicNotification(characteristic, enable)
            Log.d(TAG, "设置通知结果: $result")

            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                descriptor.value = if (enable)
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                Log.e(TAG, "特征 ${characteristic.uuid} 不包含通知描述符")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "设置通知失败：权限不足", e)
        }
    }

    private fun startDataRefresh() {
        handler.post(object : Runnable {
            override fun run() {
                receivedData.text = "接收到的数据: $latestData"
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                pendingDevice?.let {
                    connectToDevice(it)
                    pendingDevice = null
                } ?: startBleScan()
            } else {
                Toast.makeText(this, "权限未授予", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothGatt?.close()
        } catch (_: SecurityException) {
        }
        handler.removeCallbacksAndMessages(null)
    }
}
