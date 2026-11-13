"""Description → category suggestions with a TF-IDF + logistic-regression model."""
from __future__ import annotations

from typing import Dict, List, Tuple

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

TRAINING_DATA: List[Tuple[str, str]] = [
    ("whole foods weekly groceries", "Groceries"),
    ("trader joes grocery run", "Groceries"),
    ("safeway supermarket", "Groceries"),
    ("costco bulk shopping", "Groceries"),
    ("aldi grocery store", "Groceries"),
    ("kroger produce", "Groceries"),
    ("starbucks latte", "Coffee"),
    ("blue bottle coffee", "Coffee"),
    ("dunkin donuts iced coffee", "Coffee"),
    ("local cafe espresso", "Coffee"),
    ("peets coffee beans", "Coffee"),
    ("chipotle burrito", "Restaurants"),
    ("pizza dinner with friends", "Restaurants"),
    ("sushi restaurant", "Restaurants"),
    ("uber eats delivery", "Restaurants"),
    ("doordash lunch", "Restaurants"),
    ("mcdonalds burger", "Restaurants"),
    ("monthly rent payment", "Rent"),
    ("apartment rent", "Rent"),
    ("landlord rent transfer", "Rent"),
    ("electricity and water bill", "Utilities"),
    ("comcast internet bill", "Utilities"),
    ("gas utility payment", "Utilities"),
    ("phone bill verizon", "Utilities"),
    ("uber ride downtown", "Transport"),
    ("lyft trip airport", "Transport"),
    ("metro transit card", "Transport"),
    ("shell gas station", "Fuel"),
    ("chevron fuel", "Fuel"),
    ("parking garage fee", "Transport"),
    ("amazon order electronics", "Shopping"),
    ("target household items", "Shopping"),
    ("nike running shoes", "Shopping"),
    ("apple store purchase", "Shopping"),
    ("netflix subscription", "Subscriptions"),
    ("spotify premium", "Subscriptions"),
    ("icloud storage plan", "Subscriptions"),
    ("youtube premium", "Subscriptions"),
    ("movie theater tickets", "Entertainment"),
    ("concert tickets", "Entertainment"),
    ("steam game purchase", "Entertainment"),
    ("pharmacy prescription", "Health"),
    ("doctor visit copay", "Health"),
    ("dentist appointment", "Health"),
    ("gym membership", "Fitness"),
    ("yoga class pass", "Fitness"),
    ("personal trainer session", "Fitness"),
    ("coursera course", "Education"),
    ("online bootcamp tuition", "Education"),
    ("textbook purchase", "Education"),
    ("car insurance premium", "Insurance"),
    ("health insurance payment", "Insurance"),
    ("renters insurance", "Insurance"),
    ("flight to tokyo", "Travel"),
    ("hotel booking paris", "Travel"),
    ("airbnb stay", "Travel"),
    ("birthday gift for mom", "Gifts"),
    ("wedding present", "Gifts"),
    ("salary direct deposit", "Salary"),
    ("payroll deposit", "Salary"),
    ("freelance invoice payment", "Freelance"),
    ("consulting client payment", "Freelance"),
    ("dividend payout", "Investments"),
    ("index fund contribution", "Investments"),
]

KEYWORDS: Dict[str, List[str]] = {
    "Groceries": ["grocery", "groceries", "supermarket", "whole foods", "trader joe", "safeway", "costco", "aldi"],
    "Coffee": ["coffee", "starbucks", "espresso", "latte", "cafe", "cappuccino"],
    "Restaurants": ["restaurant", "pizza", "sushi", "burger", "dinner", "lunch", "uber eats", "doordash"],
    "Rent": ["rent", "landlord", "apartment"],
    "Utilities": ["electric", "water bill", "internet", "utility", "verizon", "comcast", "phone bill"],
    "Transport": ["uber", "lyft", "metro", "transit", "parking", "train", "bus"],
    "Fuel": ["gas station", "fuel", "chevron", "shell", "petrol"],
    "Shopping": ["amazon", "target", "shopping", "clothes", "nike", "apple store"],
    "Subscriptions": ["subscription", "netflix", "spotify", "icloud", "premium", "membership"],
    "Entertainment": ["movie", "cinema", "concert", "game", "steam", "theater"],
    "Health": ["pharmacy", "doctor", "dentist", "hospital", "clinic"],
    "Fitness": ["gym", "yoga", "fitness", "trainer"],
    "Education": ["course", "tuition", "book", "bootcamp", "school"],
    "Insurance": ["insurance", "premium", "policy"],
    "Travel": ["flight", "hotel", "airbnb", "booking", "trip", "airline"],
    "Gifts": ["gift", "present", "wedding"],
    "Salary": ["salary", "payroll", "direct deposit"],
    "Freelance": ["freelance", "invoice", "consulting", "client"],
    "Investments": ["dividend", "index fund", "investment", "brokerage"],
}


def _build_pipeline() -> Pipeline:
    return Pipeline([
        ("vectorizer", TfidfVectorizer(lowercase=True, ngram_range=(1, 2), sublinear_tf=True)),
        ("classifier", LogisticRegression(max_iter=1000, C=4.0)),
    ])


_MODEL = _build_pipeline()
_MODEL.fit([text for text, _ in TRAINING_DATA], [label for _, label in TRAINING_DATA])


def _keyword_match(description: str, allowed: List[str]) -> Tuple[str | None, List[str]]:
    text = description.lower()
    scored = []
    for category, words in KEYWORDS.items():
        if allowed and category not in allowed:
            continue
        hits = sum(1 for word in words if word in text)
        if hits:
            scored.append((hits, category))
    scored.sort(reverse=True)
    if not scored:
        return None, []
    return scored[0][1], [category for _, category in scored[1:4]]


def categorize(description: str, allowed: List[str]) -> Tuple[str | None, float, List[str], str]:
    text = (description or "").strip()
    if not text:
        return None, 0.0, [], "empty"

    probabilities = _MODEL.predict_proba([text])[0]
    labels = list(_MODEL.classes_)
    ranked = sorted(zip(labels, probabilities), key=lambda pair: pair[1], reverse=True)
    if allowed:
        ranked = [pair for pair in ranked if pair[0] in allowed]

    if ranked and ranked[0][1] >= 0.18:
        category, confidence = ranked[0]
        alternatives = [label for label, _ in ranked[1:4]]
        return category, round(float(confidence), 2), alternatives, "tfidf_logreg"

    keyword_category, alternatives = _keyword_match(text, allowed)
    if keyword_category:
        return keyword_category, 0.55, alternatives, "keyword"
    if ranked:
        return ranked[0][0], round(float(ranked[0][1]), 2), [label for label, _ in ranked[1:4]], "tfidf_logreg"
    return None, 0.0, [], "no_match"
