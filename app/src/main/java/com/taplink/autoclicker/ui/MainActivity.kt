package com.taplink.autoclicker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.taplink.autoclicker.R
import com.taplink.autoclicker.service.FloatingOverlayService
import com.taplink.autoclicker.service.TapAccessibilityService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnLocalMode).setOnClickListener {
            if (checkPermissions()) {
                startService(Intent(this, FloatingOverlayService::class.java))
                moveTaskToBack(true)
            }
        }

        findViewById<Button>(R.id.btnOnlineRoom).setOnClickListener {
            if (checkPermissions()) {
                startActivity(Intent(this, RoomActivity::class.java))
            }
        }
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "يرجى منح إذن الظهور فوق التطبيقات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        if (TapAccessibilityService.instance == null) {
            Toast.makeText(this, "يرجى تفعيل خدمة TapLink من إمكانية الوصول (Accessibility)", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return false
        }
        return true
    }
}