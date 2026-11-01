'use strict';
const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const crypto = require('crypto');
const PORT = Number(process.env.PORT || 10000);
const MAX_PLAYERS = 10;
const rooms = new Map();
const clients = new Map();
function send(ws, payload) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(payload)); }
function roomSnapshot(room) {
  return { code: room.code, signal: room.signal, signalVersion: room.signalVersion,
    players: room.players.map(p => ({ id: p.id, name: p.name, online: p.online, order: p.order })) };
}
function broadcast(room, payload) { for (const p of room.players) if (p.ws) send(p.ws, payload); }
function broadcastState(room) { broadcast(room, { type: 'ROOM_STATE', room: roomSnapshot(room) }); }
function cleanName(value) { return String(value || '').trim().replace(/\s+/g, ' ').slice(0, 20); }
function makeCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; let code;
  do { code = Array.from({ length: 6 }, () => alphabet[crypto.randomInt(alphabet.length)]).join(''); } while (rooms.has(code));
  return code;
}
function detach(ws) {
  const info = clients.get(ws); if (!info) return;
  clients.delete(ws); const room = rooms.get(info.roomCode); if (!room) return;
  const index = room.players.findIndex(p => p.id === info.playerId); if (index < 0) return;
  const [player] = room.players.splice(index, 1);
  if (!room.players.length) return rooms.delete(room.code);
  broadcast(room, { type: 'PLAYER_LEFT', playerId: player.id }); broadcastState(room);
}
const server = http.createServer((req, res) => {
  if (req.url === '/health') { res.writeHead(200, { 'content-type': 'application/json' }); return res.end(JSON.stringify({ ok: true, rooms: rooms.size })); }
  res.writeHead(200, { 'content-type': 'text/plain' }); res.end('ZHOGO TURN server is running');
});
const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 16 * 1024 });
wss.on('connection', ws => {
  ws.isAlive = true; ws.on('pong', () => { ws.isAlive = true; });
  ws.on('message', raw => {
    let msg; try { msg = JSON.parse(raw.toString()); } catch { return send(ws, { type: 'ERROR', message: 'Invalid JSON.' }); }
    if (!msg || typeof msg.type !== 'string') return send(ws, { type: 'ERROR', message: 'Invalid message.' });
    if (msg.type === 'CREATE_ROOM') {
      if (clients.has(ws)) detach(ws); const name = cleanName(msg.name);
      if (!name) return send(ws, { type: 'ERROR', message: 'Enter a display name.' });
      const code = makeCode(); const player = { id: crypto.randomUUID(), name, online: true, order: 0, ws };
      const room = { code, players: [player], signal: 'green', signalVersion: 0 }; rooms.set(code, room); clients.set(ws, { roomCode: code, playerId: player.id });
      send(ws, { type: 'WELCOME', playerId: player.id, roomCode: code }); broadcastState(room); return;
    }
    if (msg.type === 'JOIN_ROOM') {
      const code = String(msg.roomCode || '').trim().toUpperCase(); const name = cleanName(msg.name); const room = rooms.get(code);
      if (!name) return send(ws, { type: 'ERROR', message: 'Enter a display name.' });
      if (!room) return send(ws, { type: 'ERROR', message: 'Room not found.' });
      if (room.players.length >= MAX_PLAYERS) return send(ws, { type: 'ERROR', message: 'Room is full (10 players maximum).' });
      if (clients.has(ws)) return send(ws, { type: 'ERROR', message: 'Leave your current room before joining another.' });
      const player = { id: crypto.randomUUID(), name, online: true, order: room.players.length, ws }; room.players.push(player); clients.set(ws, { roomCode: code, playerId: player.id });
      send(ws, { type: 'WELCOME', playerId: player.id, roomCode: code }); broadcast(room, { type: 'PLAYER_JOINED', player: { id: player.id, name, online: true, order: player.order } }); broadcastState(room); return;
    }
    if (msg.type === 'PRESS') {
      const info = clients.get(ws); if (!info) return send(ws, { type: 'ERROR', message: 'Join a room first.' });
      const room = rooms.get(info.roomCode); if (!room) return send(ws, { type: 'ERROR', message: 'Room no longer exists.' });
      const player = room.players.find(p => p.id === info.playerId); if (!player || !player.online) return send(ws, { type: 'ERROR', message: 'Player is offline.' });
      if (msg.roomCode !== room.code || Number(msg.signalVersion) !== room.signalVersion) { send(ws, { type: 'ERROR', message: 'The room state changed. Syncing room.' }); return broadcastState(room); }
      room.signal = room.signal === 'red' ? 'green' : 'red'; room.signalVersion += 1;
      broadcast(room, { type: 'SIGNAL_CHANGED', signal: room.signal, signalVersion: room.signalVersion, byPlayerId: player.id }); broadcastState(room); return;
    }
    send(ws, { type: 'ERROR', message: 'Unknown event type.' });
  });
  ws.on('close', () => detach(ws)); ws.on('error', () => detach(ws));
});
const heartbeat = setInterval(() => { for (const ws of wss.clients) { if (ws.isAlive === false) { ws.terminate(); continue; } ws.isAlive = false; ws.ping(); } }, 30000); heartbeat.unref();
server.listen(PORT, '0.0.0.0', () => console.log(`ZHOGO TURN listening on ${PORT}`));
