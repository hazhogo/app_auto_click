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

    @Volatile var currentRoomCode: String = ""
        private set

    var onState: ((String, String, Boolean, Long) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onPlayers: ((List<String>) -> Unit)? = null
    var onOverlayState: ((String, Boolean) -> Unit)? = null
    var onOverlayMessage: ((String) -> Unit)? = null

    @Volatile var roomCode: String? = null
    @Volatile var isMyTurn: Boolean = true
    @Volatile var currentPlayerName: String = "ZHOGO"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_SERVER, "").orEmpty()
        currentRoomCode = prefs.getString("last_room_code", "").orEmpty()
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
            message("أدخل رابط سيرفر Render أولاً.")
            return
        }
        if (socket != null) return
        manuallyClosed = false
        val request = runCatching { Request.Builder().url(serverUrl).build() }.getOrElse {
            message("رابط السيرفر غير صالح: ${it.message}")
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 1500L
                message("تم الاتصال بالسيرفر.")
                if (currentRoomCode.isNotBlank()) {
                    joinRoom(currentRoomCode, currentPlayerName)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("type") == "STATE_UPDATE" || obj.has("state")) {
                        currentState = obj.optString("state", "GREEN")
                        val rCode = obj.optString("roomCode", currentRoomCode)
                        if (rCode.isNotBlank()) currentRoomCode = rCode
                        roomCode = currentRoomCode
                        val isGreen = (currentState == "GREEN")
                        main.post {
                            onState?.invoke(currentRoomCode, currentState, isGreen, 0)
                            onPlayers?.invoke(listOf("غرفة: $currentRoomCode"))
                            onOverlayState?.invoke("ROOM: $currentRoomCode | ${if (isGreen) "GREEN" else "RED"}", isGreen)
                        }
                    }
                } catch (_: Exception) {
                    message("تعذر قراءة استجابة السيرفر.")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
                message("انقطع الاتصال: ${t.message ?: "خطأ في الشبكة"}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                message("تم قطع الاتصال.")
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

    fun generateRandomRoomCode(): String {
        val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun createRoom(name: String) {
        val code = generateRandomRoomCode()
        joinRoom(code, name)
    }

    fun joinRoom(code: String, name: String) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            message("أدخل رمز الغرفة أولاً.")
            return
        }
        currentRoomCode = cleanCode
        roomCode = cleanCode
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString("last_room_code", cleanCode)?.apply()

        val obj = JSONObject().put("type", "JOIN_ROOM").put("roomCode", cleanCode).put("name", name)
        sendPayload(obj.toString())
        message("تم الانضمام للغرفة: $cleanCode")
    }

    fun leaveRoom() {
        val obj = JSONObject().put("type", "LEAVE_ROOM")
        sendPayload(obj.toString())
        currentRoomCode = ""
        roomCode = null
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.remove("last_room_code")?.apply()
        message("تم مغادرة الغرفة.")
        main.post {
            onState?.invoke("", "GREEN", true, 0)
            onPlayers?.invoke(emptyList())
            onOverlayState?.invoke("NOT IN ROOM", false)
        }
    }

    fun press() {
        if (currentRoomCode.isBlank()) {
            message("قم بالانضمام أو إنشاء غرفة أولاً!")
            return
        }
        val obj = JSONObject().put("type", "TOGGLE_BUTTON").put("roomCode", currentRoomCode)
        sendPayload(obj.toString())
    }

    private fun sendPayload(encoded: String) {
        val current = socket
        if (current == null) {
            connect()
            message("إعادة اتصال بالسيرفر...")
            return
        }
        current.send(encoded)
    }

    fun disconnect() {
        manuallyClosed = true
        socket?.close(1000, "User disconnected")
        socket = null
    }
}
