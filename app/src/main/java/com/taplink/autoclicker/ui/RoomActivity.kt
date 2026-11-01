package com.taplink.autoclicker.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.taplink.autoclicker.R
import com.taplink.autoclicker.network.WebSocketManager
import com.taplink.autoclicker.service.FloatingOverlayService
import com.taplink.autoclicker.service.TapAccessibilityService
import org.json.JSONObject

class RoomActivity : AppCompatActivity(), WebSocketManager.RoomEventListener {
    private lateinit var wsManager: WebSocketManager
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnAction: Button
    private lateinit var etRoomCode: EditText
    private var isHost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        tvStatus = findViewById(R.id.tvStatus)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnAction = findViewById(R.id.btnAction)
        etRoomCode = findViewById(R.id.etRoomCode)

        wsManager = WebSocketManager(this)
        wsManager.connect("ws://10.0.2.2:8080")

        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            val json = JSONObject().apply {
                put("type", "CREATE_ROOM")
                put("payload", JSONObject().put("name", "User_" + (10..99).random()))
            }
            wsManager.send(json.toString())
            isHost = true
        }

        findViewById<Button>(R.id.btnJoin).setOnClickListener {
            val code = etRoomCode.text.toString().trim()
            if (code.isNotEmpty()) {
                val json = JSONObject().apply {
                    put("type", "JOIN_ROOM")
                    put("payload", JSONObject().put("roomCode", code).put("name", "User_" + (10..99).random()))
                }
                wsManager.send(json.toString())
            }
        }

        btnAction.setOnClickListener {
            if (isHost) {
                wsManager.send(JSONObject().put("type", "TRIGGER_START").toString())
            } else {
                val json = JSONObject().apply {
                    put("type", "SET_READY")
                    put("payload", JSONObject().put("isReady", true))
                }
                wsManager.send(json.toString())
            }
        }
    }

    override fun onJoined(roomCode: String) {
        tvStatus.text = "متصل بالغرفة: $roomCode"
        startService(Intent(this, FloatingOverlayService::class.java))
        btnAction.text = if (isHost) "بدء النقرات للجميع (START)" else "جاهز (Ready)"
        btnAction.visibility = View.VISIBLE
    }

    override fun onRoomStateUpdated(payload: JSONObject) {
        val members = payload.getJSONArray("members")
        tvStatus.text = "الأعضاء المتصلون: ${members.length()}"
    }

    override fun onStartScheduled(delayMs: Long) {
        tvCountdown.visibility = View.VISIBLE
        object : CountDownTimer(delayMs, 50) {
            override fun onTick(ms: Long) { tvCountdown.text = String.format("%.1f", ms / 1000f) }
            override fun onFinish() {
                tvCountdown.text = "GO!"
                TapAccessibilityService.instance?.startClickingLoop(listOf(Pair(500f, 1000f)), 200, 50, 0)
            }
        }.start()
    }

    override fun onStop() {
        TapAccessibilityService.instance?.stopClicking()
        tvCountdown.visibility = View.GONE
    }

    override fun onError(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnect()
    }
}