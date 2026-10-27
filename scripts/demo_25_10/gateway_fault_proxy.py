"""Loopback-only API proxy for the disposable scheduling rehearsal.

Point the Next.js dev server's GATEWAY_URL at this proxy to observe browser recovery
after a committed patient-account issuance response is intentionally lost.
"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import http.client
import json
from pathlib import Path
import threading
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MANIFEST = HERE / "evidence" / "active-rehearsal.local.json"
EXPECTED_GATEWAY = "http://127.0.0.1:28080"
ISSUANCE_PATH = "/api/v1/org/patient-accounts"
HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
}
MAX_REQUEST_BYTES = 8 * 1024 * 1024
MAX_RESPONSE_BYTES = 16 * 1024 * 1024


def load_rehearsal():
    if not MANIFEST.is_file():
        raise RuntimeError("No active disposable rehearsal manifest")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    stop_file = Path(manifest.get("stopFile", ""))
    evidence_dir = Path(manifest.get("evidence", ""))
    if (manifest.get("gateway") != EXPECTED_GATEWAY
            or manifest.get("ports", {}).get("gateway") != 28080
            or manifest.get("scheduling") is not True
            or not stop_file.is_absolute()
            or not evidence_dir.is_absolute()
            or not evidence_dir.is_dir()
            or stop_file.parent != evidence_dir
            or stop_file.exists()):
        raise RuntimeError("Manifest is not an active scheduling rehearsal on Gateway 127.0.0.1:28080")
    return manifest, stop_file, evidence_dir


def read_chunked(handler):
    chunks = bytearray()
    while True:
        line = handler.rfile.readline(8192)
        if not line:
            raise ConnectionError("Incomplete chunked request")
        size_text = line.split(b";", 1)[0].strip()
        try:
            size = int(size_text, 16)
        except ValueError as error:
            raise ConnectionError("Invalid chunked request") from error
        if size == 0:
            while handler.rfile.readline(8192) not in (b"\r\n", b"\n", b""):
                pass
            break
        if len(chunks) + size > MAX_REQUEST_BYTES:
            raise OverflowError("Request body too large")
        chunk = handler.rfile.read(size)
        if len(chunk) != size or handler.rfile.read(2) != b"\r\n":
            raise ConnectionError("Incomplete chunked request")
        chunks.extend(chunk)
    return bytes(chunks)


def request_body(handler):
    transfer_encoding = handler.headers.get("Transfer-Encoding", "").lower()
    if transfer_encoding:
        if transfer_encoding.strip() != "chunked":
            raise ValueError("Unsupported transfer encoding")
        return read_chunked(handler)
    raw_length = handler.headers.get("Content-Length")
    if raw_length is None:
        return b""
    length = int(raw_length)
    if length < 0 or length > MAX_REQUEST_BYTES:
        raise OverflowError("Request body too large")
    body = handler.rfile.read(length)
    if len(body) != length:
        raise ConnectionError("Incomplete request body")
    return body


def hop_headers(headers):
    connection_tokens = set()
    for name, value in headers:
        if name.lower() == "connection":
            connection_tokens.update(part.strip().lower() for part in value.split(",") if part.strip())
    return HOP_BY_HOP | connection_tokens


class FaultProxyServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, handler, manifest, stop_file, evidence_path, drop_fault, watch_stop):
        super().__init__(address, handler)
        self.manifest = manifest
        self.stop_file = stop_file
        self.evidence_path = evidence_path
        self.drop_armed = drop_fault
        self.watch_stop = watch_stop
        self.state_lock = threading.Lock()
        self.evidence_lock = threading.Lock()
        self.evidence = {
            "schemaVersion": 1,
            "upstream": EXPECTED_GATEWAY,
            "fault": "drop-next-successful-patient-account-issuance-response" if drop_fault else "disabled",
            "requests": [],
        }
        self.write_evidence()

    def write_evidence(self):
        with self.evidence_lock:
            temporary = self.evidence_path.with_suffix(self.evidence_path.suffix + ".tmp")
            temporary.write_text(json.dumps(self.evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            temporary.replace(self.evidence_path)

    def record(self, entry):
        safe_entry = {key: value for key, value in entry.items()
                      if key in {"method", "path", "status", "patientId", "accountId",
                                 "idempotencyKeySha256", "clientResponseDropped"}}
        with self.evidence_lock:
            self.evidence["requests"].append(safe_entry)
            temporary = self.evidence_path.with_suffix(self.evidence_path.suffix + ".tmp")
            temporary.write_text(json.dumps(self.evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            temporary.replace(self.evidence_path)
        print(json.dumps(safe_entry, ensure_ascii=False, separators=(",", ":")), flush=True)


class FaultProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MediFlowRehearsalProxy"
    sys_version = ""

    def log_message(self, *_):
        # BaseHTTPRequestHandler would log arbitrary request targets and peer data.
        pass

    def do_GET(self):
        self.forward()

    def do_HEAD(self):
        self.forward()

    def do_POST(self):
        self.forward()

    def do_PUT(self):
        self.forward()

    def do_PATCH(self):
        self.forward()

    def do_DELETE(self):
        self.forward()

    def do_OPTIONS(self):
        self.forward()

    def do_TRACE(self):
        self.forward()

    def forward(self):
        parsed = urlsplit(self.path)
        log_path = parsed.path or "/"
        status = 502
        body = b""
        patient_id = None
        account_id = None
        key_hash = None
        issuance = self.command == "POST" and parsed.path == ISSUANCE_PATH

        if parsed.scheme or parsed.netloc or not (parsed.path == "/api" or parsed.path.startswith("/api/")):
            self.send_error_response(404)
            self.server.record({"method": self.command, "path": log_path, "status": 404})
            return

        try:
            body = request_body(self)
            if issuance:
                try:
                    request_json = json.loads(body) if body else {}
                    patient_id = request_json.get("patientId")
                except (UnicodeDecodeError, json.JSONDecodeError, AttributeError):
                    patient_id = None
                key = self.headers.get("Idempotency-Key")
                if key:
                    key_hash = hashlib.sha256(key.encode("utf-8")).hexdigest()

            inbound_headers = list(self.headers.items())
            blocked = hop_headers(inbound_headers) | {"host", "content-length"}
            headers = [(name, value) for name, value in inbound_headers if name.lower() not in blocked]
            headers.append(("Content-Length", str(len(body))))

            target = self.path
            connection = http.client.HTTPConnection("127.0.0.1", 28080, timeout=35)
            try:
                connection.request(self.command, target, body=body, headers=dict(headers))
                response = connection.getresponse()
                status = response.status
                response_body = response.read(MAX_RESPONSE_BYTES + 1)
                if len(response_body) > MAX_RESPONSE_BYTES:
                    status = 502
                    response_body = b'{"error":{"code":"REHEARSAL_RESPONSE_TOO_LARGE"}}'
                    upstream_headers = []
                else:
                    upstream_headers = response.getheaders()
            finally:
                connection.close()

            if issuance and 200 <= status < 300:
                try:
                    envelope = json.loads(response_body)
                    payload = envelope.get("data", envelope)
                    account_id = payload.get("accountId") if isinstance(payload, dict) else None
                except (UnicodeDecodeError, json.JSONDecodeError, AttributeError):
                    account_id = None

            drop = False
            if issuance and status == 201:
                with self.server.state_lock:
                    if self.server.drop_armed:
                        self.server.drop_armed = False
                        drop = True

            if drop:
                # Upstream committed and returned 201; the browser receives no status or body.
                self.server.record({"method": self.command, "path": log_path, "status": status,
                                    "patientId": patient_id, "accountId": account_id,
                                    "idempotencyKeySha256": key_hash, "clientResponseDropped": True})
                self.close_connection = True
                try:
                    self.connection.shutdown(2)
                except OSError:
                    pass
                self.connection.close()
                return

            self.send_upstream_response(status, response_body, upstream_headers)
            entry = {"method": self.command, "path": log_path, "status": status}
            if issuance:
                entry.update(patientId=patient_id, accountId=account_id, idempotencyKeySha256=key_hash)
            self.server.record(entry)
        except OverflowError:
            self.send_error_response(413)
            self.server.record({"method": self.command, "path": log_path, "status": 413})
        except (ValueError, ConnectionError, OSError, http.client.HTTPException):
            self.send_error_response(502)
            self.server.record({"method": self.command, "path": log_path, "status": 502})

    def send_upstream_response(self, status, body, headers):
        self.send_response(status)
        blocked = hop_headers(headers) | {"content-length", "server", "date"}
        for name, value in headers:
            if name.lower() not in blocked:
                self.send_header(name, value)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        if self.command != "HEAD" and body:
            self.wfile.write(body)
        self.close_connection = True

    def send_error_response(self, status):
        body = (json.dumps({"error": {"code": "REHEARSAL_PROXY_REJECTED" if status == 404
                                      else "REHEARSAL_PROXY_UPSTREAM_UNAVAILABLE"}})
                .encode("utf-8"))
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=28081,
                        help="Loopback listening port; use 0 to select an available port (default: 28081)")
    parser.add_argument("--drop-next-issuance-response", action="store_true",
                        help="Drop the first HTTP 201 response from patient account issuance after Gateway commits")
    parser.add_argument("--stop-with-rehearsal", action="store_true",
                        help="Stop this proxy when the manifest stopFile appears")
    parser.add_argument("--output", type=Path,
                        help="Local JSON evidence path, constrained to the active rehearsal evidence folder")
    args = parser.parse_args()
    if args.port < 0 or args.port > 65535:
        parser.error("--port must be between 0 and 65535")

    try:
        manifest, stop_file, evidence_dir = load_rehearsal()
        evidence_path = (args.output or evidence_dir / "gateway-fault-proxy.local.json").resolve()
        if not evidence_path.is_relative_to(evidence_dir.resolve()):
            parser.error("--output must stay inside the active rehearsal evidence folder")
        server = FaultProxyServer(("127.0.0.1", args.port), FaultProxyHandler,
                                  manifest, stop_file, evidence_path,
                                  args.drop_next_issuance_response, args.stop_with_rehearsal)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        parser.error(str(error))

    if args.stop_with_rehearsal:
        server.timeout = 0.5
    try:
        while True:
            if server.watch_stop and server.stop_file.exists():
                break
            server.handle_request()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
