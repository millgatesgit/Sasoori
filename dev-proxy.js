/**
 * Sasoori Dev Proxy — port 8080
 *
 * Routes:
 *   /api/v1/*  →  http://localhost:9090  (Jetty backend)
 *   /*         →  http://localhost:3000  (npx serve frontend)
 *
 * Usage:
 *   node dev-proxy.js
 *
 * Then point ngrok at port 8080:
 *   ngrok http 8080
 */

const http      = require('http');
const httpProxy = require('http-proxy');

const PROXY_PORT   = 8080;
const BACKEND_URL  = 'http://localhost:9090';
const FRONTEND_URL = 'http://localhost:3000';

const proxy = httpProxy.createProxyServer({ changeOrigin: true });

proxy.on('error', (err, req, res) => {
  console.error('[proxy error]', req.url, err.message);
  if (!res.headersSent) {
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Proxy error', detail: err.message }));
  }
});

const server = http.createServer((req, res) => {
  const target = req.url.startsWith('/api/') ? BACKEND_URL : FRONTEND_URL;
  proxy.web(req, res, { target });
});

server.listen(PROXY_PORT, () => {
  console.log(`\n  Sasoori Dev Proxy running on http://localhost:${PROXY_PORT}`);
  console.log(`  /api/*  →  ${BACKEND_URL}`);
  console.log(`  /*      →  ${FRONTEND_URL}`);
  console.log(`\n  Point ngrok here:  ngrok http ${PROXY_PORT}\n`);
});
