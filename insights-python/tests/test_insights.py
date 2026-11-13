from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_forecast_picks_up_rising_trend():
    months = [
        {"month": "2026-07", "categories": [{"categoryId": "c1", "name": "Coffee", "total": 30}]},
        {"month": "2026-08", "categories": [{"categoryId": "c1", "name": "Coffee", "total": 40}]},
        {"month": "2026-09", "categories": [{"categoryId": "c1", "name": "Coffee", "total": 55}]},
        {"month": "2026-10", "categories": [{"categoryId": "c1", "name": "Coffee", "total": 70}]},
    ]
    response = client.post("/insights/overview",
                           json={"months": months, "transactions": [], "currency": "USD"})
    assert response.status_code == 200
    forecast = response.json()["forecast"]
    assert forecast[0]["category"] == "Coffee"
    assert forecast[0]["forecastNextMonth"] >= 70
    assert forecast[0]["trend"] in {"rising", "stable"}
    assert forecast[0]["method"] == "linear_regression"


def test_anomaly_detection_flags_outlier():
    amounts = [20, 22, 19, 24, 21, 420]
    transactions = [
        {
            "transactionId": f"t{i}",
            "amount": amount,
            "occurredOn": "2026-11-01",
            "description": "Laptop purchase" if amount > 100 else "Weekly shop",
            "category": "Shopping",
        }
        for i, amount in enumerate(amounts)
    ]
    response = client.post("/insights/overview",
                           json={"months": [], "transactions": transactions, "currency": "USD"})
    anomalies = response.json()["anomalies"]
    assert any(item["amount"] == 420 for item in anomalies)
    assert anomalies[0]["severity"] in {"medium", "high"}


def test_categorize_common_description():
    response = client.post("/insights/categorize",
                           json={"description": "starbucks iced latte", "categories": ["Coffee", "Groceries"]})
    body = response.json()
    assert body["category"] == "Coffee"
    assert 0 < body["confidence"] <= 1


def test_categorize_respects_allowed_categories():
    response = client.post("/insights/categorize",
                           json={"description": "uber ride home", "categories": ["Coffee", "Transport"]})
    assert response.json()["category"] == "Transport"
