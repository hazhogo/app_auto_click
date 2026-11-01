package com.zhogo.turn.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import com.zhogo.turn.network.TurnSocket

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: Button
    private lateinit var turnSocket: TurnSocket

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingButton = Button(this).apply {
            text = "TURN"
            setBackgroundColor(Color.GREEN)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(floatingButton, params)

        // الاتصال بالسيرفر ومزامنة الزر
        turnSocket = TurnSocket { state ->
            floatingButton.post {
                when (state) {
                    "RED" -> floatingButton.setBackgroundColor(Color.RED)
                    "GREEN" -> floatingButton.setBackgroundColor(Color.GREEN)
                }
            }
        }
        turnSocket.connect()

        floatingButton.setOnClickListener {
            turnSocket.sendClickAction()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::turnSocket.isInitialized) {
            turnSocket.disconnect()
        }
        if (::floatingButton.isInitialized) {
            windowManager.removeView(floatingButton)
        }
    }
}
