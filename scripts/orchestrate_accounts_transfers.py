#!/usr/bin/env python3
import argparse
import json
import random
import time
import uuid
from datetime import datetime
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_SEED_DIGITAL_ACCOUNT_ID = "3f20291f-c0ba-4c8e-b0b2-7ff1cccb3833"


def generate_cpf():
    digits = [random.randint(0, 9) for _ in range(9)]
    first_check = cpf_check_digit(digits)
    second_check = cpf_check_digit(digits + [first_check])
    return "".join(str(digit) for digit in digits + [first_check, second_check])


def cpf_check_digit(digits):
    weight = len(digits) + 1
    total = sum(digit * (weight - index) for index, digit in enumerate(digits))
    remainder = (total * 10) % 11
    return 0 if remainder == 10 else remainder


def request_json(method, url, body=None, headers=None, timeout=10):
    data = None
    request_headers = {"Accept": "application/json"}
    if headers:
        request_headers.update(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"

    request = Request(url, data=data, headers=request_headers, method=method)
    try:
        with urlopen(request, timeout=timeout) as response:
            payload = response.read().decode("utf-8")
            if not payload:
                return {}
            return json.loads(payload)
    except HTTPError as error:
        payload = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed status={error.code} body={payload}") from error
    except URLError as error:
        raise RuntimeError(f"{method} {url} failed error={error.reason}") from error


def create_account(accounts_url, index):
    unique = uuid.uuid4().hex[:10]
    payload = {
        "fullName": f"Bank Flow User {index + 1}",
        "documentNumber": generate_cpf(),
        "email": f"bank-flow-user-{unique}@example.com",
        "motherName": f"Mother User {index + 1}",
        "socialName": f"Social User {index + 1}",
        "phoneNumber": f"1199{index + 1:07d}",
        "birthDate": "18-12-1996",
        "address": f"Rua Observability, {index + 1}",
        "isPoliticallyExposed": False,
    }
    idempotency_key = f"script-account-{unique}"
    account = request_json(
        "POST",
        f"{accounts_url}/accounts",
        payload,
        headers={"Idempotency-Key": idempotency_key},
    )
    print(
        "created account",
        account["digital_account_id"],
        account.get("branch"),
        account.get("account"),
        account.get("status"),
    )
    return account


def create_transfer(transfer_url, source_id, destination_id, amount_minor, description):
    payload = {
        "source_digital_account_id": source_id,
        "destination_digital_account_id": destination_id,
        "amount_minor": amount_minor,
        "currency": "BRL",
        "description": description,
    }
    idempotency_key = f"script-transfer-{uuid.uuid4()}"
    transfer = request_json(
        "POST",
        f"{transfer_url}/transfers",
        payload,
        headers={"Idempotency-Key": idempotency_key},
    )
    print(
        "created transfer",
        transfer["transfer_id"],
        source_id,
        "->",
        destination_id,
        amount_minor,
        transfer.get("status"),
    )
    return transfer


def get_transfer(transfer_url, transfer_id):
    return request_json("GET", f"{transfer_url}/transfers/{transfer_id}")


def simulate_psp_webhook(transfer_url, transfer, decline_rate, min_delay_seconds, max_delay_seconds):
    psp_payment_id = transfer.get("psp_payment_id")
    if not psp_payment_id:
        raise RuntimeError(f"transfer has no psp_payment_id: {transfer}")

    delay = random.uniform(min_delay_seconds, max_delay_seconds)
    status = "FAILED" if random.random() < decline_rate else "CONFIRMED"
    failure_reason = "SCRIPT_RANDOM_PSP_DECLINE" if status == "FAILED" else None

    print(
        "waiting before webhook",
        transfer["transfer_id"],
        f"{delay:.2f}s",
        status,
    )
    time.sleep(delay)

    response = request_json(
        "POST",
        f"{transfer_url}/webhooks/psp/transfers",
        {
            "psp_payment_id": psp_payment_id,
            "status": status,
            "failure_reason": failure_reason,
        },
    )
    print(
        "webhook delivered",
        transfer["transfer_id"],
        psp_payment_id,
        status,
        "->",
        response.get("status"),
    )
    return response


def simulate_psp_webhooks(transfer_url, transfers, decline_rate, min_delay_seconds, max_delay_seconds):
    return [
        simulate_psp_webhook(
            transfer_url,
            transfer,
            decline_rate,
            min_delay_seconds,
            max_delay_seconds,
        )
        for transfer in transfers
    ]


def wait_for_transfers(
    transfer_url,
    transfers,
    timeout_seconds,
    poll_interval_seconds,
    allow_failed=False,
):
    deadline = time.time() + timeout_seconds
    pending = {transfer["transfer_id"]: transfer for transfer in transfers}
    completed = {}

    while pending and time.time() < deadline:
        for transfer_id in list(pending):
            transfer = get_transfer(transfer_url, transfer_id)
            status = transfer.get("status")
            if status in {"COMPLETED", "FAILED"}:
                pending.pop(transfer_id)
                completed[transfer_id] = transfer
                print("transfer finished", transfer_id, status, transfer.get("failure_reason"))
        if pending:
            time.sleep(poll_interval_seconds)

    if pending:
        pending_ids = ", ".join(pending.keys())
        raise TimeoutError(f"transfers did not finish before timeout: {pending_ids}")

    failed = [transfer for transfer in completed.values() if transfer.get("status") == "FAILED"]
    if failed and not allow_failed:
        failures = ", ".join(
            f"{transfer['transfer_id']}:{transfer.get('failure_reason')}" for transfer in failed
        )
        raise RuntimeError(f"some transfers failed: {failures}")

    return list(completed.values())


def parse_args():
    parser = argparse.ArgumentParser(
        description="Create bank-flow accounts and orchestrate transfers between them."
    )
    parser.add_argument("--accounts-url", default="http://localhost:8084")
    parser.add_argument("--transfer-url", default="http://localhost:8083")
    parser.add_argument("--seed-digital-account-id", default=DEFAULT_SEED_DIGITAL_ACCOUNT_ID)
    parser.add_argument("--accounts", type=int, default=3)
    parser.add_argument(
        "--account-create-rate",
        type=float,
        default=0.2,
        help="Probability between 0 and 1 of creating a new account on each endless loop iteration.",
    )
    parser.add_argument(
        "--max-created-accounts",
        type=int,
        help="Stop creating new accounts after this total, including initial accounts.",
    )
    parser.add_argument("--seed-amount-minor", type=int, default=1000)
    parser.add_argument("--between-amount-minor", type=int, default=100)
    parser.add_argument("--between-min-amount-minor", type=int)
    parser.add_argument("--between-max-amount-minor", type=int)
    parser.add_argument("--ledger-wait-seconds", type=float, default=5)
    parser.add_argument("--loop-interval-min-seconds", type=float, default=1)
    parser.add_argument("--loop-interval-max-seconds", type=float, default=5)
    parser.add_argument(
        "--max-between-transfers",
        type=int,
        help="Stop after this many transfers between created accounts. Omit to run forever.",
    )
    parser.add_argument("--webhook-min-delay-seconds", type=float, default=1)
    parser.add_argument("--webhook-max-delay-seconds", type=float, default=8)
    parser.add_argument(
        "--seed-decline-rate",
        type=float,
        default=0.0,
        help="Probability between 0 and 1 that seed funding webhooks are declined.",
    )
    parser.add_argument(
        "--between-decline-rate",
        type=float,
        default=0.2,
        help="Probability between 0 and 1 that transfers between new accounts are declined.",
    )
    parser.add_argument("--poll-interval-seconds", type=float, default=2)
    parser.add_argument("--timeout-seconds", type=float, default=120)
    return parser.parse_args()


def main():
    args = parse_args()
    if args.accounts < 2:
        raise ValueError("--accounts must be at least 2")
    if not 0 <= args.account_create_rate <= 1:
        raise ValueError("--account-create-rate must be between 0 and 1")
    if args.max_created_accounts is not None and args.max_created_accounts < args.accounts:
        raise ValueError("--max-created-accounts must be greater than or equal to --accounts")
    if args.webhook_min_delay_seconds < 0:
        raise ValueError("--webhook-min-delay-seconds must be non-negative")
    if args.webhook_max_delay_seconds < args.webhook_min_delay_seconds:
        raise ValueError("--webhook-max-delay-seconds must be greater than or equal to min delay")
    if args.loop_interval_min_seconds < 0:
        raise ValueError("--loop-interval-min-seconds must be non-negative")
    if args.loop_interval_max_seconds < args.loop_interval_min_seconds:
        raise ValueError("--loop-interval-max-seconds must be greater than or equal to min interval")
    if args.max_between_transfers is not None and args.max_between_transfers < 1:
        raise ValueError("--max-between-transfers must be at least 1")
    if args.between_min_amount_minor is not None and args.between_min_amount_minor < 1:
        raise ValueError("--between-min-amount-minor must be positive")
    if args.between_max_amount_minor is not None and args.between_max_amount_minor < 1:
        raise ValueError("--between-max-amount-minor must be positive")
    if (
        args.between_min_amount_minor is not None
        and args.between_max_amount_minor is not None
        and args.between_max_amount_minor < args.between_min_amount_minor
    ):
        raise ValueError("--between-max-amount-minor must be greater than or equal to min amount")
    if not 0 <= args.seed_decline_rate <= 1:
        raise ValueError("--seed-decline-rate must be between 0 and 1")
    if not 0 <= args.between_decline_rate <= 1:
        raise ValueError("--between-decline-rate must be between 0 and 1")

    started_at = datetime.now().isoformat(timespec="seconds")
    print("starting orchestration", started_at)
    print("accounts_url", args.accounts_url)
    print("transfer_url", args.transfer_url)
    print("seed_digital_account_id", args.seed_digital_account_id)

    accounts = [create_account(args.accounts_url, index) for index in range(args.accounts)]
    digital_account_ids = [account["digital_account_id"] for account in accounts]

    print(f"waiting {args.ledger_wait_seconds}s for account-created events to reach ledger")
    time.sleep(args.ledger_wait_seconds)

    seed_transfers = [
        create_transfer(
            args.transfer_url,
            args.seed_digital_account_id,
            destination_id,
            args.seed_amount_minor,
            f"seed funding to {destination_id}",
        )
        for destination_id in digital_account_ids
    ]
    seed_transfers = simulate_psp_webhooks(
        args.transfer_url,
        seed_transfers,
        args.seed_decline_rate,
        args.webhook_min_delay_seconds,
        args.webhook_max_delay_seconds,
    )
    wait_for_transfers(
        args.transfer_url,
        seed_transfers,
        args.timeout_seconds,
        args.poll_interval_seconds,
    )

    print("starting endless transfers between created accounts")
    completed_between_count = 0
    while args.max_between_transfers is None or completed_between_count < args.max_between_transfers:
        maybe_create_and_fund_account(args, accounts, digital_account_ids)

        source_id, destination_id = random.sample(digital_account_ids, 2)
        amount_minor = random_between_amount(args)
        transfer = create_transfer(
            args.transfer_url,
            source_id,
            destination_id,
            amount_minor,
            f"continuous account transfer {completed_between_count + 1}",
        )
        transfer = simulate_psp_webhook(
            args.transfer_url,
            transfer,
            args.between_decline_rate,
            args.webhook_min_delay_seconds,
            args.webhook_max_delay_seconds,
        )
        wait_for_transfers(
            args.transfer_url,
            [transfer],
            args.timeout_seconds,
            args.poll_interval_seconds,
            allow_failed=True,
        )
        completed_between_count += 1
        interval = random.uniform(args.loop_interval_min_seconds, args.loop_interval_max_seconds)
        print("waiting before next transfer", f"{interval:.2f}s")
        time.sleep(interval)

    print("orchestration completed")
    print(json.dumps({"accounts": digital_account_ids}, indent=2))


def maybe_create_and_fund_account(args, accounts, digital_account_ids):
    if args.max_created_accounts is not None and len(accounts) >= args.max_created_accounts:
        return None
    if random.random() >= args.account_create_rate:
        return None

    account = create_account(args.accounts_url, len(accounts))
    accounts.append(account)
    digital_account_id = account["digital_account_id"]

    print(f"waiting {args.ledger_wait_seconds}s for new account-created event to reach ledger")
    time.sleep(args.ledger_wait_seconds)

    transfer = create_transfer(
        args.transfer_url,
        args.seed_digital_account_id,
        digital_account_id,
        args.seed_amount_minor,
        f"seed funding to new account {digital_account_id}",
    )
    transfer = simulate_psp_webhook(
        args.transfer_url,
        transfer,
        args.seed_decline_rate,
        args.webhook_min_delay_seconds,
        args.webhook_max_delay_seconds,
    )
    try:
        wait_for_transfers(
            args.transfer_url,
            [transfer],
            args.timeout_seconds,
            args.poll_interval_seconds,
        )
    except Exception:
        print("new account funding failed; account will not enter transfer pool", digital_account_id)
        raise

    digital_account_ids.append(digital_account_id)
    print("new account entered transfer pool", digital_account_id, "pool_size", len(digital_account_ids))
    return account


def random_between_amount(args):
    min_amount = args.between_min_amount_minor
    max_amount = args.between_max_amount_minor
    if min_amount is None and max_amount is None:
        return args.between_amount_minor
    if min_amount is None:
        min_amount = 1
    if max_amount is None:
        max_amount = min_amount
    return random.randint(min_amount, max_amount)


if __name__ == "__main__":
    main()
