package com.zhogo.turn.network

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TurnSocket {
    // Replace this with your Render service URL. Use wss://, not https://.
    private const val SERVER_URL = "wss://YOUR-RENDER-SERVICE.onrender.com/ws"

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<String>()
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

    fun connect() {
        if (SERVER_URL.contains("YOUR-RENDER-SERVICE")) {
            onMessage?.invoke("Set your Render URL in TurnSocket.kt first.")
            return
        }
        if (socket != null) return
        val request = Request.Builder().url(SERVER_URL).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onMessage?.invoke("Connected to server.")
                while (true) {
                    val queued = pending.poll() ?: break
                    if (!webSocket.send(queued)) {
                        pending.add(queued)
                        break
                    }
                }
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
                            onState?.invoke(roomCode.orEmpty(), currentName, isMyTurn, turnVersion)
                        }
                        "ERROR" -> onMessage?.invoke(obj.optString("message"))
                        "TURN_ACCEPTED" -> onMessage?.invoke("Turn passed.")
                    }
                } catch (_: Exception) {
                    onMessage?.invoke("Could not read server response.")
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                onMessage?.invoke("Connection lost: ${t.message ?: "network error"}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                onMessage?.invoke("Disconnected.")
            }
        })
    }

    fun createRoom(name: String) = send(JSONObject().put("type", "CREATE_ROOM").put("name", name))
    fun joinRoom(code: String, name: String) = send(
        JSONObject().put("type", "JOIN_ROOM").put("roomCode", code).put("name", name)
    )
    fun press() {
        val code = roomCode ?: return onMessage?.invoke("Join a room first.")
        if (!isMyTurn) return onMessage?.invoke("It is not your turn.")
        send(JSONObject().put("type", "PRESS").put("roomCode", code).put("turnVersion", turnVersion))
    }
    private fun send(obj: JSONObject) {
        val current = socket
        val encoded = obj.toString()
        if (current == null) {
            pending.add(encoded)
            connect()
            onMessage?.invoke("Connecting…")
        } else if (!current.send(encoded)) {
            pending.add(encoded)
            onMessage?.invoke("Message queued; reconnecting may be required.")
        }
    }
    fun disconnect() {
        socket?.close(1000, "User disconnected")
        socket = null
    }
}
