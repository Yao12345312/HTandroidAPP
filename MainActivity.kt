package com.example.bloothtomapapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查并请求权限
        if (!hasRequiredPermissions()) {
            requestMissingPermissions()
        }

        // 设置蓝牙图标点击事件
        val bluetoothIcon = findViewById<ImageView>(R.id.img_bluetooth)
        bluetoothIcon.setOnClickListener {
            if (hasRequiredPermissions()) {
                val intent = Intent(this, BleScanActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "请先授予必要的权限", Toast.LENGTH_SHORT).show()
                requestMissingPermissions()
            }
        }
    }

    // 检查是否已授予所需的权限
    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 获取所需的权限列表
    private fun getRequiredPermissions(): List<String> {
        val requiredPermissions = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12 及以上需要以下蓝牙权限
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // 如果需要位置权限，可以取消注释以下行
         requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        return requiredPermissions
    }

    // 请求未授予的权限
    private fun requestMissingPermissions() {
        val missingPermissions = getRequiredPermissions().filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_PERMISSION_CODE)
        } else {
            Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show()
        }
    }

    // 处理权限请求的结果
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 权限已授予
                Toast.makeText(this, "所有必要权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                // 权限被拒绝
                Toast.makeText(this, "权限被拒绝，某些功能可能无法使用", Toast.LENGTH_SHORT).show()
            }
        }
    }
}