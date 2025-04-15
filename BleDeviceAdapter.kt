package com.example.bloothtomapapplication

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat

class BleDeviceAdapter(
    private val context: Context,
    private val devices: List<BluetoothDevice>
) : BaseAdapter() {

    override fun getCount(): Int = devices.size
    override fun getItem(position: Int): Any = devices[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val textView = TextView(context)
        val device = devices[position]

        // 检查权限
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            textView.text = "${device.name ?: "未知设备"}\n${device.address}"
        } else {
            textView.text = "权限未授予，无法显示设备信息"
        }

        textView.setPadding(20, 20, 20, 20)
        return textView
    }
}