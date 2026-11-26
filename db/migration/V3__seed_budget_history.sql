-- Backfill six months of demo budgets so the Budget history view has data.
-- Budgets are derived from the previous month's actual spend, with the same
-- intentionally tight categories as the current-month seed, and existing rows
-- are left untouched.
DO $$
DECLARE
    v_user   UUID;
    v_month  DATE;
    i        INT;
    c        RECORD;
    v_budget NUMERIC;
BEGIN
    SELECT id INTO v_user FROM users WHERE email = 'demo@finsight.app';
    IF v_user IS NULL THEN
        RETURN;
    END IF;

    FOR i IN 1..6 LOOP
        v_month := (date_trunc('month', CURRENT_DATE) - make_interval(months => i))::date;

        FOR c IN SELECT ct.id, ct.name,
                        COALESCE((SELECT SUM(t.amount_base) FROM transactions t
                                  WHERE t.user_id = v_user AND t.category_id = ct.id
                                    AND t.type = 'EXPENSE'
                                    AND t.occurred_on >= (v_month - interval '1 month')::date
                                    AND t.occurred_on < v_month), 0) AS prev_total
                 FROM categories ct
                 WHERE ct.user_id = v_user AND ct.kind = 'EXPENSE'
                   AND ct.name IN ('Groceries', 'Restaurants', 'Coffee', 'Transport', 'Shopping',
                                   'Entertainment', 'Utilities', 'Subscriptions')
        LOOP
            v_budget := round(c.prev_total * (CASE WHEN c.name IN ('Groceries', 'Restaurants', 'Shopping')
                                                   THEN 0.92 ELSE 1.0 END), 2);
            IF v_budget > 0 THEN
                INSERT INTO budgets (user_id, category_id, period_start, amount)
                VALUES (v_user, c.id, v_month, v_budget)
                ON CONFLICT (user_id, category_id, period_start) DO NOTHING;
            END IF;
        END LOOP;
    END LOOP;
END $$;
