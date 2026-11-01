package com.zhogo.turn.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.zhogo.turn.network.TurnSocket

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private var button: TextView? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addFloatingButton()
        TurnSocket.onState = { _, currentName, mine, _ ->
            updateButton(if (mine) "YOUR TURN" else "WAIT: $currentName", mine)
        }
        TurnSocket.onMessage = { updateButton(it.take(18), false) }
    }

    private fun startForegroundNotification() {
        val channelId = "zhogo_turn_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ZHOGO TURN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("ZHOGO TURN is active")
            .setContentText("Floating turn control is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(7, notification)
    }

    private fun addFloatingButton() {
        val view = TextView(this).apply {
            text = "TURN"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(0, 145, 85))
                setStroke(3, Color.WHITE)
            }
        }
        val params = WindowManager.LayoutParams(
            dp(76), dp(76),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(18)
            y = dp(180)
        }
        button = view
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        val touchSlop = (8 * resources.displayMetrics.density)
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x = (startX - dx.toInt()).coerceAtLeast(0)
                        params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        v.performClick()
                    }
                    true
                }
                else -> true
            }
        }
        view.setOnClickListener { TurnSocket.press() }
        windowManager.addView(view, params)
    }

    private fun updateButton(label: String, enabled: Boolean) {
        runCatching {
            button?.text = label
            button?.alpha = if (enabled) 1f else 0.65f
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        TurnSocket.onState = null
        TurnSocket.onMessage = null
        button?.let { runCatching { windowManager.removeView(it) } }
        button = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
