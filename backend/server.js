const { WebSocketServer } = require('ws');
const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });
const rooms = new Map();

function generateRoomId() { return Math.floor(100000 + Math.random() * 900000).toString(); }

wss.on('connection', (ws) => {
    ws.id = Math.random().toString(36).substring(7);
    ws.roomCode = null;

    ws.on('message', (msg) => {
        try {
            const data = JSON.parse(msg);
            handleMessage(ws, data);
        } catch(e) {}
    });

    ws.on('close', () => leaveRoom(ws));
});

function handleMessage(ws, data) {
    const { type, payload } = data;
    if (type === 'TIME_SYNC_REQUEST') {
        ws.send(JSON.stringify({ type: 'TIME_SYNC_RESPONSE', payload: { clientSendTime: payload.clientSendTime, serverTime: Date.now() } }));
    } else if (type === 'CREATE_ROOM') {
        const roomCode = generateRoomId();
        ws.roomCode = roomCode;
        rooms.set(roomCode, { hostId: ws.id, members: new Map([[ws.id, { ws, name: payload.name, isReady: true }]]) });
        ws.send(JSON.stringify({ type: 'ROOM_CREATED', payload: { roomCode, hostId: ws.id } }));
        broadcastRoomState(roomCode);
    } else if (type === 'JOIN_ROOM') {
        const room = rooms.get(payload.roomCode);
        if (!room) return ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'الغرفة غير موجودة!' } }));
        ws.roomCode = payload.roomCode;
        room.members.set(ws.id, { ws, name: payload.name, isReady: false });
        ws.send(JSON.stringify({ type: 'ROOM_JOINED', payload: { roomCode: payload.roomCode, hostId: room.hostId } }));
        broadcastRoomState(payload.roomCode);
    } else if (type === 'SET_READY') {
        const room = rooms.get(ws.roomCode);
        if (room && room.members.has(ws.id)) {
            room.members.get(ws.id).isReady = payload.isReady;
            broadcastRoomState(ws.roomCode);
        }
    } else if (type === 'TRIGGER_START') {
        const room = rooms.get(ws.roomCode);
        if (room && room.hostId === ws.id) {
            broadcastToRoom(ws.roomCode, { type: 'START_SCHEDULED', payload: { targetServerTime: Date.now() + 3000, countdownMs: 3000 } });
        }
    } else if (type === 'TRIGGER_STOP') {
        broadcastToRoom(ws.roomCode, { type: 'EMERGENCY_STOP', payload: {} });
    }
}

function leaveRoom(ws) {
    if (!ws.roomCode || !rooms.has(ws.roomCode)) return;
    const room = rooms.get(ws.roomCode);
    room.members.delete(ws.id);
    if (room.members.size === 0) rooms.delete(ws.roomCode);
    else {
        if (room.hostId === ws.id) room.hostId = room.members.keys().next().value;
        broadcastRoomState(ws.roomCode);
    }
}

function broadcastRoomState(code) {
    const room = rooms.get(code);
    if (!room) return;
    const members = [];
    room.members.forEach((v, k) => members.push({ id: k, name: v.name, isReady: v.isReady, isHost: room.hostId === k }));
    broadcastToRoom(code, { type: 'ROOM_UPDATE', payload: { roomCode: code, hostId: room.hostId, members } });
}

function broadcastToRoom(code, data) {
    const room = rooms.get(code);
    if (!room) return;
    const str = JSON.stringify(data);
    room.members.forEach(m => { if (m.ws.readyState === 1) m.ws.send(str); });
}

console.log(`Server running on port ${PORT}`);