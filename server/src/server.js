'use strict';

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = Number(process.env.PORT || 10000);
const rooms = new Map(); // roomCode -> { currentState: "GREEN", clients: Set<WebSocket> }

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ ok: true, activeRooms: rooms.size }));
  }
  res.writeHead(200, { 'content-type': 'text/plain' });
  res.end('ZHOGO TURN multi-room server is running');
});

const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 16 * 1024 });

function getOrCreateRoom(roomCode) {
  const code = (roomCode || "GLOBAL").trim().toUpperCase();
  if (!rooms.has(code)) {
    rooms.set(code, { currentState: "GREEN", clients: new Set() });
  }
  return rooms.get(code);
}

function broadcastRoomState(roomCode) {
  const room = rooms.get(roomCode);
  if (!room) return;
  const payload = JSON.stringify({ 
    type: 'STATE_UPDATE', 
    roomCode: roomCode, 
    state: room.currentState 
  });
  for (const client of room.clients) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  }
}

function leaveCurrentRoom(ws) {
  if (ws.currentRoomCode && rooms.has(ws.currentRoomCode)) {
    const room = rooms.get(ws.currentRoomCode);
    room.clients.delete(ws);
    if (room.clients.size === 0) {
      rooms.delete(ws.currentRoomCode);
    }
  }
  ws.currentRoomCode = null;
}

wss.on('connection', ws => {
  ws.isAlive = true;
  ws.currentRoomCode = null;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); }
    catch { return; }

    if (msg.type === 'JOIN_ROOM' || msg.type === 'CREATE_ROOM') {
      leaveCurrentRoom(ws);
      const roomCode = (msg.roomCode || msg.code || "GLOBAL").trim().toUpperCase();
      ws.currentRoomCode = roomCode;
      const room = getOrCreateRoom(roomCode);
      room.clients.add(ws);
      
      // إرسال حالة الغرفة فور الانضمام
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ 
          type: 'STATE_UPDATE', 
          roomCode: roomCode, 
          state: room.currentState 
        }));
      }
      return;
    }

    if (msg.type === 'TOGGLE_BUTTON' || msg.type === 'PRESS') {
      const roomCode = (msg.roomCode || ws.currentRoomCode || "GLOBAL").trim().toUpperCase();
      const room = getOrCreateRoom(roomCode);
      // تبديل حالة هذه الغرفة المحددة فقط
      room.currentState = (room.currentState === "GREEN") ? "RED" : "GREEN";
      broadcastRoomState(roomCode);
      return;
    }

    if (msg.type === 'LEAVE_ROOM') {
      leaveCurrentRoom(ws);
      return;
    }
  });

  ws.on('close', () => leaveCurrentRoom(ws));
  ws.on('error', () => leaveCurrentRoom(ws));
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) { ws.terminate(); continue; }
    ws.isAlive = false;
    ws.ping();
  }
}, 30000);
heartbeat.unref();

server.listen(PORT, '0.0.0.0', () => {
  console.log(`ZHOGO TURN server listening on ${PORT}`);
});
