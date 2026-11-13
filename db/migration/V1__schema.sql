-- FinSight schema (PostgreSQL 14+)
-- UUID primary keys, referential integrity, check constraints and covering indexes.

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    base_currency   CHAR(3)      NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            VARCHAR(80) NOT NULL,
    type            VARCHAR(20) NOT NULL DEFAULT 'CHECKING',
    currency        CHAR(3)     NOT NULL DEFAULT 'USD',
    initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
    archived        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT accounts_type_check CHECK (type IN ('CHECKING', 'SAVINGS', 'CREDIT', 'CASH', 'INVESTMENT')),
    CONSTRAINT accounts_user_name_unique UNIQUE (user_id, name)
);

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    kind        VARCHAR(10) NOT NULL,
    color       CHAR(7)     NOT NULL DEFAULT '#6366f1',
    icon        VARCHAR(40) NOT NULL DEFAULT 'tag',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT categories_kind_check CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT categories_user_name_unique UNIQUE (user_id, name, kind)
);

CREATE TABLE recurring_transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id    UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category_id   UUID REFERENCES categories (id) ON DELETE SET NULL,
    type          VARCHAR(10) NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency      CHAR(3) NOT NULL DEFAULT 'USD',
    exchange_rate NUMERIC(14, 6) NOT NULL DEFAULT 1 CHECK (exchange_rate > 0),
    description   VARCHAR(255) NOT NULL,
    frequency     VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',
    next_run_date DATE NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT recurring_type_check CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT recurring_frequency_check CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY'))
);

CREATE TABLE transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id    UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category_id   UUID REFERENCES categories (id) ON DELETE SET NULL,
    recurring_id  UUID REFERENCES recurring_transactions (id) ON DELETE SET NULL,
    type          VARCHAR(10) NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency      CHAR(3) NOT NULL DEFAULT 'USD',
    exchange_rate NUMERIC(14, 6) NOT NULL DEFAULT 1 CHECK (exchange_rate > 0),
    amount_base   NUMERIC(14, 2) NOT NULL CHECK (amount_base > 0),
    description   VARCHAR(255) NOT NULL,
    merchant      VARCHAR(120),
    notes         TEXT,
    occurred_on   DATE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT transactions_type_check CHECK (type IN ('INCOME', 'EXPENSE'))
);

CREATE TABLE budgets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id  UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT budgets_period_first_of_month CHECK (period_start = date_trunc('month', period_start)::date),
    CONSTRAINT budgets_unique UNIQUE (user_id, category_id, period_start)
);

CREATE TABLE goals (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    target_amount  NUMERIC(14, 2) NOT NULL CHECK (target_amount > 0),
    current_amount NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
    target_date    DATE,
    color          CHAR(7) NOT NULL DEFAULT '#10b981',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user ON accounts (user_id) WHERE archived = FALSE;
CREATE INDEX idx_categories_user ON categories (user_id, kind);
CREATE INDEX idx_transactions_user_date ON transactions (user_id, occurred_on DESC);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category_id);
CREATE INDEX idx_transactions_user_type_date ON transactions (user_id, type, occurred_on DESC);
CREATE INDEX idx_transactions_account ON transactions (account_id);
CREATE INDEX idx_transactions_description_trgm ON transactions USING GIN (to_tsvector('simple', description));
CREATE INDEX idx_budgets_user_period ON budgets (user_id, period_start);
CREATE INDEX idx_goals_user ON goals (user_id);
CREATE INDEX idx_recurring_due ON recurring_transactions (next_run_date) WHERE active = TRUE;

-- Reporting view: monthly spending per category (expenses only), used by dashboard & insights.
CREATE OR REPLACE VIEW monthly_spending_summary AS
SELECT t.user_id,
       date_trunc('month', t.occurred_on)::date AS month,
       t.category_id,
       c.name  AS category_name,
       c.color,
       SUM(t.amount_base) AS total_spent,
       COUNT(*)           AS transaction_count
FROM transactions t
         LEFT JOIN categories c ON c.id = t.category_id
WHERE t.type = 'EXPENSE'
GROUP BY 1, 2, 3, 4, 5;

-- Reporting view: monthly income vs expense per user.
CREATE OR REPLACE VIEW monthly_cashflow AS
SELECT user_id,
       date_trunc('month', occurred_on)::date AS month,
       SUM(CASE WHEN type = 'INCOME' THEN amount_base ELSE 0 END)  AS income,
       SUM(CASE WHEN type = 'EXPENSE' THEN amount_base ELSE 0 END) AS expenses
FROM transactions
GROUP BY 1, 2;
