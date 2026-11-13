"""Next-month spend forecasting per category.

Uses a least-squares linear trend over the last six months (scikit-learn/numpy),
falling back to a recency-weighted average when there are too few data points.
"""
from __future__ import annotations

from typing import Dict, List

import numpy as np

from .models import CategoryMonth

MIN_POINTS_FOR_TREND = 3


def _weighted_average(values: List[float]) -> float:
    tail = values[-3:]
    weights = np.arange(1, len(tail) + 1, dtype=float)
    return float(np.dot(tail, weights) / weights.sum())


def _fit_trend(values: List[float]) -> tuple[float, float]:
    """Returns (next_value, r_squared) for a linear fit over the series."""
    x = np.arange(len(values), dtype=float)
    y = np.asarray(values, dtype=float)
    slope, intercept = np.polyfit(x, y, 1)
    predicted = slope * x + intercept
    ss_res = float(np.sum((y - predicted) ** 2))
    ss_tot = float(np.sum((y - y.mean()) ** 2))
    r2 = 0.0 if ss_tot == 0 else max(0.0, 1.0 - ss_res / ss_tot)
    return float(slope * len(values) + intercept), r2


def build_forecast(months: List[CategoryMonth]) -> List[Dict]:
    if not months:
        return []

    ordered = sorted(months, key=lambda m: m.month)
    labels = [m.month for m in ordered]
    series: Dict[str, List[float]] = {}
    names: Dict[str, str] = {}

    for month in ordered:
        for category in month.categories:
            key = category.categoryId
            names[key] = category.name
            series.setdefault(key, [0.0] * len(labels))
            series[key][labels.index(month.month)] = float(category.total)

    forecast: List[Dict] = []
    for key, values in series.items():
        if sum(values) <= 0:
            continue
        last_month = values[-1]
        if len(values) >= MIN_POINTS_FOR_TREND and sum(1 for v in values if v > 0) >= MIN_POINTS_FOR_TREND:
            next_value, r2 = _fit_trend(values)
            confidence = min(0.95, max(0.35, 0.4 + 0.55 * r2))
            method = "linear_regression"
        else:
            next_value = _weighted_average(values)
            confidence = 0.4
            method = "weighted_average"

        next_value = max(0.0, round(next_value, 2))
        change = 0.0 if last_month == 0 else round((next_value - last_month) / last_month * 100, 1)
        trend = "rising" if change > 8 else "falling" if change < -8 else "stable"
        forecast.append({
            "categoryId": key,
            "category": names[key],
            "lastMonth": round(last_month, 2),
            "forecastNextMonth": next_value,
            "changePercent": change,
            "trend": trend,
            "confidence": round(confidence, 2),
            "method": method,
        })

    return sorted(forecast, key=lambda item: item["forecastNextMonth"], reverse=True)
