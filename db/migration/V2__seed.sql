-- Demo seed for FinSight. Password for demo@finsight.app is Demo@1234 (BCrypt).
-- Idempotent: exits early when the demo user already exists.
DO $$
DECLARE
    v_user    UUID;
    v_check   UUID;
    v_savings UUID;
    v_credit  UUID;
    v_rent_cat UUID;
    c         RECORD;
    m         DATE;
    i         INT;
    k         INT;
    n_tx      INT;
    base      NUMERIC;
    monthly   NUMERIC;
    amt       NUMERIC;
    tx_cur    CHAR(3);
    tx_rate   NUMERIC;
    tx_day    INT;
    v_desc    VARCHAR;
    v_merch   VARCHAR;
    v_acct    UUID;
    v_budget_cat UUID;
    v_budget_amt NUMERIC;
BEGIN
    SELECT id INTO v_user FROM users WHERE email = 'demo@finsight.app';
    IF v_user IS NOT NULL THEN
        RETURN;
    END IF;

    INSERT INTO users (email, password_hash, full_name, base_currency)
    VALUES ('demo@finsight.app',
            '$2b$10$6bRuyZxyqTmNxAqEh6qvcOY1gUFAiwx5jOfz.8PK4oTggd2LspKkm',
            'Alex Morgan', 'USD')
    RETURNING id INTO v_user;

    INSERT INTO accounts (user_id, name, type, currency, initial_balance) VALUES
        (v_user, 'Everyday Checking', 'CHECKING', 'USD', 2400),
        (v_user, 'High-Yield Savings', 'SAVINGS', 'USD', 14800),
        (v_user, 'Sapphire Credit Card', 'CREDIT', 'USD', 0),
        (v_user, 'Travel Wallet', 'CASH', 'EUR', 350);

    SELECT id INTO v_check FROM accounts WHERE user_id = v_user AND name = 'Everyday Checking';
    SELECT id INTO v_savings FROM accounts WHERE user_id = v_user AND name = 'High-Yield Savings';
    SELECT id INTO v_credit FROM accounts WHERE user_id = v_user AND name = 'Sapphire Credit Card';

    INSERT INTO categories (user_id, name, kind, color, icon) VALUES
        (v_user, 'Groceries', 'EXPENSE', '#22c55e', 'shopping-cart'),
        (v_user, 'Restaurants', 'EXPENSE', '#f97316', 'utensils'),
        (v_user, 'Coffee', 'EXPENSE', '#a16207', 'coffee'),
        (v_user, 'Rent', 'EXPENSE', '#ef4444', 'home'),
        (v_user, 'Utilities', 'EXPENSE', '#0ea5e9', 'zap'),
        (v_user, 'Internet', 'EXPENSE', '#6366f1', 'wifi'),
        (v_user, 'Transport', 'EXPENSE', '#14b8a6', 'bus'),
        (v_user, 'Fuel', 'EXPENSE', '#64748b', 'fuel'),
        (v_user, 'Shopping', 'EXPENSE', '#ec4899', 'shopping-bag'),
        (v_user, 'Entertainment', 'EXPENSE', '#8b5cf6', 'film'),
        (v_user, 'Health', 'EXPENSE', '#10b981', 'heart-pulse'),
        (v_user, 'Fitness', 'EXPENSE', '#84cc16', 'dumbbell'),
        (v_user, 'Travel', 'EXPENSE', '#06b6d4', 'plane'),
        (v_user, 'Subscriptions', 'EXPENSE', '#f43f5e', 'repeat'),
        (v_user, 'Education', 'EXPENSE', '#3b82f6', 'book-open'),
        (v_user, 'Insurance', 'EXPENSE', '#78716c', 'shield'),
        (v_user, 'Gifts', 'EXPENSE', '#d946ef', 'gift'),
        (v_user, 'Misc', 'EXPENSE', '#94a3b8', 'tag'),
        (v_user, 'Salary', 'INCOME', '#059669', 'banknote'),
        (v_user, 'Freelance', 'INCOME', '#0d9488', 'laptop'),
        (v_user, 'Investments', 'INCOME', '#7c3aed', 'trending-up');

    SELECT id INTO v_rent_cat FROM categories WHERE user_id = v_user AND name = 'Rent';

    -- 12 months of expenses with deterministic pseudo-random variation.
    FOR i IN REVERSE 11..0 LOOP
        m := date_trunc('month', CURRENT_DATE)::date - (i || ' months')::interval;
        FOR c IN SELECT id, name FROM categories WHERE user_id = v_user AND kind = 'EXPENSE' LOOP
            base := CASE c.name
                        WHEN 'Rent' THEN 1650 WHEN 'Groceries' THEN 520 WHEN 'Restaurants' THEN 265
                        WHEN 'Coffee' THEN 78 WHEN 'Utilities' THEN 185 WHEN 'Internet' THEN 65
                        WHEN 'Transport' THEN 120 WHEN 'Fuel' THEN 140 WHEN 'Shopping' THEN 290
                        WHEN 'Entertainment' THEN 120 WHEN 'Health' THEN 95 WHEN 'Fitness' THEN 45
                        WHEN 'Travel' THEN 180 WHEN 'Subscriptions' THEN 58 WHEN 'Education' THEN 80
                        WHEN 'Insurance' THEN 120 WHEN 'Gifts' THEN 65 ELSE 75 END;
            monthly := base * (0.85 + (abs(hashtext(c.name || i::text)::bigint) % 30) / 100.0);
            IF i = 0 THEN
                monthly := monthly * (EXTRACT(DAY FROM CURRENT_DATE)::numeric / 30.0);
            END IF;
            n_tx := 2 + (abs(hashtext(c.name || i::text || 'n')::bigint) % 3);
            FOR k IN 1..n_tx LOOP
                amt := round(monthly / n_tx * (0.7 + (abs(hashtext(c.name || i::text || k::text)::bigint) % 60) / 100.0), 2);
                tx_day := 1 + (abs(hashtext(c.name || i::text || k::text || 'd')::bigint) % 26);
                IF i = 0 THEN
                    tx_day := LEAST(tx_day, EXTRACT(DAY FROM CURRENT_DATE)::int);
                END IF;

                v_desc := CASE c.name
                    WHEN 'Rent' THEN 'Monthly rent'
                    WHEN 'Groceries' THEN (ARRAY['Whole Foods weekly shop','Trader Joe''s groceries','Costco bulk run','Farmers market'])[1 + (k + i) % 4]
                    WHEN 'Restaurants' THEN (ARRAY['Dinner at Nobu','Taco night','Pasta bar lunch','Sushi with friends'])[1 + (k + i) % 4]
                    WHEN 'Coffee' THEN (ARRAY['Blue Bottle latte','Starbucks cold brew','Local espresso'])[1 + (k + i) % 3]
                    WHEN 'Utilities' THEN 'Electricity & water bill'
                    WHEN 'Internet' THEN 'Fiber internet'
                    WHEN 'Transport' THEN (ARRAY['Metro card top-up','Uber ride','Bus pass'])[1 + (k + i) % 3]
                    WHEN 'Fuel' THEN 'Shell gas station'
                    WHEN 'Shopping' THEN (ARRAY['Amazon order','Nike sneakers','Home goods','Zara clothing'])[1 + (k + i) % 4]
                    WHEN 'Entertainment' THEN (ARRAY['Movie tickets','Concert tickets','Steam games'])[1 + (k + i) % 3]
                    WHEN 'Health' THEN 'Pharmacy prescription'
                    WHEN 'Fitness' THEN 'Gym membership'
                    WHEN 'Travel' THEN (ARRAY['Weekend train trip','Flight booking','Hotel stay'])[1 + (k + i) % 3]
                    WHEN 'Subscriptions' THEN (ARRAY['Netflix','Spotify','iCloud storage'])[1 + (k + i) % 3]
                    WHEN 'Education' THEN 'Online course'
                    WHEN 'Insurance' THEN 'Auto insurance premium'
                    WHEN 'Gifts' THEN 'Birthday gift'
                    ELSE 'Miscellaneous purchase' END;

                v_merch := CASE c.name
                    WHEN 'Groceries' THEN 'Whole Foods' WHEN 'Restaurants' THEN 'Local Bistro'
                    WHEN 'Coffee' THEN 'Blue Bottle' WHEN 'Rent' THEN 'Parkview Apartments'
                    WHEN 'Shopping' THEN 'Amazon' WHEN 'Fuel' THEN 'Shell'
                    WHEN 'Travel' THEN 'Amtrak' ELSE NULL END;

                v_acct := CASE WHEN c.name IN ('Rent', 'Utilities', 'Insurance', 'Internet') THEN v_check
                               WHEN c.name = 'Travel' THEN v_credit
                               ELSE v_credit END;

                tx_cur := 'USD';
                tx_rate := 1;
                IF c.name = 'Travel' AND i IN (2, 5) AND k = 1 THEN
                    tx_cur := 'EUR';
                    tx_rate := 1.085;
                END IF;

                INSERT INTO transactions (user_id, account_id, category_id, type, amount, currency,
                                          exchange_rate, amount_base, description, merchant, occurred_on)
                VALUES (v_user, v_acct, c.id, 'EXPENSE', amt, tx_cur, tx_rate,
                        round(amt * tx_rate, 2), v_desc, v_merch, m + (tx_day - 1));
            END LOOP;
        END LOOP;
    END LOOP;

    -- Income: salary every month, freelance sporadically, one investment payout.
    FOR i IN REVERSE 11..0 LOOP
        m := date_trunc('month', CURRENT_DATE)::date - (i || ' months')::interval;
        INSERT INTO transactions (user_id, account_id, category_id, type, amount, currency, exchange_rate,
                                  amount_base, description, merchant, occurred_on)
        VALUES (v_user, v_check, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Salary'),
                'INCOME', 5200, 'USD', 1, 5200, 'Monthly salary', 'Acme Corp', m);
        IF i % 3 = 0 THEN
            INSERT INTO transactions (user_id, account_id, category_id, type, amount, currency, exchange_rate,
                                      amount_base, description, merchant, occurred_on)
            VALUES (v_user, v_check, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Freelance'),
                    'INCOME', 350 + (abs(hashtext('free' || i::text)::bigint) % 500), 'USD', 1,
                    350 + (abs(hashtext('free' || i::text)::bigint) % 500),
                    'Freelance design project', 'Upwork', m + 14);
        END IF;
    END LOOP;

    -- A clear outlier so anomaly detection has something to find.
    INSERT INTO transactions (user_id, account_id, category_id, type, amount, currency, exchange_rate,
                              amount_base, description, merchant, occurred_on)
    VALUES (v_user, v_credit, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Shopping'),
            'EXPENSE', 2399.00, 'USD', 1, 2399.00, 'MacBook Pro purchase', 'Apple Store',
            CURRENT_DATE - 9);

    -- Current-month budgets derived from last month's actual spend (some intentionally tight).
    FOR c IN SELECT ct.id, ct.name,
                    COALESCE((SELECT SUM(t.amount_base) FROM transactions t
                              WHERE t.user_id = v_user AND t.category_id = ct.id AND t.type = 'EXPENSE'
                                AND t.occurred_on >= date_trunc('month', CURRENT_DATE) - interval '1 month'
                                AND t.occurred_on < date_trunc('month', CURRENT_DATE)), 0) AS prev_total
             FROM categories ct
             WHERE ct.user_id = v_user AND ct.kind = 'EXPENSE'
               AND ct.name IN ('Groceries', 'Restaurants', 'Coffee', 'Transport', 'Shopping',
                               'Entertainment', 'Utilities', 'Subscriptions')
    LOOP
        v_budget_amt := round(c.prev_total * (CASE WHEN c.name IN ('Groceries', 'Restaurants', 'Shopping') THEN 0.92 ELSE 1.0 END), 2);
        IF v_budget_amt > 0 THEN
            INSERT INTO budgets (user_id, category_id, period_start, amount)
            VALUES (v_user, c.id, date_trunc('month', CURRENT_DATE)::date, v_budget_amt);
        END IF;
    END LOOP;

    INSERT INTO goals (user_id, name, target_amount, current_amount, target_date, color) VALUES
        (v_user, 'Emergency Fund', 20000, 14800, CURRENT_DATE + 300, '#10b981'),
        (v_user, 'Japan Trip', 6000, 2350, CURRENT_DATE + 180, '#06b6d4'),
        (v_user, 'New Laptop', 2500, 2400, CURRENT_DATE + 45, '#8b5cf6'),
        (v_user, 'Home Down Payment', 60000, 12500, CURRENT_DATE + 900, '#f59e0b');

    INSERT INTO recurring_transactions (user_id, account_id, category_id, type, amount, currency,
                                        exchange_rate, description, frequency, next_run_date, active)
    VALUES
        (v_user, v_check, v_rent_cat, 'EXPENSE', 1650, 'USD', 1, 'Monthly rent',
         'MONTHLY', date_trunc('month', CURRENT_DATE + interval '1 month')::date, TRUE),
        (v_user, v_check, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Internet'),
         'EXPENSE', 65, 'USD', 1, 'Fiber internet', 'MONTHLY',
         date_trunc('month', CURRENT_DATE + interval '1 month')::date + 4, TRUE),
        (v_user, v_credit, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Subscriptions'),
         'EXPENSE', 15.49, 'USD', 1, 'Netflix subscription', 'MONTHLY',
         date_trunc('month', CURRENT_DATE + interval '1 month')::date + 9, TRUE),
        (v_user, v_credit, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Fitness'),
         'EXPENSE', 45, 'USD', 1, 'Gym membership', 'MONTHLY',
         date_trunc('month', CURRENT_DATE + interval '1 month')::date + 2, TRUE),
        (v_user, v_check, (SELECT id FROM categories WHERE user_id = v_user AND name = 'Salary'),
         'INCOME', 5200, 'USD', 1, 'Monthly salary', 'MONTHLY',
         date_trunc('month', CURRENT_DATE + interval '1 month')::date, TRUE),
        (v_user, v_check, NULL, 'EXPENSE', 22, 'USD', 1, 'Coffee beans subscription', 'WEEKLY',
         CURRENT_DATE + 2, TRUE);
END $$;
