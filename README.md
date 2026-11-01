# ZHOGO TURN

Android + Node.js/WebSocket starter for turn-taking between 2–10 friends.

## What this MVP does
- Create/join a room using a room code.
- Server-authoritative turn order (atomic in-memory updates).
- Floating overlay button using Android `WindowManager`.
- Tapping the floating button submits the current player's turn and advances to the next player.
- Reconnect and room-state sync.

The overlay only sends a turn event to this app's server. It does **not** tap or control other apps.

## Requirements
- Android Studio with JDK 17 and Android SDK (compile/target SDK 35).
- Node.js 20+ for local server testing.
- GitHub account for Actions builds; Render account for hosting.

## 1. Deploy server to Render
1. Push this repository to GitHub.
2. In Render, choose **New → Blueprint**, select the repository, and let it read `render.yaml`.
3. Render creates the Web Service. Copy its public URL, e.g. `https://zhogo-turn-server.onrender.com`.
4. The Android app currently uses a placeholder URL. Replace `https://YOUR-RENDER-SERVICE.onrender.com` in:
   `android/app/src/main/java/com/zhogo/turn/network/TurnSocket.kt`
   with your actual Render hostname (keep `wss://`).
5. Commit and push the change; GitHub Actions builds a debug APK.

For local server:
```bash
cd server
npm install
npm start
```
Server listens on `0.0.0.0:10000` by default. Health check: `/health`.

## 2. Build APK with GitHub Actions
- Push the repository to GitHub.
- Open **Actions → Build Android APK**.
- Wait for the workflow to finish.
- Download the `zhogo-turn-debug-apk` artifact.

The APK is a debug build for testing. For distribution, create a signed release build in Android Studio and protect your signing key.

## 3. Android setup
1. Install the APK.
2. Open the app and grant the "Display over other apps" permission when prompted.
3. Enter a display name.
4. Create a room or join with a room code.
5. Tap **Show floating button**.
6. The foreground notification keeps the overlay service visible. Tap the floating `TURN` button only when the app indicates it is your turn.

## Protocol
Client → server:
- `CREATE_ROOM {name}`
- `JOIN_ROOM {roomCode,name}`
- `PRESS {roomCode,playerId,turnVersion}`

Server → client:
- `ROOM_STATE {room}`
- `ERROR {message}`
- `TURN_ACCEPTED {turnVersion}`
- `PLAYER_JOINED {player}`
- `PLAYER_LEFT {playerId}`

## Important limitations
- Room state is in memory: a Render restart/redeploy clears active rooms. Add Redis/PostgreSQL persistence for production.
- This starter does not include accounts, invite links, push notifications, or end-to-end encryption.
- Use only in apps and situations where overlay use is permitted. Android may restrict overlays over secure/system screens.
