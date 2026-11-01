package com.zhogo.turn

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhogo.turn.network.TurnSocket
import com.zhogo.turn.overlay.FloatingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TurnSocket.connect()
        setContent { ZHOGOTurnScreen() }
    }
}

@Composable
fun ZHOGOTurnScreen() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("ZHOGO") }
    var code by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connect to the server to begin.") }
    var current by remember { mutableStateOf("Waiting for room") }
    var myTurn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        TurnSocket.onMessage = { status = it }
        TurnSocket.onState = { roomCode, currentName, mine, _ ->
            room = roomCode
            current = currentName.ifBlank { "Waiting" }
            myTurn = mine
            status = if (mine) "It is your turn." else "Waiting for $current"
        }
        onDispose {
            TurnSocket.onMessage = null
            TurnSocket.onState = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF12D98B),
        background = androidx.compose.ui.graphics.Color(0xFF090D0B),
        surface = androidx.compose.ui.graphics.Color(0xFF111A16)
    )) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("ZHOGO TURN", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Multiplayer floating turn control")
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text("Your display name") }, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { TurnSocket.connect(); TurnSocket.createRoom(name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("CREATE ROOM")
                }
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().take(6) },
                    label = { Text("Room code") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
                OutlinedButton(onClick = { TurnSocket.connect(); TurnSocket.joinRoom(code, name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("JOIN ROOM")
                }
                HorizontalDivider()
                Text("ROOM: ${room.ifBlank { "—" }}")
                Text("CURRENT TURN: $current")
                Text(status, color = MaterialTheme.colorScheme.primary)
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } else {
                            ContextCompat.startForegroundService(context, Intent(context, FloatingService::class.java))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SHOW FLOATING BUTTON") }
                OutlinedButton(
                    onClick = { context.stopService(Intent(context, FloatingService::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("HIDE FLOATING BUTTON") }
                if (room.isNotBlank()) {
                    Text(if (myTurn) "You are next to press." else "Wait for your turn.")
                }
                Spacer(Modifier.weight(1f))
                Text("The overlay sends a turn signal only; it does not click inside other apps.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
