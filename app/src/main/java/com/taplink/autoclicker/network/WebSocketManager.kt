package com.taplink.autoclicker.network

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(private val listener: RoomEventListener) {
    private var client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var webSocket: WebSocket? = null
    val timeSyncManager = TimeSyncManager { send(it) }

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                Handler(Looper.getMainLooper()).post { handleIncomingMessage(text) }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Handler(Looper.getMainLooper()).post { listener.onError("Connection error: \${t.localizedMessage}") }
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        val json = JSONObject(text)
        when (json.getString("type")) {
            "TIME_SYNC_RESPONSE" -> timeSyncManager.handleSyncResponse(json.getJSONObject("payload"))
            "ROOM_CREATED", "ROOM_JOINED" -> {
                timeSyncManager.requestSync()
                listener.onJoined(json.getJSONObject("payload").getString("roomCode"))
            }
            "ROOM_UPDATE" -> listener.onRoomStateUpdated(json.getJSONObject("payload"))
            "START_SCHEDULED" -> {
                val payload = json.getJSONObject("payload")
                val targetServerTime = payload.getLong("targetServerTime")
                val delayMs = targetServerTime - timeSyncManager.getEstimatedServerTime()
                listener.onStartScheduled(delayMs.coerceAtLeast(0))
            }
            "EMERGENCY_STOP" -> listener.onStop()
            "ERROR" -> listener.onError(json.getJSONObject("payload").getString("message"))
        }
    }

    fun send(data: String) { webSocket?.send(data) }
    fun disconnect() { webSocket?.close(1000, "Leaving") }

    interface RoomEventListener {
        fun onJoined(roomCode: String)
        fun onRoomStateUpdated(payload: JSONObject)
        fun onStartScheduled(delayMs: Long)
        fun onStop()
        fun onError(msg: String)
    }
}