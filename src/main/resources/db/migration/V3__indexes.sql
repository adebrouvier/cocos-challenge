-- Performance indexes (see README, "Schema changes").
--
-- Built CONCURRENTLY so that tables already in use (e.g. the populated production database) are not
-- locked against writes while the index builds. This requires:
--   * V3__indexes.sql.conf: CONCURRENTLY can't run inside a transaction.
--   * spring.flyway.postgresql.transactional-lock=false: otherwise Flyway holds an open transaction
--     during the migration and CREATE INDEX CONCURRENTLY waits for it forever.
--
-- If a concurrent build is interrupted it leaves an INVALID index behind and Flyway doesn't record the
-- migration. Each index is dropped first so that re-running the migration rebuilds it properly
-- (CREATE ... IF NOT EXISTS would silently keep the invalid index).
--
-- The instrument search has no index: it ORs a ticker prefix with a name substring (LIKE '%q%'),
-- which always needs a scan.

-- Portfolio and order submission load a user's orders filtered by status.
DROP INDEX CONCURRENTLY IF EXISTS idx_orders_user_status;
CREATE INDEX CONCURRENTLY idx_orders_user_status ON orders (userId, status);

-- Latest-price lookup: the most recent marketdata rows of each instrument. Not unique: the process that
-- loads prices may insert more than one row per day.
DROP INDEX CONCURRENTLY IF EXISTS idx_marketdata_instrument_date;
CREATE INDEX CONCURRENTLY idx_marketdata_instrument_date ON marketdata (instrumentId, date);
