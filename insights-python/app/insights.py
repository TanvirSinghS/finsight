"""Plain-language insights generated from the monthly aggregates."""
from __future__ import annotations

from typing import Dict, List

from .models import CategoryMonth


def _format_money(value: float, currency: str) -> str:
    return f"{value:,.0f} {currency}"


def build_insights(months: List[CategoryMonth], forecast: List[Dict], currency: str) -> List[Dict]:
    if len(months) < 2:
        return [{
            "text": "Add a couple of months of transactions and FinSight will start surfacing spending trends.",
            "type": "info",
            "severity": "low",
            "category": None,
        }]

    ordered = sorted(months, key=lambda m: m.month)
    previous, last = ordered[-2], ordered[-1]
    previous_totals = {c.name: c.total for c in previous.categories}
    last_totals = {c.name: c.total for c in last.categories}

    results: List[Dict] = []

    changes = []
    for name, total in last_totals.items():
        before = previous_totals.get(name, 0.0)
        if before <= 0:
            continue
        delta = total - before
        percent = delta / before * 100
        if abs(percent) >= 20 and abs(delta) >= 20:
            changes.append((abs(delta), name, total, before, delta, percent))
    changes.sort(reverse=True)

    for _, name, total, before, delta, percent in changes[:3]:
        if delta > 0:
            results.append({
                "text": (f"{name} jumped {percent:.0f}% month over month "
                         f"({_format_money(before, currency)} → {_format_money(total, currency)})."),
                "type": "warning",
                "severity": "high" if percent >= 60 else "medium",
                "category": name,
            })
        else:
            results.append({
                "text": (f"Nice work — {name} spending fell {abs(percent):.0f}% "
                         f"({_format_money(before, currency)} → {_format_money(total, currency)})."),
                "type": "positive",
                "severity": "low",
                "category": name,
            })

    last_total = sum(last_totals.values())
    if last_total > 0:
        top_name, top_value = max(last_totals.items(), key=lambda pair: pair[1])
        share = top_value / last_total * 100
        results.append({
            "text": f"{top_name} is your largest category this month at {share:.0f}% of spending.",
            "type": "info",
            "severity": "medium" if share >= 40 else "low",
            "category": top_name,
        })

    history = [sum(c.total for c in month.categories) for month in ordered[:-1]]
    if history:
        average = sum(history) / len(history)
        if average > 0:
            percent = (last_total - average) / average * 100
            direction = "above" if percent >= 0 else "below"
            results.append({
                "text": (f"This month's spending is {abs(percent):.0f}% {direction} your "
                         f"{len(history)}-month average of {_format_money(average, currency)}."),
                "type": "trend",
                "severity": "high" if percent >= 30 else "low",
                "category": None,
            })

    if forecast:
        expected = sum(item["forecastNextMonth"] for item in forecast)
        rising = [item for item in forecast if item["trend"] == "rising"][:2]
        text = f"Next month's projected spend is about {_format_money(expected, currency)}."
        if rising:
            text += " Watch " + " and ".join(item["category"] for item in rising) + "."
        results.append({"text": text, "type": "forecast", "severity": "low", "category": None})

    return results[:6]
