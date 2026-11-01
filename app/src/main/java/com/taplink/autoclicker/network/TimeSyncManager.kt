package com.taplink.autoclicker.network

import org.json.JSONObject

class TimeSyncManager(private val sendFunction: (String) -> Unit) {
    var clockOffsetMs: Long = 0
        private set

    fun requestSync() {
        val t0 = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("type", "TIME_SYNC_REQUEST")
            put("payload", JSONObject().put("clientSendTime", t0))
        }
        sendFunction(json.toString())
    }

    fun handleSyncResponse(payload: JSONObject) {
        val t3 = System.currentTimeMillis()
        val t0 = payload.getLong("clientSendTime")
        val serverTime = payload.getLong("serverTime")
        val rtt = t3 - t0
        val estimatedServerTimeNow = serverTime + (rtt / 2)
        clockOffsetMs = estimatedServerTimeNow - t3
    }

    fun getEstimatedServerTime(): Long = System.currentTimeMillis() + clockOffsetMs
}