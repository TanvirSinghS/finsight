# Database

Canonical PostgreSQL schema and seed data. These files are used directly by Flyway
(the Spring Boot build copies `db/migration` into the jar), and can also be applied manually:

```bash
psql "$DATABASE_URL" -f db/migration/V1__schema.sql
psql "$DATABASE_URL" -f db/migration/V2__seed.sql
```

| File | Purpose |
| --- | --- |
| `migration/V1__schema.sql` | Tables, constraints, indexes and the `monthly_spending_summary` / `monthly_cashflow` views |
| `migration/V2__seed.sql` | Demo user `demo@finsight.app` / `Demo@1234`, 12 months of transactions, budgets, goals, recurring payments |

The seed is idempotent — it exits immediately if the demo user already exists.
