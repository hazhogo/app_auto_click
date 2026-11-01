package com.taplink.autoclicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var controlMenuView: View? = null
    private val clickTargetViews = mutableListOf<Pair<View, WindowManager.LayoutParams>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        createControlMenu()
    }

    private fun startForegroundNotification() {
        val channelId = "taplink_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TapLink Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TapLink Active")
            .setContentText("لوحة التحكم العائمة نشطة")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1001, notification)
    }

    private fun createControlMenu() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = 20; y = 0
        }

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6121824"))
            setPadding(16, 20, 16, 20)
        }

        menuLayout.addView(createButton("▶", Color.parseColor("#00E5FF")) {
            val service = TapAccessibilityService.instance
            if (service != null && !service.isRunning()) {
                val coords = clickTargetViews.map { Pair(it.second.x.toFloat() + 40f, it.second.y.toFloat() + 40f) }
                service.startClickingLoop(coords, 200, 50, 0)
            } else {
                service?.stopClicking()
            }
        })

        menuLayout.addView(createButton("➕", Color.parseColor("#00FF66")) { addClickTarget() })
        menuLayout.addView(createButton("🔄", Color.WHITE) {
            clickTargetViews.forEach { windowManager.removeView(it.first) }
            clickTargetViews.clear()
        })
        menuLayout.addView(createButton("✖", Color.parseColor("#FF3366")) { stopSelf() })

        menuLayout.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(menuLayout, params)
                        return true
                    }
                }
                return false
            }
        })
        controlMenuView = menuLayout
        windowManager.addView(menuLayout, params)
    }

    private fun addClickTarget() {
        val targetIndex = clickTargetViews.size + 1
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val targetParams = WindowManager.LayoutParams(
            120, 120, layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300; y = 300 + (targetIndex * 50)
        }

        val targetView = TextView(this).apply {
            text = "$targetIndex"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#AA00E5FF"))
        }

        targetView.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0; var initialY = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = targetParams.x; initialY = targetParams.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        targetParams.x = initialX + (event.rawX - touchX).toInt()
                        targetParams.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(targetView, targetParams)
                        return true
                    }
                }
                return false
            }
        })

        clickTargetViews.add(Pair(targetView, targetParams))
        windowManager.addView(targetView, targetParams)
    }

    private fun createButton(label: String, colorHex: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(colorHex)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controlMenuView?.let { windowManager.removeView(it) }
        clickTargetViews.forEach { windowManager.removeView(it.first) }
        clickTargetViews.clear()
        TapAccessibilityService.instance?.stopClicking()
    }
}