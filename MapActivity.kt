package com.example.bloothtomapapplication

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import android.widget.TextView

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap
    private lateinit var btnSelectNav: Button
    private lateinit var btnConfirmNav: Button

    private var isSelectingNav = false
    private var selectedLatLng: LatLng? = null

    private lateinit var textSentData: TextView  // 用于显示发送的数据

    private val handler = Handler(Looper.getMainLooper())
    private var sendRunnable: Runnable? = null
    private var currentTargetLat: Double? = null
    private var currentTargetLon: Double? = null

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("latitude", 0.0) ?: return
            val lon = intent.getDoubleExtra("longitude", 0.0)
            val point = LatLng(lat, lon)

            runOnUiThread {
                baiduMap.clear()

                // 加载原始图标
                val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.marker_icon)

                // 缩放图标：这里将图标缩小为 80x80 像素，修改为你需要的尺寸
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)

                // 创建 MarkerOptions，设置缩放后的图标
                val markerOptions = MarkerOptions()
                    .position(point)
                    .title("当前位置")
                    .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))

                // 添加 marker 到地图
                baiduMap.addOverlay(markerOptions)

                // 动画效果：地图移动到标记点并放大到适合的级别
                baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 18f))
            }
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
        btnSelectNav = findViewById(R.id.btn_select_nav)
        btnConfirmNav = findViewById(R.id.btn_confirm_nav)

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

        textSentData = findViewById(R.id.text_sent_data)

        btnConfirmNav.setOnClickListener {
            selectedLatLng?.let {
                currentTargetLat = it.latitude
                currentTargetLon = it.longitude

                startRepeatedSend()

                Toast.makeText(this, "开始持续发送目标点", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "未选择目标点", Toast.LENGTH_SHORT).show()

            isSelectingNav = false
            btnConfirmNav.visibility = Button.GONE
        }


        btnSelectNav.setOnClickListener {
            isSelectingNav = true
            btnConfirmNav.visibility = Button.VISIBLE
            Toast.makeText(this, "请点击地图选择导航点", Toast.LENGTH_SHORT).show()
        }

        baiduMap.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(p: LatLng?) {
                if (isSelectingNav && p != null) {
                    selectedLatLng = p
                    Toast.makeText(this@MapActivity, "选中: ${p.latitude}, ${p.longitude}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onMapPoiClick(poi: com.baidu.mapapi.map.MapPoi?) {}
        })
    }

    private fun sendTargetPointViaBle(lat: Double, lon: Double) {
        // 格式化保留小数点后10位
        val formattedLat = String.format("%.10f", lat)
        val formattedLon = String.format("%.10f", lon)

        // 拼接数据格式（比如 T:23.1275716667,114.3020566667）
        val dataToSend = "T:$formattedLat,$formattedLon"

        // 将字符串转为字节数组（ASCII）
        val byteArray = dataToSend.toByteArray(Charsets.US_ASCII)

        // 将字节数组转换为16进制表示
        val hexString = byteArray.joinToString(" ") { String.format("0x%02X", it) }

        runOnUiThread {
            textSentData.text = "发送内容: $hexString"  // 显示16进制数据
        }

        // 使用广播方式传递给 BleScanActivity 发蓝牙数据
        val intent = Intent("com.example.bloothtomapapplication.SEND_TARGET").apply {
            putExtra("target_data", byteArray)  // 发送字节数组（ASCII 16进制数据）
        }
        sendBroadcast(intent)
    }



    private fun startRepeatedSend() {
        stopRepeatedSend()  // 先清除旧任务
        sendRunnable = object : Runnable {
            override fun run() {
                val lat = currentTargetLat ?: return
                val lon = currentTargetLon ?: return
                sendTargetPointViaBle(lat, lon)
                handler.postDelayed(this, 500) // 每隔1秒发送一次
            }
        }
        handler.post(sendRunnable!!)
    }

    private fun stopRepeatedSend() {
        sendRunnable?.let {
            handler.removeCallbacks(it)
        }
        sendRunnable = null
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gpsReceiver)
        mapView.onDestroy()
        stopRepeatedSend()
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
