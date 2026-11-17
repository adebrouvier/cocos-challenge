package ar.com.arieldebrouvier.cocos.models;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A user's portfolio, valued at the latest market prices. Values are not rounded.
 *
 * @param totalValue    cash (including reserved cash) plus the market value of all positions
 * @param availableCash cash that can be used by new orders
 * @param reservedCash  cash committed to pending (NEW) LIMIT BUY orders
 * @param positions     positions ordered by ticker
 */
public record Portfolio(
        User user,
        BigDecimal totalValue,
        BigDecimal availableCash,
        BigDecimal reservedCash,
        List<Position> positions) {

    private static final Comparator<Position> BY_TICKER = Comparator
            .comparing((Position p) -> p.instrument().getTicker(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(p -> p.instrument().getId());

    /**
     * Builds a portfolio from the user's account state and their priced positions.
     * <p>
     * Total value is the cash balance plus the market value of all positions. Positions without market data
     * (null market value) are still listed but count as 0, and a negative position (more shares sold than
     * bought, as in the seed data) reduces the total.
     *
     * @param user         the account's user
     * @param accountState the user's cash and reservations
     * @param positions    the user's priced positions, in any order
     * @return the portfolio, with positions ordered by ticker
     */
    public static Portfolio of(User user, AccountState accountState, List<Position> positions) {
        BigDecimal positionsValue = positions.stream()
                .map(Position::marketValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Portfolio(
                user,
                accountState.cashBalance().add(positionsValue),
                accountState.availableCash(),
                accountState.reservedCash(),
                positions.stream().sorted(BY_TICKER).toList());
    }
}
