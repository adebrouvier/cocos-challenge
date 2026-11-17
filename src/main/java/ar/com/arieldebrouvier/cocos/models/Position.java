package ar.com.arieldebrouvier.cocos.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A {@link AccountState.Holding} valued at the latest market price. Values are not rounded. Price-dependent values
 * are null when the instrument has no market data.
 *
 * @param quantity       shares held according to FILLED orders
 * @param lastPrice      latest close
 * @param marketValue    quantity * lastPrice
 * @param averageCost    average cost per share held (weighted average cost); null if the position isn't long
 * @param totalReturnPct return of the position vs. its average cost, in %
 * @param dailyReturnPct latest close vs. previous close, in %
 */
public record Position(
        Instrument instrument,
        long quantity,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal averageCost,
        BigDecimal totalReturnPct,
        BigDecimal dailyReturnPct) {

    /** Internal precision of percentages; rounding for display happens in the presentation layer. */
    private static final int PERCENT_SCALE = 6;

    /**
     * Values a holding at the latest market price.
     *
     * @param instrument the holding's instrument
     * @param holding    what the user owns of the instrument
     * @param marketData the instrument's latest market data, or null if it has none
     * @return the priced position; price-dependent values are null when {@code marketData} is null
     */
    public static Position of(Instrument instrument, AccountState.Holding holding, MarketData marketData) {
        BigDecimal lastPrice = marketData == null ? null : marketData.getClose();
        BigDecimal previousClose = marketData == null ? null : marketData.getPreviousClose();
        BigDecimal marketValue = lastPrice == null ? null
                : lastPrice.multiply(BigDecimal.valueOf(holding.quantity()));
        return new Position(
                instrument,
                holding.quantity(),
                lastPrice,
                marketValue,
                holding.averageCost(),
                percentChange(lastPrice, holding.averageCost()),
                percentChange(lastPrice, previousClose));
    }

    /** (current - base) / base * 100, or null when either value is missing or base is zero. */
    private static BigDecimal percentChange(BigDecimal current, BigDecimal base) {
        if (current == null || base == null || base.signum() == 0) {
            return null;
        }
        return current.subtract(base)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, PERCENT_SCALE, RoundingMode.HALF_EVEN);
    }
}
