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
import java.util.concurrent.ConcurrentLinkedQueue

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
    @Volatile private var lastName = "ZHOGO"
    @Volatile private var lastRoomCode: String? = null
    private val pending = ConcurrentLinkedQueue<String>()

    @Volatile var playerId: String? = null
        private set
    @Volatile var roomCode: String? = null
        private set
    @Volatile var turnVersion: Long = 0
        private set
    @Volatile var isMyTurn: Boolean = false
        private set
    @Volatile var signalRed: Boolean = false
        private set
    @Volatile var signalVersion: Long = 0
        private set
    @Volatile var currentPlayerName: String = ""
        private set

    var onState: ((String, String, Boolean, Long) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onPlayers: ((List<String>) -> Unit)? = null
    var onOverlayState: ((String, Boolean) -> Unit)? = null
    var onSignal: ((Boolean) -> Unit)? = null
    var onOverlayMessage: ((String) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_SERVER, "").orEmpty()
        lastName = prefs.getString("player_name", "ZHOGO").orEmpty()
        lastRoomCode = prefs.getString("last_room", null)
        if (serverUrl.isNotBlank()) connect()
    }

    private fun saveRoomSession() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("player_name", lastName)
            ?.putString("last_room", lastRoomCode)
            ?.apply()
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
                var queued = false
                while (true) {
                    val item = pending.poll() ?: break
                    queued = true
                    if (!webSocket.send(item)) {
                        pending.add(item)
                        break
                    }
                }
                val code = lastRoomCode
                if (!queued && !code.isNullOrBlank()) {
                    webSocket.send(JSONObject().put("type", "JOIN_ROOM").put("roomCode", code).put("name", lastName).toString())
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    when (obj.optString("type")) {
                        "WELCOME" -> {
                            playerId = obj.optString("playerId")
                            roomCode = obj.optString("roomCode")
                            lastRoomCode = roomCode
                            saveRoomSession()
                        }
                        "ROOM_STATE" -> {
                            val room = obj.getJSONObject("room")
                            roomCode = room.getString("code")
                            signalVersion = room.optLong("signalVersion", 0L)
                            signalRed = room.optString("signal", "green") == "red"
                            val players = room.getJSONArray("players")
                            val names = mutableListOf<String>()
                            var currentName = ""
                            for (i in 0 until players.length()) {
                                val p = players.getJSONObject(i)
                                val label = p.optString("name") + if (p.optBoolean("online")) " (online)" else " (offline)"
                                names.add(label)
                                if (p.optString("id") == currentId) currentName = p.optString("name")
                            }
                            currentPlayerName = currentName
                            main.post {
                                onState?.invoke(roomCode.orEmpty(), if (signalRed) "الحالة حمراء" else "الحالة خضراء", signalRed, signalVersion)
                                onPlayers?.invoke(names)
                                onSignal?.invoke(signalRed)
                                onOverlayState?.invoke(if (signalRed) "الحالة: أحمر" else "الحالة: أخضر", !signalRed)
                            }
                        }
                        "ERROR" -> message(obj.optString("message", "Server error."))
                        "SIGNAL_CHANGED" -> {
                            signalVersion = obj.optLong("signalVersion", signalVersion)
                            signalRed = obj.optString("signal", "green") == "red"
                            main.post { onSignal?.invoke(signalRed); onOverlayState?.invoke(if (signalRed) "الحالة: أحمر" else "الحالة: أخضر", !signalRed) }
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
        lastName = name.trim().ifBlank { "ZHOGO" }
        lastRoomCode = null
        saveRoomSession()
        send(JSONObject().put("type", "CREATE_ROOM").put("name", lastName), queueIfConnecting = true)
    }

    fun joinRoom(code: String, name: String) {
        lastName = name.trim().ifBlank { "ZHOGO" }
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length != 6) {
            message("Enter the 6-character room code.")
            return
        }
        lastRoomCode = cleanCode
        saveRoomSession()
        send(JSONObject().put("type", "JOIN_ROOM").put("roomCode", cleanCode).put("name", lastName), queueIfConnecting = true)
    }

    fun leaveRoom() {
        lastRoomCode = null
        saveRoomSession()
        roomCode = null
        playerId = null
        isMyTurn = false
        signalRed = false
        signalVersion = 0
        socket?.close(1000, "Leave room")
        socket = null
        manuallyClosed = true
        main.post {
            onState?.invoke("", "", false, 0)
            onPlayers?.invoke(emptyList())
            onOverlayState?.invoke("NOT IN ROOM", false)
        }
    }

    fun press() {
        val code = roomCode
        if (code == null) return message("Join a room first.")
        send(JSONObject().put("type", "PRESS").put("roomCode", code).put("signalVersion", signalVersion), queueIfConnecting = false)
    }

    private fun send(obj: JSONObject, queueIfConnecting: Boolean) {
        val encoded = obj.toString()
        val current = socket
        if (current == null) {
            if (queueIfConnecting) pending.add(encoded)
            connect()
            message(if (queueIfConnecting) "Connecting; request queued." else "Not connected. Reconnect before pressing.")
            return
        }
        if (!current.send(encoded)) {
            if (queueIfConnecting) pending.add(encoded)
            message("Could not send message. Check your connection.")
        }
    }

    fun disconnect() {
        manuallyClosed = true
        pending.clear()
        socket?.close(1000, "User disconnected")
        socket = null
    }
}
