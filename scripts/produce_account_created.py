import argparse
import json
import random
import time
import uuid
from typing import Any, Dict, Optional

from confluent_kafka import Producer


DEFAULT_BRANCHES = ["0001", "0002", "0003", "0004"]
DEFAULT_CURRENCIES = ["BRL"]


def build_event(owner_id: Optional[str] = None) -> Dict[str, Any]:
    account_number = f"{random.randint(10000, 99999)}-{random.randint(0, 9)}"

    return {
        "owner_id": owner_id or str(uuid.uuid4()),
        "branch": random.choice(DEFAULT_BRANCHES),
        "account": account_number,
        "currency": random.choice(DEFAULT_CURRENCIES),
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
        f"key={message.key().decode('utf-8')}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Produce account-created events to Kafka."
    )
    parser.add_argument("--bootstrap-server", default="localhost:9092")
    parser.add_argument("--topic", default="account-created")
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--interval-seconds", type=float, default=0)
    parser.add_argument(
        "--owner-id",
        help="Use a fixed owner_id as the Kafka message key for every generated event.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    producer = Producer({"bootstrap.servers": args.bootstrap_server})

    for index in range(args.count):
        event = build_event(args.owner_id)
        key = event["owner_id"]
        value = json.dumps(event, separators=(",", ":")).encode("utf-8")

        producer.produce(
            topic=args.topic,
            key=key.encode("utf-8"),
            value=value,
            callback=delivery_report,
            headers={
                "event_name": "account.created",
                "content_type": "application/json",
            },
        )
        producer.poll(0)

        if args.interval_seconds > 0 and index < args.count - 1:
            time.sleep(args.interval_seconds)

    producer.flush()


if __name__ == "__main__":
    main()
