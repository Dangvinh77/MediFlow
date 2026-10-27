"""Capture and replay the three outpatient patient-result facts on a rehearsal broker.

This helper is intentionally limited to the disposable RabbitMQ declared by
``active-rehearsal.local.json``. It never reads a business database or logs event bodies.
The optional ``pika`` package is needed only for this additional broker acceptance.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import threading
import time
from collections import Counter
from pathlib import Path


EVENTS = {
    "medicalrecord.completed": "recordId",
    "lab.result.created": "labId",
    "followup.recommendation.created": "recommendationId",
}
EXPECTED_TITLES = {
    "medicalrecord.completed": "Kết quả khám đã có",
    "lab.result.created": "Kết quả xét nghiệm đã có",
    "followup.recommendation.created": "Có đề xuất tái khám",
}
BROKER_NAME = re.compile(r"^mediflow-rehearsal-[0-9]{8}-[0-9]{6}-rabbit$")
EXCHANGE = "mediflow.events"
NOTICE_QUEUE = "notification.q"
DLQ = "notification.dlq"
REPLAY_COPIES = 3


def _docker(*args: str) -> str:
    return subprocess.run(["docker", *args], check=True, capture_output=True, text=True,
                          timeout=10).stdout.strip()


def _normalize_property(value):
    if isinstance(value, bytes):
        return {"bytesHex": value.hex()}
    if isinstance(value, dict):
        return {str(key): _normalize_property(child) for key, child in sorted(value.items(), key=lambda row: str(row[0]))}
    if isinstance(value, (list, tuple)):
        return [_normalize_property(child) for child in value]
    if hasattr(value, "isoformat"):
        return value.isoformat()
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    return repr(value)


def _properties_signature(properties) -> str:
    names = ("content_type", "content_encoding", "headers", "delivery_mode", "priority",
             "correlation_id", "reply_to", "expiration", "message_id", "timestamp", "type",
             "user_id", "app_id", "cluster_id")
    normalized = {name: _normalize_property(getattr(properties, name, None)) for name in names}
    return json.dumps(normalized, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


class ResultReplayAcceptance:
    """Observe exact source envelopes and prove their replay is effect-idempotent."""

    def __init__(self, manifest_path: Path, gateway: str):
        try:
            import pika
        except ImportError as error:
            raise RuntimeError("Install optional runner dependency with: python -m pip install pika==1.3.2") from error

        self.pika = pika
        self.gateway = gateway.rstrip("/")
        self.manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if not self.manifest.get("scheduling") or self.manifest.get("gateway", "").rstrip("/") != self.gateway:
            raise RuntimeError("Result replay requires the matching active disposable scheduling rehearsal")
        if Path(self.manifest["stopFile"]).exists():
            raise RuntimeError("The active rehearsal has already been stopped")

        names = self.manifest.get("containers", [])
        brokers = [name for name in names if isinstance(name, str) and BROKER_NAME.fullmatch(name)]
        if len(brokers) != 1:
            raise RuntimeError("Could not identify exactly one runner-owned RabbitMQ container")
        self.broker_name = brokers[0]
        self._assert_owned_running_container()
        port = _docker("port", self.broker_name, "5672/tcp")
        match = re.fullmatch(r"127\.0\.0\.1:(\d+)", port)
        if not match:
            raise RuntimeError("The rehearsal broker must publish AMQP on loopback only")
        self.port = int(match.group(1))
        self._condition = threading.Condition()
        self._captured: list[dict] = []
        self._consumer_error: BaseException | None = None
        self._stop_consumer = threading.Event()
        self._consumer_ready = threading.Event()
        self.capture_queue = None
        self._consumer_thread = threading.Thread(target=self._consume, name="onboarding-result-event-capture", daemon=True)
        self._consumer_thread.start()
        if not self._consumer_ready.wait(timeout=15):
            self.close()
            raise TimeoutError("Could not start the private event observer on the rehearsal broker")
        if self._consumer_error:
            error = self._consumer_error
            self.close()
            raise RuntimeError("Could not initialize the private event observer") from error
        try:
            self._queue_state()
            self._assert_queue_binding()
        except BaseException:
            self.close()
            raise

    @classmethod
    def begin(cls, manifest_path: Path, gateway: str) -> "ResultReplayAcceptance":
        """Start the private observer before any result events are produced."""
        return cls(manifest_path, gateway)

    def _assert_owned_running_container(self) -> None:
        info = _docker("inspect", "--format", "{{.Config.Image}} {{.State.Running}}", self.broker_name)
        image, separator, running = info.rpartition(" ")
        if not separator or image != "rabbitmq:3.13-alpine" or running != "true":
            raise RuntimeError("Manifest broker is not the expected running disposable RabbitMQ image")

    def _consume(self) -> None:
        try:
            params = self.pika.ConnectionParameters(
                host="127.0.0.1", port=self.port,
                credentials=self.pika.PlainCredentials("rehearsal", "rehearsal-local"),
                heartbeat=30, blocked_connection_timeout=10, socket_timeout=10, connection_attempts=1)
            self.capture_connection = self.pika.BlockingConnection(params)
            self.capture_channel = self.capture_connection.channel()
            self.capture_queue = self.capture_channel.queue_declare(queue="", exclusive=True,
                                                                    auto_delete=True).method.queue
            for event_type in EVENTS:
                self.capture_channel.queue_bind(queue=self.capture_queue, exchange=EXCHANGE,
                                                routing_key=event_type)
            self.capture_channel.basic_consume(queue=self.capture_queue, on_message_callback=self._on_event,
                                               auto_ack=True)
            self._consumer_ready.set()
            while not self._stop_consumer.is_set():
                self.capture_connection.process_data_events(time_limit=0.5)
        except BaseException as error:  # propagate AMQP loss to the acceptance caller
            with self._condition:
                self._consumer_error = error
                self._condition.notify_all()
            self._consumer_ready.set()
        finally:
            if getattr(self, "capture_connection", None) and self.capture_connection.is_open:
                self.capture_connection.close()

    def _on_event(self, _channel, method, properties, body: bytes) -> None:
        try:
            envelope = json.loads(body)
            event_type = method.routing_key
            if envelope.get("eventType") != event_type or event_type not in EVENTS:
                raise AssertionError("Captured an event with an unexpected routing key or envelope type")
            if envelope.get("version") != 1 or envelope.get("producer") != (
                    "lab-service" if event_type == "lab.result.created" else "clinical-service"):
                raise AssertionError("Captured a source event with an unexpected version or producer")
            payload = envelope.get("payload") or {}
            record = {"routingKey": event_type, "eventId": envelope.get("eventId"),
                      "patientId": payload.get("patientId"), "sourceId": payload.get(EVENTS[event_type]),
                      "body": bytes(body), "properties": properties,
                      "propertiesSignature": _properties_signature(properties)}
            with self._condition:
                self._captured.append(record)
                self._condition.notify_all()
        except BaseException as error:
            with self._condition:
                self._consumer_error = error
                self._condition.notify_all()

    def _assert_queue_binding(self) -> None:
        output = _docker("exec", self.broker_name, "rabbitmqctl", "list_bindings", "-p", "/",
                         "source_name", "destination_name", "routing_key").splitlines()
        bindings = {tuple(line.split("\t")) for line in output[1:] if line.strip()}
        missing = [event for event in EVENTS if (EXCHANGE, NOTICE_QUEUE, event) not in bindings]
        if missing:
            raise AssertionError("Notification queue is not bound for result event(s): " + ", ".join(missing))

    def _queue_state(self) -> dict[str, dict[str, int]]:
        output = _docker("exec", self.broker_name, "rabbitmqctl", "list_queues", "-p", "/",
                         "name", "messages_ready", "messages_unacknowledged", "consumers").splitlines()
        queues = {}
        for line in output[1:]:
            columns = line.split("\t")
            if len(columns) == 4 and columns[0] in (NOTICE_QUEUE, DLQ):
                queues[columns[0]] = {"ready": int(columns[1]), "unacknowledged": int(columns[2]),
                                      "consumers": int(columns[3])}
        if NOTICE_QUEUE not in queues or DLQ not in queues:
            raise AssertionError("Expected Notification queue and DLQ are not declared in the rehearsal broker")
        return queues

    def _wait_for_originals(self, patient_id: str, source_ids: dict[str, str], timeout: int = 40) -> list[dict]:
        deadline = time.monotonic() + timeout
        with self._condition:
            while time.monotonic() < deadline:
                if self._consumer_error:
                    raise RuntimeError("AMQP observer failed") from self._consumer_error
                found = []
                for event_type, source_id in source_ids.items():
                    candidates = [event for event in self._captured
                                  if event["routingKey"] == event_type and event["patientId"] == patient_id
                                  and event["sourceId"] == source_id]
                    if len(candidates) == 1 and candidates[0]["eventId"]:
                        found.append(candidates[0])
                    elif len(candidates) > 1:
                        raise AssertionError(f"Expected one original {event_type} fact, saw {len(candidates)}")
                if len(found) == len(EVENTS):
                    ids = [event["eventId"] for event in found]
                    if len(set(ids)) != len(EVENTS):
                        raise AssertionError("The three source facts must have distinct event IDs")
                    return found
                self._condition.wait(timeout=min(1.0, max(0.0, deadline - time.monotonic())))
        raise TimeoutError("Did not observe all three original result events on the isolated rehearsal broker")

    def _wait_for_capture_copies(self, originals: list[dict], baseline: Counter,
                                 start_index: int, timeout: int = 30) -> None:
        keys = {(event["routingKey"], event["eventId"]) for event in originals}
        wanted = Counter({key: REPLAY_COPIES for key in keys})
        expected = baseline + wanted
        deadline = time.monotonic() + timeout
        with self._condition:
            while time.monotonic() < deadline:
                if self._consumer_error:
                    raise RuntimeError("AMQP observer failed while verifying exact replay") from self._consumer_error
                current = Counter((event["routingKey"], event["eventId"]) for event in self._captured
                                  if (event["routingKey"], event["eventId"]) in keys)
                if all(current[key] >= count for key, count in expected.items()):
                    if current != expected:
                        raise AssertionError("Observed unexpected duplicate result-event deliveries")
                    replayed = [event for event in self._captured[start_index:]
                                if (event["routingKey"], event["eventId"]) in keys]
                    original_by_key = {(event["routingKey"], event["eventId"]): event for event in originals}
                    replay_counts = Counter((event["routingKey"], event["eventId"]) for event in replayed)
                    if replay_counts != wanted:
                        raise AssertionError("Expected exactly three captured replay copies per source event")
                    for event in replayed:
                        original = original_by_key[(event["routingKey"], event["eventId"])]
                        if event["body"] != original["body"]:
                            raise AssertionError("Replayed event bytes differ from the original source envelope")
                        if event["propertiesSignature"] != original["propertiesSignature"]:
                            raise AssertionError("Replayed AMQP properties differ from the original source event")
                    return
                self._condition.wait(timeout=min(0.5, max(0.0, deadline - time.monotonic())))
        raise TimeoutError("RabbitMQ did not route all three replay copies to the private observer")

    def _wait_for_notification_drain(self, timeout: int = 30) -> dict[str, dict[str, int]]:
        """Require an active listener and a stable empty durable notification queue."""
        deadline = time.monotonic() + timeout
        stable = 0
        last = None
        while time.monotonic() < deadline:
            state = self._queue_state()
            notice = state[NOTICE_QUEUE]
            if notice["consumers"] < 1:
                stable = 0
            elif notice["ready"] == 0 and notice["unacknowledged"] == 0:
                stable = stable + 1 if state == last else 1
                if stable >= 3:
                    return state
            else:
                stable = 0
            last = state
            time.sleep(0.5)
        raise TimeoutError("Notification consumer did not drain the rehearsal queue to a stable empty state")

    def verify(self, call, patient_id: str, record_id: str, test_id: str,
               recommendation_id: str, expected_notice_ids: list[str]) -> dict:
        """Replay original event bytes and assert no extra patient history effects."""
        source_ids = {
            "medicalrecord.completed": record_id,
            "lab.result.created": test_id,
            "followup.recommendation.created": recommendation_id,
        }
        originals = self._wait_for_originals(patient_id, source_ids)
        by_type = {event["routingKey"]: event for event in originals}
        for event_type, event in by_type.items():
            assert event["patientId"] == patient_id and event["sourceId"] == source_ids[event_type]

        before = self._patient_history(call, patient_id)
        before_ids = sorted(row["notificationId"] for row in before)
        expected_ids = sorted(expected_notice_ids)
        if before_ids != expected_ids or len(before) != 3:
            raise AssertionError("Before replay, patient history must contain exactly the three captured result notices")
        for event_type, title in EXPECTED_TITLES.items():
            matches = [row for row in before if row.get("title") == title]
            if len(matches) != 1 or matches[0]["patientId"] != patient_id or matches[0]["channel"] != "IN_APP":
                raise AssertionError(f"Expected exactly one private in-app notice for {event_type}")

        queue_before = self._wait_for_notification_drain()
        if queue_before[DLQ]["ready"] or queue_before[DLQ]["unacknowledged"]:
            raise AssertionError("Result replay starts with unrelated dead letters; isolate/diagnose the fixture first")
        publisher_params = self.pika.ConnectionParameters(
            host="127.0.0.1", port=self.port,
            credentials=self.pika.PlainCredentials("rehearsal", "rehearsal-local"),
            heartbeat=30, blocked_connection_timeout=10, socket_timeout=10, connection_attempts=1)
        keys = {(event["routingKey"], event["eventId"]) for event in originals}
        with self._condition:
            capture_start_index = len(self._captured)
            capture_baseline = Counter((event["routingKey"], event["eventId"]) for event in self._captured
                                       if (event["routingKey"], event["eventId"]) in keys)
        publisher_connection = self.pika.BlockingConnection(publisher_params)
        replay = None
        try:
            replay = publisher_connection.channel()
            replay.confirm_delivery()
            for event in originals:
                for _ in range(REPLAY_COPIES):
                    accepted = replay.basic_publish(exchange=EXCHANGE, routing_key=event["routingKey"],
                                                    body=event["body"], properties=event["properties"],
                                                    mandatory=True)
                    if accepted is False:
                        raise AssertionError("Broker did not confirm replay publication")
        finally:
            if replay is not None and replay.is_open:
                replay.close()
            if publisher_connection.is_open:
                publisher_connection.close()
        self._wait_for_capture_copies(originals, capture_baseline, capture_start_index)
        queue_after = self._wait_for_notification_drain()
        if queue_after[DLQ] != queue_before[DLQ]:
            raise AssertionError("Replayed result facts changed the Notification DLQ counts")

        after = self._patient_history(call, patient_id)
        after_ids = sorted(row["notificationId"] for row in after)
        if after_ids != before_ids or len(after) != len(before):
            raise AssertionError("Replaying source events created or removed a patient notification")
        by_title = {row["title"]: row["notificationId"] for row in after}
        if any(by_title.get(title) != next(row["notificationId"] for row in before if row["title"] == title)
               for title in EXPECTED_TITLES.values()):
            raise AssertionError("The original notification effect IDs changed after replay")

        return {
            "scope": "isolated-packaged-notification-result-event-replay",
            "brokerContainer": self.broker_name,
            "patientId": patient_id,
            "events": [{"eventType": event["routingKey"], "eventId": event["eventId"],
                        "sourceId": event["sourceId"], "bodySha256": hashlib.sha256(event["body"]).hexdigest(),
                        "originalCopies": 1, "replayCopies": REPLAY_COPIES} for event in originals],
            "notificationIdsBefore": before_ids,
            "notificationIdsAfter": after_ids,
            "notificationQueueBefore": queue_before[NOTICE_QUEUE],
            "notificationQueueAfter": queue_after[NOTICE_QUEUE],
            "deadLetterQueueBefore": queue_before[DLQ],
            "deadLetterQueueAfter": queue_after[DLQ],
            "historyEffectCountBefore": len(before),
            "historyEffectCountAfter": len(after),
            "passed": True,
        }

    @staticmethod
    def _patient_history(call, patient_id: str) -> list[dict]:
        result = call("nurse", "GET", f"/api/v1/notifications/patient/{patient_id}?page=0&size=100")
        return [row for row in result["content"] if row["title"] in EXPECTED_TITLES.values()]

    def close(self) -> None:
        self._stop_consumer.set()
        if getattr(self, "_consumer_thread", None):
            self._consumer_thread.join(timeout=3)

    def __enter__(self) -> "ResultReplayAcceptance":
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        self.close()
