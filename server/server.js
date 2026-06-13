const express = require('express');
const app = express();
const PORT = 3000;

app.use(express.json());

// Guarda os clientes SSE conectados
const clients = new Set();

app.get('/events', (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.flushHeaders();

  clients.add(res);
  console.log(`Cliente conectou. Total: ${clients.size}`);

  req.on('close', () => {
    clients.delete(res);
    console.log(`Cliente desconectou. Total: ${clients.size}`);
  });
});

// Endpoint chamado pelo painel web para disparar um evento
app.post('/send', (req, res) => {
  const { message } = req.body;
  if (!message) return res.status(400).json({ error: 'message obrigatório' });

  const payload = JSON.stringify({
    message,
    timestamp: new Date().toISOString(),
  });

  let sent = 0;
  clients.forEach(client => {
    client.write(`event: update\n`);
    client.write(`data: ${payload}\n\n`);
    sent++;
  });

  console.log(`Evento enviado para ${sent} cliente(s): ${message}`);
  res.json({ ok: true, clients: sent });
});

// Painel web para disparar eventos manualmente
app.get('/', (req, res) => {
  res.send(`<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <title>SSE - Painel</title>
  <style>
    body { font-family: sans-serif; max-width: 480px; margin: 60px auto; padding: 0 16px; }
    h1 { font-size: 1.4rem; margin-bottom: 24px; }
    input { width: 100%; padding: 10px; font-size: 1rem; box-sizing: border-box; border: 1px solid #ccc; border-radius: 6px; }
    button { margin-top: 12px; padding: 10px 24px; font-size: 1rem; background: #6200ee; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
    button:hover { background: #3700b3; }
    #status { margin-top: 16px; font-size: 0.9rem; color: #555; }
    #log { margin-top: 24px; border-top: 1px solid #eee; padding-top: 16px; }
    .entry { font-size: 0.85rem; color: #333; margin-bottom: 4px; }
  </style>
</head>
<body>
  <h1>SSE — Disparar Evento</h1>
  <input id="msg" type="text" placeholder="Digite a mensagem..." />
  <br>
  <button onclick="send()">Enviar</button>
  <div id="status"></div>
  <div id="log"></div>

  <script>
    document.getElementById('msg').addEventListener('keydown', e => {
      if (e.key === 'Enter') send();
    });

    async function send() {
      const msg = document.getElementById('msg').value.trim();
      if (!msg) return;

      const res = await fetch('/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: msg }),
      });
      const data = await res.json();

      document.getElementById('status').textContent =
        \`✓ Enviado para \${data.clients} cliente(s)\`;
      document.getElementById('msg').value = '';

      const entry = document.createElement('div');
      entry.className = 'entry';
      entry.textContent = \`[\${new Date().toLocaleTimeString()}] \${msg}\`;
      document.getElementById('log').prepend(entry);
    }
  </script>
</body>
</html>`);
});

app.listen(PORT, () => {
  console.log(`Servidor SSE rodando em http://localhost:${PORT}`);
  console.log(`Painel: http://localhost:${PORT}`);
  console.log(`Endpoint SSE: http://localhost:${PORT}/events`);
});
