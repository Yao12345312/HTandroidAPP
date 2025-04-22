package com.example.bloothtomapapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化百度地图 SDK
        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)
        SDKInitializer.setCoordType(CoordType.BD09LL)

        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.bmapView)
        baiduMap = mapView.map

        // 示例坐标（可替换为你的GPS解析结果）
        val latitude = 39.915
        val longitude = 116.404
        val location = LatLng(latitude, longitude)

        // 设置缩放等级
        val mapStatusUpdate = MapStatusUpdateFactory.newLatLngZoom(location, 18f)
        baiduMap.setMapStatus(mapStatusUpdate)

        // 加载自定义 marker icon
        val markerIcon = BitmapDescriptorFactory.fromResource(R.drawable.marker_icon) // 你的图标
        if (markerIcon != null) {
            val markerOptions = MarkerOptions()
                .position(location)
                .icon(markerIcon)

            baiduMap.addOverlay(markerOptions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
