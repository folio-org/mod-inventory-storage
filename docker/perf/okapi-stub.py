#!/usr/bin/env python3
"""
okapi-stub.py — minimal stand-in for the few Okapi calls the reindex endpoints make.

The publish/export endpoints call isCentralTenant() -> ConsortiumDataCache.loadConsortiumData,
which does GET {X-Okapi-Url}/user-tenants?limit=1. For a non-consortium throughput test we
just need that to return 200 with an empty userTenants array (the result is cached for 5 min).

Run this, then point the load test at it via OKAPI_URL=http://<host>:9131

    python3 okapi-stub.py 9131
"""
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer
sys.stdout.reconfigure(line_buffering=True)


class Handler(BaseHTTPRequestHandler):
    def _send(self, body):
        payload = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        # /user-tenants -> empty => tenant treated as non-consortium
        self._send({"userTenants": [], "totalRecords": 0})

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9131
    print(f"okapi-stub listening on :{port} (GET /user-tenants -> empty)")
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()


