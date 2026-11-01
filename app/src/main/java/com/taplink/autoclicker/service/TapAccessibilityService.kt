package com.taplink.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class TapAccessibilityService : AccessibilityService() {
    companion object {
        var instance: TapAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isClicking = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopClicking() }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    fun performSingleClick(x: Float, y: Float, durationMs: Long = 50) {
        val clickPath = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun startClickingLoop(points: List<Pair<Float, Float>>, intervalMs: Long, durationMs: Long, repeatCount: Int) {
        if (isClicking || points.isEmpty()) return
        isClicking = true
        serviceScope.launch {
            var cycles = 0
            while (isClicking && (repeatCount == 0 || cycles < repeatCount)) {
                for (point in points) {
                    if (!isClicking) break
                    performSingleClick(point.first, point.second, durationMs)
                    delay(intervalMs)
                }
                cycles++
            }
            isClicking = false
        }
    }

    fun stopClicking() { isClicking = false }
    fun isRunning(): Boolean = isClicking
}