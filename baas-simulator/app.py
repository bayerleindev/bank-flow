#!/usr/bin/env python3
import json
import os
import random
import socket
import string
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib import request
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlparse


HOST = os.getenv("BAAS_HOST", "0.0.0.0")
PORT = int(os.getenv("BAAS_PORT", "8089"))
WEBHOOK_URL = os.getenv(
    "ACCOUNTS_WEBHOOK_URL", "http://localhost:8080/webhooks/baas/accounts"
)
WEBHOOK_DELAY_SECONDS = float(os.getenv("WEBHOOK_DELAY_SECONDS", "8"))
TRANSFERS_WEBHOOK_URL = os.getenv(
    "TRANSFERS_WEBHOOK_URL", "http://localhost:8083/baas/webhooks/transfers"
)
TRANSFERS_WEBHOOK_DELAY_SECONDS = float(
    os.getenv("TRANSFERS_WEBHOOK_DELAY_SECONDS", "2")
)
TRANSFERS_WEBHOOK_SUCCESS_RATE = float(os.getenv("TRANSFERS_WEBHOOK_SUCCESS_RATE", "0.5"))
WEBHOOK_TIMEOUT_SECONDS = float(os.getenv("WEBHOOK_TIMEOUT_SECONDS", "10"))
WEBHOOK_MAX_ATTEMPTS = int(os.getenv("WEBHOOK_MAX_ATTEMPTS", "3"))
WEBHOOK_RETRY_BACKOFF_SECONDS = float(os.getenv("WEBHOOK_RETRY_BACKOFF_SECONDS", "1"))
DICT_FILE = Path(os.getenv("DICT_FILE", str(Path(__file__).with_name("pix-keys.json"))))
END_TO_END_ISPB = "13935893"


class BaasSimulatorHandler(BaseHTTPRequestHandler):
    server_version = "BaasSimulator/0.1"

    def do_GET(self):
        if self.path == "/health":
            self.write_json(200, {"status": "UP"})
            return

        parsed_url = urlparse(self.path)
        if parsed_url.path == "/dict":
            self.handle_dict_lookup(parsed_url)
            return

        self.write_json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path == "/pix/payments":
            self.handle_pix_payment()
            return

        if self.path != "/accounts":
            self.write_json(404, {"error": "not_found"})
            return

        try:
            payload = self.read_json()
            account_id = payload["accountId"]
            trace_headers = self.read_trace_headers()
        except (json.JSONDecodeError, KeyError) as exc:
            self.write_json(400, {"error": "invalid_request", "message": str(exc)})
            return

        threading.Thread(
            target=send_webhook_later,
            args=(account_id, trace_headers),
            daemon=True,
        ).start()

        self.write_json(
            202,
            {
                "accountId": account_id,
                "status": "REQUEST_ACCEPTED",
                "webhookUrl": WEBHOOK_URL,
            },
        )

    def handle_pix_payment(self):
        try:
            payload = self.read_json()
            transfer_id = payload["transferId"]
            payload["debitParty"]
            payload["creditParty"]
            payload["amountMinor"]
            payload["currency"]
            payload["requestedAt"]
            trace_headers = self.read_trace_headers()
        except (json.JSONDecodeError, KeyError) as exc:
            self.write_json(400, {"error": "invalid_request", "message": str(exc)})
            return

        print(
            "pix payment requested transferId=%s amountMinor=%s currency=%s"
            % (transfer_id, payload["amountMinor"], payload["currency"])
        )
        threading.Thread(
            target=send_transfer_webhook_later,
            args=(transfer_id, trace_headers),
            daemon=True,
        ).start()

        self.write_json(202, {"transferId": transfer_id, "status": "REQUESTED"})

    def read_json(self):
        content_length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(content_length)
        return json.loads(body.decode("utf-8"))

    def write_json(self, status_code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def handle_dict_lookup(self, parsed_url):
        query = parse_qs(parsed_url.query)
        key = query.get("key", [None])[0]
        if not key:
            self.write_json(400, {"error": "missing_key"})
            return

        pix_key = load_pix_keys().get(key)
        if not pix_key:
            self.write_json(404, {"error": "key_not_found"})
            return

        self.write_json(
            200,
            {
                "account": pix_key["account"],
                "owner": pix_key["owner"],
                "endToEndId": build_end_to_end_id(),
            },
        )

    def log_message(self, format, *args):
        print("%s - %s" % (self.address_string(), format % args))

    def read_trace_headers(self):
        headers = {}
        traceparent = self.headers.get("traceparent")
        tracestate = self.headers.get("tracestate")
        if traceparent:
            headers["traceparent"] = traceparent
        if tracestate:
            headers["tracestate"] = tracestate
        return headers


def send_webhook_later(account_id, trace_headers):
    time.sleep(WEBHOOK_DELAY_SECONDS)
    webhook_payload = build_webhook_payload(account_id)
    data = json.dumps(webhook_payload).encode("utf-8")

    for attempt in range(1, WEBHOOK_MAX_ATTEMPTS + 1):
        if send_webhook(account_id, webhook_payload, data, trace_headers, attempt):
            return
        if attempt < WEBHOOK_MAX_ATTEMPTS:
            time.sleep(WEBHOOK_RETRY_BACKOFF_SECONDS * attempt)

    print(
        "webhook exhausted accountId=%s status=%s attempts=%s"
        % (account_id, webhook_payload["status"], WEBHOOK_MAX_ATTEMPTS)
    )


def send_webhook(account_id, webhook_payload, data, trace_headers, attempt):
    headers = {"Content-Type": "application/json", "Connection": "close"}
    headers.update(trace_headers)
    http_request = request.Request(
        WEBHOOK_URL,
        data=data,
        headers=headers,
        method="POST",
    )
    try:
        with request.urlopen(http_request, timeout=WEBHOOK_TIMEOUT_SECONDS) as response:
            print(
                "webhook sent accountId=%s status=%s httpStatus=%s attempt=%s"
                % (account_id, webhook_payload["status"], response.status, attempt)
            )
            return True
    except HTTPError as exc:
        print(
            "webhook failed accountId=%s status=%s httpStatus=%s attempt=%s body=%s"
            % (
                account_id,
                webhook_payload["status"],
                exc.code,
                attempt,
                exc.read().decode("utf-8"),
            )
        )
    except URLError as exc:
        print(
            "webhook failed accountId=%s status=%s attempt=%s reason=%s"
            % (account_id, webhook_payload["status"], attempt, exc.reason)
        )
    except (TimeoutError, socket.timeout) as exc:
        print(
            "webhook timeout accountId=%s status=%s attempt=%s timeout=%ss reason=%s"
            % (
                account_id,
                webhook_payload["status"],
                attempt,
                WEBHOOK_TIMEOUT_SECONDS,
                exc,
            )
        )

    return False


def send_transfer_webhook_later(transfer_id, trace_headers):
    time.sleep(TRANSFERS_WEBHOOK_DELAY_SECONDS)
    webhook_payload = build_transfer_webhook_payload(transfer_id)
    data = json.dumps(webhook_payload).encode("utf-8")

    for attempt in range(1, WEBHOOK_MAX_ATTEMPTS + 1):
        if send_transfer_webhook(transfer_id, webhook_payload, data, trace_headers, attempt):
            return
        if attempt < WEBHOOK_MAX_ATTEMPTS:
            time.sleep(WEBHOOK_RETRY_BACKOFF_SECONDS * attempt)

    print(
        "transfer webhook exhausted transferId=%s status=%s attempts=%s"
        % (transfer_id, webhook_payload["status"], WEBHOOK_MAX_ATTEMPTS)
    )


def send_transfer_webhook(transfer_id, webhook_payload, data, trace_headers, attempt):
    headers = {"Content-Type": "application/json", "Connection": "close"}
    headers.update(trace_headers)
    http_request = request.Request(
        TRANSFERS_WEBHOOK_URL,
        data=data,
        headers=headers,
        method="POST",
    )
    try:
        with request.urlopen(http_request, timeout=WEBHOOK_TIMEOUT_SECONDS) as response:
            print(
                "transfer webhook sent transferId=%s status=%s httpStatus=%s attempt=%s"
                % (transfer_id, webhook_payload["status"], response.status, attempt)
            )
            return True
    except HTTPError as exc:
        print(
            "transfer webhook failed transferId=%s status=%s httpStatus=%s attempt=%s body=%s"
            % (
                transfer_id,
                webhook_payload["status"],
                exc.code,
                attempt,
                exc.read().decode("utf-8"),
            )
        )
    except URLError as exc:
        print(
            "transfer webhook failed transferId=%s status=%s attempt=%s reason=%s"
            % (transfer_id, webhook_payload["status"], attempt, exc.reason)
        )
    except (TimeoutError, socket.timeout) as exc:
        print(
            "transfer webhook timeout transferId=%s status=%s attempt=%s timeout=%ss reason=%s"
            % (
                transfer_id,
                webhook_payload["status"],
                attempt,
                WEBHOOK_TIMEOUT_SECONDS,
                exc,
            )
        )

    return False


def build_webhook_payload(account_id):
    if random.random() < 0.85:
        account_number = str(random.randint(10000, 99999))
        return {
            "accountId": account_id,
            "status": "ACTIVE",
            "branchNumber": random.choice(["0001", "0002", "0003"]),
            "accountNumber": account_number,
            "accountDigit": str(calculate_digit(account_number)),
        }

    return {
        "accountId": account_id,
        "status": "REJECTED",
        "rejectionReason": random.choice(
            [
                "DOCUMENT_VALIDATION_FAILED",
                "BUREAU_REJECTED",
                "INCOMPLETE_REGISTRATION_DATA",
            ]
        ),
    }


def build_transfer_webhook_payload(transfer_id):
    if random.random() < TRANSFERS_WEBHOOK_SUCCESS_RATE:
        return {
            "transferId": transfer_id,
            "status": "COMPLETED",
            "reason": None,
        }

    return {
        "transferId": transfer_id,
        "status": "REJECTED",
        "reason": random.choice(
            [
                "BAAS_REJECTED",
                "DESTINATION_BANK_UNAVAILABLE",
                "PIX_PAYMENT_TIMEOUT",
            ]
        ),
    }


def calculate_digit(account_number):
    return sum(int(digit) for digit in account_number) % 10


def load_pix_keys():
    with DICT_FILE.open(encoding="utf-8") as dict_file:
        payload = json.load(dict_file)
        return payload["keys"]


def build_end_to_end_id():
    now = datetime.now(timezone.utc)
    random_suffix = "".join(random.choices(string.ascii_uppercase + string.digits, k=11))
    return "E%s%s%s%s" % (
        END_TO_END_ISPB,
        now.strftime("%Y%m%d"),
        now.strftime("%H%M"),
        random_suffix,
    )


def main():
    server = ThreadingHTTPServer((HOST, PORT), BaasSimulatorHandler)
    print("BaaS simulator running on http://%s:%s" % (HOST, PORT))
    print("Accounts webhook target: %s" % WEBHOOK_URL)
    print("Transfers webhook target: %s" % TRANSFERS_WEBHOOK_URL)
    print("DICT file: %s" % DICT_FILE)
    print(
        "Webhook retry: attempts=%s timeout=%ss backoff=%ss"
        % (WEBHOOK_MAX_ATTEMPTS, WEBHOOK_TIMEOUT_SECONDS, WEBHOOK_RETRY_BACKOFF_SECONDS)
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
