from datetime import datetime, timezone

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .anomalies import detect_anomalies
from .categorizer import categorize
from .forecasting import build_forecast
from .insights import build_insights
from .models import CategorizeRequest, OverviewRequest

app = FastAPI(
    title="FinSight Insights",
    version="1.0.0",
    description=(
        "Python analytics microservice behind FinSight: per-category spend forecasting, "
        "z-score anomaly detection, plain-language insights and ML auto-categorization."
    ),
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", tags=["Health"])
def health() -> dict:
    return {"status": "UP", "service": "finsight-insights"}


@app.post("/insights/overview", tags=["Insights"])
def overview(payload: OverviewRequest) -> dict:
    forecast = build_forecast(payload.months)
    anomalies = detect_anomalies(payload.transactions)
    insights = build_insights(payload.months, forecast, payload.currency)
    return {
        "forecast": forecast,
        "anomalies": anomalies,
        "insights": insights,
        "meta": {
            "model": "sklearn linear regression + z-score + tfidf/logreg",
            "monthsAnalysed": len(payload.months),
            "transactionsAnalysed": len(payload.transactions),
            "generatedAt": datetime.now(timezone.utc).isoformat(),
        },
    }


@app.post("/insights/categorize", tags=["Insights"])
def categorize_transaction(payload: CategorizeRequest) -> dict:
    category, confidence, alternatives, method = categorize(payload.description, payload.categories)
    return {
        "category": category,
        "confidence": confidence,
        "alternatives": alternatives,
        "method": method,
    }
