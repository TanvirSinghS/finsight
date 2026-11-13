"""Unusual-spend detection using per-category z-scores over the last six months."""
from __future__ import annotations

import statistics
from typing import Dict, List

from .models import TransactionRow

Z_THRESHOLD = 2.2
MIN_CATEGORY_SAMPLES = 4


def detect_anomalies(transactions: List[TransactionRow]) -> List[Dict]:
    grouped: Dict[str, List[TransactionRow]] = {}
    for row in transactions:
        grouped.setdefault(row.category, []).append(row)

    results: List[Dict] = []
    for category, rows in grouped.items():
        amounts = [float(r.amount) for r in rows]
        if len(amounts) < MIN_CATEGORY_SAMPLES:
            continue
        mean = statistics.fmean(amounts)
        stdev = statistics.pstdev(amounts)
        if stdev <= 0:
            continue
        for row in rows:
            z = (float(row.amount) - mean) / stdev
            if z >= Z_THRESHOLD and float(row.amount) > mean * 1.5:
                severity = "high" if z >= 3 else "medium"
                results.append({
                    "transactionId": row.transactionId,
                    "description": row.description,
                    "amount": round(float(row.amount), 2),
                    "occurredOn": row.occurredOn,
                    "category": category,
                    "reason": (
                        f"{category} spend of {row.amount:,.2f} is {z:.1f} standard deviations "
                        f"above your usual {mean:,.2f}."
                    ),
                    "severity": severity,
                    "zScore": round(z, 2),
                })

    results.sort(key=lambda item: item["zScore"], reverse=True)
    return results[:10]
