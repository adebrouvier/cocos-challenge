package ar.com.arieldebrouvier.cocos.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A user's cash and positions, derived by replaying their FILLED and NEW orders in chronological order.
 *
 * <ul>
 *   <li>FILLED orders move cash and shares.</li>
 *   <li>NEW (pending LIMIT) orders don't move anything, but they <em>reserve</em> what they would
 *       consume: cash for a BUY, shares for a SELL. Reserved amounts can't be used by other orders.</li>
 * </ul>
 */
public final class AccountState {

    private static final int COST_SCALE = 6;

    private BigDecimal cashBalance = BigDecimal.ZERO;

    /** Cash committed to pending ({@link OrderStatus#NEW}) BUY orders. */
    private BigDecimal reservedCash = BigDecimal.ZERO;

    /** What the user owns of each instrument (see {@link Holding}), keyed by instrument id. */
    private final Map<Integer, Holding> holdings = new LinkedHashMap<>();

    private AccountState() {
    }

    /**
     * Replays orders into an account state.
     *
     * @param orders the user's orders, sorted chronologically. Statuses other than FILLED and NEW are ignored
     * @return the resulting account state
     */
    public static AccountState fromOrders(List<Order> orders) {
        AccountState state = new AccountState();
        orders.forEach(state::applyOrder);
        return state;
    }

    /** Adds the effect of one order to the state: FILLED orders settle, NEW orders reserve, others are ignored. */
    private void applyOrder(Order order) {
        if (order.getStatus() == OrderStatus.FILLED) {
            settleFilledOrder(order);
        } else if (order.getStatus() == OrderStatus.NEW) {
            reserveForPendingOrder(order);
        }
    }

    /** Moves cash and shares for an executed order, and updates the holding's cost basis (see {@link Holding}). */
    private void settleFilledOrder(Order order) {
        switch (order.getSide()) {
            case CASH_IN -> cashBalance = cashBalance.add(order.cashAmount());
            case CASH_OUT -> cashBalance = cashBalance.subtract(order.cashAmount());
            case BUY -> {
                cashBalance = cashBalance.subtract(order.cashAmount());
                getOrCreateHolding(order.getInstrumentId()).buy(order.getSize(), order.getPrice());
            }
            case SELL -> {
                cashBalance = cashBalance.add(order.cashAmount());
                getOrCreateHolding(order.getInstrumentId()).sell(order.getSize());
            }
        }
    }

    /** Reserves what a pending order would consume: cash for a BUY, shares for a SELL. Nothing moves yet. */
    private void reserveForPendingOrder(Order order) {
        switch (order.getSide()) {
            case BUY -> reservedCash = reservedCash.add(order.cashAmount());
            case SELL -> {
                Holding holding = getOrCreateHolding(order.getInstrumentId());
                holding.reservedQuantity += order.getSize();
            }
            default -> {
                // Cash transfers are always executed immediately; a pending one has nothing to reserve.
            }
        }
    }

    /** The holding of an instrument, created empty the first time the instrument is traded. */
    private Holding getOrCreateHolding(Integer instrumentId) {
        return holdings.computeIfAbsent(instrumentId, id -> new Holding());
    }

    /**
     * {@return the user's cash balance} Cash in, minus cash out, minus what was paid for buys, plus what was
     * received for sells, counting only executed (FILLED) orders. Pending orders don't move cash, so the
     * balance still includes the cash they reserve.
     */
    public BigDecimal cashBalance() {
        return cashBalance;
    }

    /** {@return cash committed to pending (NEW) BUY orders} */
    public BigDecimal reservedCash() {
        return reservedCash;
    }

    /** {@return cash that can be used by a new order: cash balance minus reserved cash} */
    public BigDecimal availableCash() {
        return cashBalance.subtract(reservedCash);
    }

    /**
     * Shares that can be sold by a new order: filled shares minus those committed to pending SELL orders.
     *
     * @param instrumentId id of the instrument
     * @return available shares; 0 if the user never traded the instrument
     */
    public long availableShares(Integer instrumentId) {
        Holding h = holdings.get(instrumentId);
        return h == null ? 0 : h.quantity - h.reservedQuantity;
    }

    /**
     * Every instrument the user has traded, including positions back at zero shares.
     *
     * @return read-only holdings keyed by instrument id
     */
    public Map<Integer, Holding> holdings() {
        return Collections.unmodifiableMap(holdings);
    }

    /**
     * Instruments the user currently holds: holdings with a non-zero quantity. Instruments that were sold down to
     * zero are excluded. Negative quantities (more shares sold than bought, as in the seed data) are kept so they
     * stay visible.
     *
     * @return read-only holdings keyed by instrument id
     */
    public Map<Integer, Holding> openHoldings() {
        Map<Integer, Holding> open = new LinkedHashMap<>();
        holdings.forEach((instrumentId, holding) -> {
            if (holding.quantity() != 0) {
                open.put(instrumentId, holding);
            }
        });
        return Collections.unmodifiableMap(open);
    }

    /**
     * What the user owns of one instrument, and what it cost them. Built only from orders, with no market prices.
     * <ul>
     *   <li>{@code quantity}: shares owned, according to FILLED orders.</li>
     *   <li>{@code reservedQuantity}: how many of those shares are committed to pending (NEW) SELL orders.</li>
     *   <li>{@code costBasis}: what the shares currently held cost (weighted average cost). A buy adds what it paid;
     *       a sell removes the sold shares at the average cost, so the average of the remaining shares doesn't
     *       change.</li>
     * </ul>
     * Example: bought 10 at 100, then 10 at 120 → cost 2,200, average 110. Sold 15 → 5 shares left, cost 550,
     * average still 110. Bought 10 at 200 → 15 shares, cost 2,550, average 170.
     * <p>
     * Only a long position has a cost. Shares that bring a negative position (more sold than bought, as in the seed
     * data) back up to zero add nothing to it, and a flat or negative position has no average cost.
     * <p>
     * A holding is not priced. {@link Position} is the same holding valued at the latest market price (market value,
     * returns), as shown in the {@link Portfolio}.
     */
    public static final class Holding {

        private long quantity;
        private long reservedQuantity;
        private BigDecimal costBasis = BigDecimal.ZERO;

        private Holding() {
        }

        private void buy(long size, BigDecimal price) {
            // Only the shares that end up in a long position are paid into its cost; the rest cover a negative one.
            long opened = Math.max(0, quantity + size) - Math.max(0, quantity);
            costBasis = costBasis.add(price.multiply(BigDecimal.valueOf(opened)));
            quantity += size;
        }

        private void sell(long size) {
            // The shares still held keep their average cost: the basis shrinks in proportion to the shares sold.
            long longBefore = Math.max(0, quantity);
            long longAfter = Math.max(0, quantity - size);
            costBasis = longAfter == 0 ? BigDecimal.ZERO
                    : costBasis.multiply(BigDecimal.valueOf(longAfter))
                            .divide(BigDecimal.valueOf(longBefore), COST_SCALE, RoundingMode.HALF_EVEN);
            quantity -= size;
        }

        /** {@return shares held according to FILLED orders} */
        public long quantity() {
            return quantity;
        }

        /** {@return shares committed to pending (NEW) SELL orders} */
        public long reservedQuantity() {
            return reservedQuantity;
        }

        /**
         * {@return the average cost per share held (cost basis / quantity), or null if the position is flat or
         * negative}
         */
        public BigDecimal averageCost() {
            return quantity <= 0 ? null
                    : costBasis.divide(BigDecimal.valueOf(quantity), COST_SCALE, RoundingMode.HALF_EVEN);
        }
    }
}
