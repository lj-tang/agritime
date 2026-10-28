import http.server, http.client, os, urllib.parse

FRONTEND_PAGES = '/tmp/agritime-push/frontend/pages'
FRONTEND_ASSETS = '/tmp/agritime-push/frontend/assets'
BACKEND = ('localhost', 8080)

CT_MAP = {'.js':'application/javascript','.css':'text/css','.json':'application/json',
          '.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.html':'text/html;charset=utf-8',
          '.ico':'image/x-icon','.woff':'font/woff','.woff2':'font/woff2'}

class Handler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self): self.handle_request()
    def do_POST(self): self.handle_request()
    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin','*')
        self.send_header('Access-Control-Allow-Methods','GET,POST,OPTIONS')
        self.send_header('Access-Control-Allow-Headers','Content-Type')
        self.end_headers()
    def handle_request(self):
        if self.path.startswith('/api/') or self.path.startswith('/health'):
            self.proxy(); return
        if self.path.startswith('/assets/'):
            self.serve_file(FRONTEND_ASSETS + urllib.parse.unquote(self.path[7:])); return
        path = urllib.parse.unquote(self.path.split('?')[0])
        if path == '/' or path == '': path = '/index.html'
        self.serve_file(FRONTEND_PAGES + path)
    def serve_file(self, fpath):
        try:
            with open(fpath, 'rb') as f: content = f.read()
            ext = os.path.splitext(fpath)[1].lower()
            self.send_response(200)
            self.send_header('Content-Type', CT_MAP.get(ext, 'application/octet-stream'))
            self.send_header('Content-Length', str(len(content)))
            self.end_headers()
            self.wfile.write(content)
        except FileNotFoundError:
            try:
                with open(FRONTEND_PAGES + '/index.html', 'rb') as f: c = f.read()
                self.send_response(200); self.send_header('Content-Type','text/html;charset=utf-8')
                self.send_header('Content-Length',str(len(c))); self.end_headers(); self.wfile.write(c)
            except Exception: self.send_error(404)
    def proxy(self):
        try:
            conn = http.client.HTTPConnection(BACKEND[0], BACKEND[1], timeout=30)
            headers = {k: v for k, v in self.headers.items() if k.lower() not in ('host','connection')}
            body = None
            if self.command == 'POST':
                length = int(self.headers.get('Content-Length', 0))
                body = self.rfile.read(length) if length else None
            conn.request(self.command, self.path, body=body, headers=headers)
            resp = conn.getresponse()
            data = resp.read()
            self.send_response(resp.status)
            self.send_header('Access-Control-Allow-Origin','*')
            for k, v in resp.getheaders():
                if k.lower() not in ('transfer-encoding','connection'): self.send_header(k, v)
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            self.send_error(502, str(e))

server = http.server.HTTPServer(('0.0.0.0', 80), Handler)
print('前端 server 启动在 80 端口，root=/tmp/agritime-push/frontend/pages，反代 /api->8080', flush=True)
server.serve_forever()