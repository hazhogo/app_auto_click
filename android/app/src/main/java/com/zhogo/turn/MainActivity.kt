package com.zhogo.turn

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhogo.turn.network.TurnSocket
import com.zhogo.turn.overlay.FloatingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); TurnSocket.initialize(this); setContent { ZHOGOTurnScreen() } }
}

@Composable
fun ZHOGOTurnScreen() {
    val context = LocalContext.current
    var server by remember { mutableStateOf(TurnSocket.getServerUrl().removeSuffix("/ws").ifBlank { "https://zhogo-turn-server.onrender.com" }) }
    var name by remember { mutableStateOf("ZHOGO") }; var code by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }; var status by remember { mutableStateOf("أنشئ غرفة أو انضم إلى غرفة للبدء") }
    var signalRed by remember { mutableStateOf(false) }; var players by remember { mutableStateOf(emptyList<String>()) }
    DisposableEffect(Unit) {
        TurnSocket.onMessage = { status = it }
        TurnSocket.onState = { roomCode, _, red, _ -> room = roomCode; signalRed = red; status = if (red) "الحالة الحالية: أحمر" else "الحالة الحالية: أخضر" }
        TurnSocket.onSignal = { signalRed = it; status = if (it) "تم تحويل الحالة إلى الأحمر لدى الجميع" else "تم تحويل الحالة إلى الأخضر لدى الجميع" }
        TurnSocket.onPlayers = { players = it }
        onDispose { TurnSocket.onMessage = null; TurnSocket.onState = null; TurnSocket.onSignal = null; TurnSocket.onPlayers = null }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF55E6A5), background = Color(0xFF08110D), surface = Color(0xFF122019))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ZHOGO", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("تحكم جماعي سريع وواضح للحالة بين أفراد الغرفة", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(server, { server = it }, label = { Text("رابط الخادم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { status = if (TurnSocket.saveServerUrl(context, server)) "جارٍ الاتصال بالخادم…" else "استخدم رابطًا يبدأ بـ https:// أو wss://" }, Modifier.fillMaxWidth()) { Text("حفظ والاتصال") }
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("اسمك داخل الغرفة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ TurnSocket.createRoom(name) }, Modifier.weight(1f)) { Text("إنشاء غرفة") }
                    OutlinedButton({ TurnSocket.joinRoom(code, name) }, Modifier.weight(1f), enabled = code.length == 6) { Text("انضمام") }
                }
                OutlinedTextField(code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, label = { Text("رمز الغرفة (6 أحرف)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                HorizontalDivider()
                Text("الغرفة: ${room.ifBlank { "—" }}", color = MaterialTheme.colorScheme.primary)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (signalRed) Color(0xFF651D25) else Color(0xFF145C3C))) {
                    Column(Modifier.padding(16.dp)) { Text(if (signalRed) "الحالة: أحمر" else "الحالة: أخضر", style = MaterialTheme.typography.titleLarge); Text(status) }
                }
                if (players.isNotEmpty()) { Text("المشاركون (${players.size})", style = MaterialTheme.typography.titleMedium); LazyColumn(Modifier.heightIn(max = 120.dp)) { items(players) { Text("• $it", Modifier.padding(vertical = 2.dp)) } } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ TurnSocket.leaveRoom(); room = ""; players = emptyList(); signalRed = false }, Modifier.weight(1f), enabled = room.isNotBlank()) { Text("مغادرة") }
                    OutlinedButton({ val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "انضم إلى غرفتي في ZHOGO: $room") }; context.startActivity(Intent.createChooser(share, "مشاركة رمز الغرفة")) }, Modifier.weight(1f), enabled = room.isNotBlank()) { Text("مشاركة") }
                }
                Button({ if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) else ContextCompat.startForegroundService(context, Intent(context, FloatingService::class.java)) }, Modifier.fillMaxWidth(), enabled = room.isNotBlank()) { Text("إظهار زر التحكم العائم") }
                OutlinedButton({ context.stopService(Intent(context, FloatingService::class.java)) }, Modifier.fillMaxWidth()) { Text("إخفاء زر التحكم") }
                Text("اضغط الزر العائم لتبديل الحالة عند الجميع: أحمر ثم أخضر. لا يتحكم الزر في التطبيقات الأخرى.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
