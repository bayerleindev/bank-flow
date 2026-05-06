import argparse
import json
import random
import time
import uuid
from typing import Any, Dict, Optional

from confluent_kafka import Producer


def random_account() -> str:
    return f"{random.randint(10000, 99999)}-{random.randint(0, 9)}"


def random_amount_cents() -> int:
    return random.randint(100, 50_000)


def build_event(
    source_owner_id: Optional[str] = None,
    destination_owner_id: Optional[str] = None,
) -> Dict[str, Any]:
    source_owner_id = source_owner_id or str(uuid.uuid4())
    destination_owner_id = destination_owner_id or str(uuid.uuid4())

    return {
        "transfer_id": str(uuid.uuid4()),
        "source_owner_id": source_owner_id,
        "source_account": random_account(),
        "destination_owner_id": destination_owner_id,
        "destination_account": random_account(),
        "amount_cents": random_amount_cents(),
        "currency": "BRL",
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
    parser = argparse.ArgumentParser(description="Produce transfer events to Kafka.")
    parser.add_argument("--bootstrap-server", default="localhost:9092")
    parser.add_argument("--topic", default="ledger-movements")
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--interval-seconds", type=float, default=0)
    parser.add_argument(
        "--source-owner-id",
        help="Use a fixed source owner_id as the Kafka message key.",
    )
    parser.add_argument(
        "--destination-owner-id",
        help="Use a fixed destination owner_id in the transfer event.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    producer = Producer({"bootstrap.servers": args.bootstrap_server})

    for index in range(args.count):
        event = build_event(args.source_owner_id, args.destination_owner_id)
        key = event["source_owner_id"]
        value = json.dumps(event, separators=(",", ":")).encode("utf-8")

        producer.produce(
            topic=args.topic,
            key=key.encode("utf-8"),
            value=value,
            callback=delivery_report,
            headers={
                "event_name": "transfer.posted",
                "content_type": "application/json",
            },
        )
        producer.poll(0)

        if args.interval_seconds > 0 and index < args.count - 1:
            time.sleep(args.interval_seconds)

    producer.flush()


if __name__ == "__main__":
    main()
