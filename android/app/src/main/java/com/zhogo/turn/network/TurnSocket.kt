package com.zhogo.turn.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

object TurnSocket {
    private const val PREFS = "zhogo_turn_settings"
    private const val KEY_SERVER = "server_url"
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()

    @Volatile private var appContext: Context? = null
    @Volatile private var serverUrl: String = ""
    @Volatile private var socket: WebSocket? = null
    @Volatile private var manuallyClosed = false
    @Volatile private var reconnectDelay = 1500L

    @Volatile var currentState: String = "GREEN"
        private set

    var onState: ((String, String, Boolean, Long) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onPlayers: ((List<String>) -> Unit)? = null
    var onOverlayState: ((String, Boolean) -> Unit)? = null
    var onOverlayMessage: ((String) -> Unit)? = null

    @Volatile var roomCode: String? = "GLOBAL"
    @Volatile var isMyTurn: Boolean = true
    @Volatile var currentPlayerName: String = "Global"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_SERVER, "").orEmpty()
        if (serverUrl.isNotBlank()) connect()
    }

    fun saveServerUrl(context: Context, raw: String): Boolean {
        val value = raw.trim().trimEnd('/')
        val normalized = when {
            value.startsWith("https://") -> "wss://" + value.removePrefix("https://")
            value.startsWith("http://") -> "ws://" + value.removePrefix("http://")
            value.startsWith("wss://") || value.startsWith("ws://") -> value
            else -> return false
        }
        if (normalized.endsWith("/ws").not()) serverUrl = "$normalized/ws" else serverUrl = normalized
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SERVER, serverUrl).apply()
        disconnect()
        connect()
        return true
    }

    fun getServerUrl(): String = serverUrl

    private fun message(text: String) {
        main.post {
            onMessage?.invoke(text)
            onOverlayMessage?.invoke(text)
        }
    }

    fun connect() {
        if (serverUrl.isBlank()) {
            message("Enter your Render server URL first.")
            return
        }
        if (socket != null) return
        manuallyClosed = false
        val request = runCatching { Request.Builder().url(serverUrl).build() }.getOrElse {
            message("Invalid server URL: ${it.message}")
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 1500L
                message("Connected to server.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("type") == "STATE_UPDATE" || obj.has("state")) {
                        currentState = obj.optString("state", "GREEN")
                        val isGreen = (currentState == "GREEN")
                        main.post {
                            onState?.invoke("GLOBAL", currentState, isGreen, 0)
                            onPlayers?.invoke(listOf("Connected Users"))
                            onOverlayState?.invoke(if (isGreen) "STATUS: GREEN" else "STATUS: RED", isGreen)
                        }
                    }
                } catch (_: Exception) {
                    message("Could not read server response.")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
                message("Connection lost: ${t.message ?: "network error"}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                message("Disconnected.")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyClosed || serverUrl.isBlank()) return
        val delay = reconnectDelay
        reconnectDelay = min(reconnectDelay * 2, 30000L)
        main.postDelayed({ if (!manuallyClosed && socket == null) connect() }, delay)
    }

    fun createRoom(name: String) {
        connect()
        message("Connected to synchronized button.")
    }

    fun joinRoom(code: String, name: String) {
        connect()
        message("Connected to synchronized button.")
    }

    fun leaveRoom() {
        disconnect()
        message("Disconnected.")
    }

    fun press() {
        val current = socket
        val obj = JSONObject().put("type", "TOGGLE_BUTTON")
        if (current == null) {
            connect()
            message("Reconnecting...")
            return
        }
        current.send(obj.toString())
    }

    fun disconnect() {
        manuallyClosed = true
        socket?.close(1000, "User disconnected")
        socket = null
    }
}
