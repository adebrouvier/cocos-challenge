-- Integrity constraints on top of the challenge's provided schema (see README, "Schema changes").
-- Runs in a single transaction: if existing data violates any constraint, the whole migration
-- rolls back and the database is left exactly as it was (the application then fails to start).

-- The API looks users up by account number, which must identify a single user.
ALTER TABLE users ADD CONSTRAINT uq_users_accountnumber UNIQUE (accountNumber);

-- The application maps these columns to enums; any other value would break reads.
ALTER TABLE orders ADD CONSTRAINT chk_orders_side   CHECK (side   IN ('BUY', 'SELL', 'CASH_IN', 'CASH_OUT'));
ALTER TABLE orders ADD CONSTRAINT chk_orders_type   CHECK (type   IN ('MARKET', 'LIMIT'));
ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED'));
ALTER TABLE orders ADD CONSTRAINT chk_orders_size   CHECK (size >= 0);

-- Every price row belongs to an instrument and a day, and carries a usable price. This lets the
-- application rely on it instead of working around missing or zero values: a feed that writes a
-- bad row gets an error at insert time instead.
-- previousClose may be missing (an instrument has none on its first trading day), but not zero or negative.
ALTER TABLE marketdata ALTER COLUMN instrumentId SET NOT NULL;
ALTER TABLE marketdata ALTER COLUMN date         SET NOT NULL;
ALTER TABLE marketdata ALTER COLUMN close        SET NOT NULL;
ALTER TABLE marketdata ADD CONSTRAINT chk_marketdata_close_positive         CHECK (close > 0);
ALTER TABLE marketdata ADD CONSTRAINT chk_marketdata_previousclose_positive CHECK (previousClose > 0);
