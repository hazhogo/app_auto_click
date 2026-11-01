
package com.zhogo.turn.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.zhogo.turn.network.TurnSocket

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var button: TextView? = null
    private var banner: TextView? = null

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()

        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        addStatusBanner()
        addFloatingButton()

        TurnSocket.onOverlayState = { label, green ->
            updateTurnUI(label, green)
        }
        TurnSocket.onSignal = { red ->
            updateTurnUI(if (red) "الحالة: أحمر" else "الحالة: أخضر", !red)
        }

        TurnSocket.onOverlayMessage = {
            updateBanner(it.take(35), false)
        }

        TurnSocket.connect()

        if (TurnSocket.roomCode != null) {
            updateTurnUI(
                if (TurnSocket.signalRed) "الحالة: أحمر" else "الحالة: أخضر",
                !TurnSocket.signalRed
            )
        } else {
            updateTurnUI("NOT IN ROOM", false)
        }
    }

    private fun startForegroundNotification() {
        val channelId = "zhogo_turn_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ZHOGO TURN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("ZHOGO TURN is active")
            .setContentText("Floating turn control is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(7, notification)
    }

    private fun addStatusBanner() {
        val view = TextView(this).apply {
            text = "WAITING FOR TURN"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(25, 45, 35))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(0, 255, 130))
            }
            elevation = dp(8).toFloat()
        }

        val params = WindowManager.LayoutParams(
            dp(280),
            dp(50),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(35)
        }

        banner = view
        windowManager.addView(view, params)
    }

    private fun addFloatingButton() {
        val view = TextView(this).apply {
            text = "TURN"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = buttonBackground(true)
        }

        val params = WindowManager.LayoutParams(
            dp(76),
            dp(76),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
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
        val touchSlop = 8 * resources.displayMetrics.density

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

                    if (kotlin.math.abs(dx) > touchSlop ||
                        kotlin.math.abs(dy) > touchSlop
                    ) {
                        dragged = true
                    }

                    if (dragged) {
                        params.x = (startX - dx.toInt()).coerceAtLeast(0)
                        params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching {
                            windowManager.updateViewLayout(v, params)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) v.performClick()
                    true
                }

                else -> true
            }
        }

        view.setOnClickListener {
            TurnSocket.press()
        }

        windowManager.addView(view, params)
    }

    private fun buttonBackground(enabled: Boolean) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (enabled) Color.rgb(0, 190, 95)
                else Color.rgb(75, 75, 75)
            )
            setStroke(dp(3), Color.WHITE)
        }

    private fun updateTurnUI(label: String, green: Boolean) {
        button?.apply {
            text = "TURN"
            alpha = 1f
            background = buttonBackground(!TurnSocket.signalRed)
        }
    
        updateBanner(label, green)
    }
    
    private fun updateBanner(text: String, green: Boolean) {
        banner?.apply {
            this.text = text
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(
                    if (green) Color.rgb(0, 145, 65)
                    else Color.rgb(45, 45, 45)
                )
                setStroke(
                    dp(1),
                    if (green) Color.rgb(100, 255, 150)
                    else Color.GRAY
                )
            }
        }
    }

    override fun onDestroy() {
        TurnSocket.onOverlayState = null
        TurnSocket.onOverlayMessage = null
        TurnSocket.onSignal = null

        button?.let {
            runCatching { windowManager.removeView(it) }
        }

        banner?.let {
            runCatching { windowManager.removeView(it) }
        }

        button = null
        banner = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
