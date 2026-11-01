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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhogo.turn.network.TurnSocket
import com.zhogo.turn.overlay.FloatingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TurnSocket.initialize(this)
        setContent { ZHOGOTurnScreen() }
    }
}

@Composable
fun ZHOGOTurnScreen() {
    val context = LocalContext.current
    var server by remember { mutableStateOf(TurnSocket.getServerUrl().removeSuffix("/ws")) }
    var name by remember { mutableStateOf("ZHOGO") }
    var code by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter your Render URL, then connect.") }
    var current by remember { mutableStateOf("Waiting for room") }
    var myTurn by remember { mutableStateOf(false) }
    var players by remember { mutableStateOf(emptyList<String>()) }

    DisposableEffect(Unit) {
        TurnSocket.onMessage = { status = it }
        TurnSocket.onState = { roomCode, currentName, mine, _ ->
            room = roomCode
            current = currentName.ifBlank { "Waiting" }
            myTurn = mine
            status = if (mine) "It is your turn." else "Waiting for $current"
        }
        TurnSocket.onPlayers = { players = it }
        onDispose {
            TurnSocket.onMessage = null
            TurnSocket.onState = null
            TurnSocket.onPlayers = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF12D98B),
        background = androidx.compose.ui.graphics.Color(0xFF090D0B),
        surface = androidx.compose.ui.graphics.Color(0xFF111A16)
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("ZHOGO TURN", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Take turns with friends using a floating control.")
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text("Render server URL (https://...onrender.com)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Button(onClick = {
                    if (TurnSocket.saveServerUrl(context, server)) status = "Connecting to server…"
                    else status = "URL must start with https:// or wss://"
                }, modifier = Modifier.fillMaxWidth()) { Text("SAVE SERVER & CONNECT") }

                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text("Your display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Button(onClick = { TurnSocket.createRoom(name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("CREATE ROOM")
                }
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) },
                    label = { Text("6-character room code") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
                OutlinedButton(onClick = { TurnSocket.joinRoom(code, name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("JOIN ROOM")
                }

                HorizontalDivider()
                Text("ROOM: ${room.ifBlank { "—" }}", color = MaterialTheme.colorScheme.primary)
                Text("CURRENT TURN: $current")
                Text(status, color = if (myTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (players.isNotEmpty()) {
                    Text("PLAYERS (${players.size})", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(players) { player -> Text("• $player", modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        TurnSocket.leaveRoom()
                        room = ""
                        current = "Waiting for room"
                        myTurn = false
                    }, modifier = Modifier.weight(1f)) { Text("LEAVE ROOM") }
                    OutlinedButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join my ZHOGO TURN room: $room")
                        }
                        context.startActivity(Intent.createChooser(share, "Share room code"))
                    }, enabled = room.isNotBlank(), modifier = Modifier.weight(1f)) { Text("SHARE ROOM") }
                }
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    } else {
                        ContextCompat.startForegroundService(context, Intent(context, FloatingService::class.java))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("SHOW FLOATING BUTTON") }
                OutlinedButton(onClick = { context.stopService(Intent(context, FloatingService::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("HIDE FLOATING BUTTON")
                }
                Text("The floating button sends a turn signal to your friends. It does not control or tap inside other apps.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
