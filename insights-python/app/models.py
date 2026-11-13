from typing import List

from pydantic import BaseModel, Field


class CategoryTotal(BaseModel):
    categoryId: str
    name: str
    total: float


class CategoryMonth(BaseModel):
    month: str
    categories: List[CategoryTotal] = Field(default_factory=list)


class TransactionRow(BaseModel):
    transactionId: str
    amount: float
    occurredOn: str
    description: str
    category: str = "Uncategorized"


class OverviewRequest(BaseModel):
    months: List[CategoryMonth] = Field(default_factory=list)
    transactions: List[TransactionRow] = Field(default_factory=list)
    currency: str = "USD"


class CategorizeRequest(BaseModel):
    description: str
    categories: List[str] = Field(default_factory=list)
