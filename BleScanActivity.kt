package com.example.bloothtomapapplication

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class BleScanActivity : AppCompatActivity() {

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
    private var pendingDevice: BluetoothDevice? = null // 用于存储待连接的设备

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
//若已经获得权限，返回true
    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 11 及以下无需 BLUETOOTH_CONNECT
        }
    }
//请求权限
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
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                requestPermissionCode
            )
        }
    }

    private fun startBleScan() {
        if (!hasPermissions()) {
            Toast.makeText(this, "权限不足，请在设置中开启蓝牙相关权限", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }

        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "无法获取 BLE 扫描器", Toast.LENGTH_SHORT).show()
            return
        }

        bleScanner = bluetoothLeScanner

        try {
            bleScanner.startScan(scanCallback)
            Toast.makeText(this, "开始扫描BLE设备...", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "权限异常，无法扫描BLE", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this@BleScanActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            if (!devices.contains(result.device)) {
                devices.add(result.device)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            Toast.makeText(this, "没有连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        runOnUiThread {
                            connectionStatus.text = "蓝牙状态: 已连接"
                            Toast.makeText(this@BleScanActivity, "已连接到设备", Toast.LENGTH_SHORT).show()
                        }
                        if (ActivityCompat.checkSelfPermission(
                                this@BleScanActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        runOnUiThread {
                            connectionStatus.text = "蓝牙状态: 已断开"
                            Toast.makeText(this@BleScanActivity, "设备已断开连接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (ActivityCompat.checkSelfPermission(
                            this@BleScanActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    val service = gatt.services.firstOrNull()
                    service?.characteristics?.forEach { characteristic ->
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            setCharacteristicNotification(gatt, characteristic, true)
                        }
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    val data = characteristic.value
                    val hexData = data?.joinToString(" ") { String.format("0x%02X", it) } ?: "无数据"
                    runOnUiThread {
                        receivedData.text = "接收到的数据: $hexData"
                    }
                }
            })
        } catch (e: SecurityException) {
            Toast.makeText(this, "连接设备失败：权限异常", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setCharacteristicNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ) {
        if (!hasConnectPermission()) {
            Toast.makeText(this, "没有连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            gatt.setCharacteristicNotification(characteristic, enable)
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        } catch (e: SecurityException) {
            Toast.makeText(this, "设置通知失败：权限异常", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (pendingDevice != null) {
                    connectToDevice(pendingDevice!!)
                    pendingDevice = null
                } else {
                    startBleScan()
                }
            } else {
                Toast.makeText(this, "权限未授予，操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 检查是否有 BLUETOOTH_CONNECT 权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                bluetoothGatt?.close()
            } catch (e: SecurityException) {
                Toast.makeText(this, "关闭蓝牙连接失败：权限异常", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "没有权限关闭蓝牙连接", Toast.LENGTH_SHORT).show()
        }
    }
}
