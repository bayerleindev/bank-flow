import argparse
import json
import uuid
from typing import Any, Dict, Optional

from confluent_kafka import Producer


def build_event(original_external_id: str, reason: str, reversal_id: Optional[str] = None) -> Dict[str, Any]:
    return {
        "reversal_id": reversal_id or str(uuid.uuid4()),
        "original_external_id": original_external_id,
        "reason": reason,
    }


def delivery_report(error: Optional[Exception], message: Any) -> None:
    if error is not None:
        print(f"failed to deliver message: {error}")
        return

    print(
        "delivered "
        f"topic={message.topic()} "
        f"partition={message.partition()} "
        f"offset={message.offset()} "
        f"key={message.key().decode('utf-8')} "
        f"value={message.value().decode('utf-8')}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Produce ledger reversal events to Kafka.")
    parser.add_argument("--bootstrap-server", default="localhost:9092")
    parser.add_argument("--topic", default="ledger-reversals")
    parser.add_argument("--original-external-id", required=True)
    parser.add_argument("--reason", default="TRANSFER_CANCELLED")
    parser.add_argument("--reversal-id")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    producer = Producer({"bootstrap.servers": args.bootstrap_server})
    event = build_event(args.original_external_id, args.reason, args.reversal_id)
    value = json.dumps(event, separators=(",", ":")).encode("utf-8")

    producer.produce(
        topic=args.topic,
        key=event["original_external_id"].encode("utf-8"),
        value=value,
        callback=delivery_report,
        headers={
            "event_name": "ledger.reversal_requested",
            "content_type": "application/json",
        },
    )
    producer.flush()


if __name__ == "__main__":
    main()
