package com.zhogo.turn.network

import okhttp3.*
import org.json.JSONObject

class TurnSocket(private val onStateChanged: (String) -> Unit) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder()
            .url("wss://zhogo-turn-server.onrender.com")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "STATE_UPDATE") {
                        val state = json.getString("state")
                        onStateChanged(state)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }

    fun sendClickAction() {
        val json = JSONObject().apply {
            put("action", "TOGGLE_BUTTON")
        }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnected")
    }
}
