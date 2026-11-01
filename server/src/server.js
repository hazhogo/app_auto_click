'use strict';

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = Number(process.env.PORT || 10000);
let currentState = "GREEN"; // الحالة الافتراضية للزر

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ ok: true, state: currentState }));
  }
  res.writeHead(200, { 'content-type': 'text/plain' });
  res.end('ZHOGO TURN server is running');
});

const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 16 * 1024 });

function broadcastState() {
  const payload = JSON.stringify({ type: 'STATE_UPDATE', state: currentState });
  for (const client of wss.clients) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  }
}

wss.on('connection', ws => {
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  // إرسال الحالة الحالية فور اتصال أي مستخدم جديد
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'STATE_UPDATE', state: currentState }));
  }

  ws.on('message', raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); }
    catch { return; }

    if (msg.type === 'TOGGLE_BUTTON' || msg.type === 'PRESS') {
      // تبديل الحالة بين GREEN و RED لدى الجميع
      currentState = (currentState === "GREEN") ? "RED" : "GREEN";
      broadcastState();
    }
  });

  ws.on('close', () => {});
  ws.on('error', () => {});
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
