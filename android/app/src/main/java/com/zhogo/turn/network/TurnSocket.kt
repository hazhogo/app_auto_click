package com.zhogo.turn.network

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper

object TurnSocket {
    // Replace this with your Render service URL. Use wss://, not https://.
    private const val SERVER_URL = "wss://YOUR-RENDER-SERVICE.onrender.com/ws"

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile var playerId: String? = null
        private set
    @Volatile var roomCode: String? = null
        private set
    @Volatile var turnVersion: Long = 0
        private set
    @Volatile var isMyTurn: Boolean = false
        private set
    @Volatile var currentPlayerName: String = ""
        private set

    var onState: ((String, String, Boolean, Long) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun notifyMessage(message: String) {
        mainHandler.post { onMessage?.invoke(message) }
    }

    private fun notifyState(code: String, name: String, mine: Boolean, version: Long) {
        mainHandler.post { onState?.invoke(code, name, mine, version) }
    }

    fun connect() {
        if (SERVER_URL.contains("YOUR-RENDER-SERVICE")) {
            notifyMessage("Set your Render URL in TurnSocket.kt first.")
            return
        }
        if (socket != null) return
        val request = Request.Builder().url(SERVER_URL).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                notifyMessage("Connected to server.")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    when (obj.optString("type")) {
                        "WELCOME" -> {
                            playerId = obj.optString("playerId")
                            roomCode = obj.optString("roomCode")
                        }
                        "ROOM_STATE" -> {
                            val room = obj.getJSONObject("room")
                            roomCode = room.getString("code")
                            turnVersion = room.getLong("turnVersion")
                            val players = room.getJSONArray("players")
                            var currentName = ""
                            isMyTurn = room.optString("currentPlayerId") == playerId
                            for (i in 0 until players.length()) {
                                val p = players.getJSONObject(i)
                                if (p.optString("id") == room.optString("currentPlayerId")) {
                                    currentName = p.optString("name")
                                }
                            }
                            currentPlayerName = currentName
                            notifyState(roomCode.orEmpty(), currentName, isMyTurn, turnVersion)
                        }
                        "ERROR" -> notifyMessage(obj.optString("message"))
                        "TURN_ACCEPTED" -> notifyMessage("Turn passed.")
                    }
                } catch (_: Exception) {
                    notifyMessage("Could not read server response.")
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                notifyMessage("Connection lost: ${t.message ?: "network error"}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                notifyMessage("Disconnected.")
            }
        })
    }

    fun createRoom(name: String): Unit {
        send(JSONObject().put("type", "CREATE_ROOM").put("name", name))
    }

    fun joinRoom(code: String, name: String): Unit {
        send(JSONObject().put("type", "JOIN_ROOM").put("roomCode", code).put("name", name))
    }

    fun press(): Unit {
        val code = roomCode
        if (code == null) {
            notifyMessage("Join a room first.")
            return
        }
        if (!isMyTurn) {
            notifyMessage("It is not your turn.")
            return
        }
        send(JSONObject().put("type", "PRESS").put("roomCode", code).put("turnVersion", turnVersion))
    }

    private fun send(obj: JSONObject): Unit {
        val current = socket
        if (current == null) {
            notifyMessage("Not connected. Wait for connection, then try again.")
            connect()
            return
        }
        if (!current.send(obj.toString())) {
            notifyMessage("Message could not be sent. Check your connection.")
        }
    }
    fun disconnect() {
        socket?.close(1000, "User disconnected")
        socket = null
        playerId = null
        roomCode = null
        isMyTurn = false
        turnVersion = 0
    }
}
