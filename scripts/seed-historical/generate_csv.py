#!/usr/bin/env python3
from __future__ import annotations

import csv
import random
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 6:
        raise SystemExit(
            "Usage: generate_csv.py <anchor_path> <compare_path> <record_count> <trade_date> <run_key>"
        )

    anchor_path = Path(sys.argv[1])
    compare_path = Path(sys.argv[2])
    record_count = int(sys.argv[3])
    trade_date = sys.argv[4]
    run_key = sys.argv[5]

    rng = random.Random(run_key)

    currencies = ["USD", "EUR", "GBP", "SGD", "JPY"]
    products = ["Payments", "FX", "Securities", "Card", "Fees"]
    sub_products = ["Wire", "ACH", "Prime", "Retail", "Institutional"]
    entities = ["US", "EU", "APAC", "LATAM", "MEA"]

    # Always produce at least record_count rows for anchor, allow small mismatches for compare.
    anchor_rows: list[list[str]] = []
    compare_rows: list[list[str]] = []

    for idx in range(record_count):
        txn_id = f"{run_key}-{idx:05d}"
        amount = round(rng.uniform(250.0, 12500.0), 2)
        currency = rng.choice(currencies)
        product = rng.choice(products)
        sub_product = rng.choice(sub_products)
        entity = rng.choice(entities)

        anchor_rows.append([
            txn_id,
            f"{amount:.2f}",
            currency,
            trade_date,
            product,
            sub_product,
            entity,
        ])

        compare_amount = amount
        # Introduce mismatches in ~12% of records.
        if rng.random() < 0.12:
            drift = rng.uniform(-55.0, 55.0)
            compare_amount = round(compare_amount + drift, 2)

        if rng.random() < 0.06:
            # Omit the record entirely to simulate missing breaks.
            continue

        compare_rows.append([
            txn_id,
            f"{compare_amount:.2f}",
            currency,
            trade_date,
            product,
            sub_product,
            entity,
        ])

    # Guarantee compare side has at least 100 rows by re-adding some anchor entries if necessary.
    while len(compare_rows) < min(record_count, 100):
        idx = rng.randrange(len(anchor_rows))
        row = anchor_rows[idx][:]
        # apply slight variance to distinguish duplicate insertion
        row[1] = f"{float(row[1]) + rng.uniform(-10.0, 10.0):.2f}"
        compare_rows.append(row)

    header = [
        "transactionId",
        "amount",
        "currency",
        "tradeDate",
        "product",
        "subProduct",
        "entity",
    ]

    anchor_path.parent.mkdir(parents=True, exist_ok=True)
    compare_path.parent.mkdir(parents=True, exist_ok=True)

    with anchor_path.open("w", newline="") as anchor_file:
        writer = csv.writer(anchor_file)
        writer.writerow(header)
        writer.writerows(anchor_rows)

    with compare_path.open("w", newline="") as compare_file:
        writer = csv.writer(compare_file)
        writer.writerow(header)
        writer.writerows(compare_rows)


if __name__ == "__main__":
    main()
