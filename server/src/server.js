const http = require('http');
const { WebSocketServer } = require('ws');

const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ZK Turn Server is active and running!\n');
});

const wss = new WebSocketServer({ server });

let currentState = "GREEN"; // الحالة الافتراضية للزر

wss.on('connection', (ws) => {
    // إرسال الحالة الحالية للعميل فور الاتصال
    ws.send(JSON.stringify({ type: 'STATE_UPDATE', state: currentState }));

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            if (data.action === 'TOGGLE_BUTTON') {
                // تبديل الحالة بين GREEN و RED
                currentState = currentState === "GREEN" ? "RED" : "GREEN";

                // بث الحالة الجديدة لجميع العملاء المتصلين
                wss.clients.forEach((client) => {
                    if (client.readyState === client.OPEN) {
                        client.send(JSON.stringify({ type: 'STATE_UPDATE', state: currentState }));
                    }
                });
            }
        } catch (e) {
            console.error("Error parsing message:", e);
        }
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
