package com.example.bloothtomapapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import android.os.Build
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.BitmapDescriptorFactory
import android.graphics.Bitmap
import android.graphics.Canvas


class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap
    private var currentMarker: Marker? = null

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("latitude", 0.0) ?: return
            val lon = intent.getDoubleExtra("longitude", 0.0)
            val time = intent.getStringExtra("utc_time") ?: "未知时间"

            val latLng = LatLng(lat, lon)
            updateMarker(latLng, time)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)
        SDKInitializer.setCoordType(CoordType.BD09LL)

        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.bmapView)
        baiduMap = mapView.map

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                gpsReceiver,
                IntentFilter("com.example.bloothtomapapplication.UPDATE_GPS"),
                RECEIVER_NOT_EXPORTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(
                gpsReceiver,
                IntentFilter("com.example.bloothtomapapplication.UPDATE_GPS"),
                RECEIVER_EXPORTED
            )
        }
    }

    private fun updateMarker(location: LatLng, time: String) {
        // 有新的经纬度数据传入时，更新地图中心点
        val mapStatusUpdate = MapStatusUpdateFactory.newLatLngZoom(location, 18f)
        baiduMap.setMapStatus(mapStatusUpdate)

        // 清除旧 Marker
        currentMarker?.remove()

        // 将 XML Drawable 转换为 Bitmap 并生成 BitmapDescriptor
        val drawable = resources.getDrawable(R.drawable.marker_icon, theme)
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight

        if (width > 0 && height > 0) {
            drawable.setBounds(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)

            val markerIcon = BitmapDescriptorFactory.fromBitmap(bitmap)
            val options = MarkerOptions().position(location).icon(markerIcon)
            currentMarker = baiduMap.addOverlay(options) as Marker
        } else {
            Toast.makeText(this, "Marker 图标加载失败", Toast.LENGTH_SHORT).show()
        }

        Toast.makeText(this, "更新时间：$time", Toast.LENGTH_SHORT).show()
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gpsReceiver)
        mapView.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
