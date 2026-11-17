package ar.com.arieldebrouvier.cocos.models;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.cashIn;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.filled;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.instrument;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PortfolioTest {

    private final User user = mock(User.class);

    /** Cash balance 10,000, of which 1,000 is reserved by a pending buy. */
    private static AccountState accountState() {
        return AccountState.fromOrders(List.of(cashIn(10_000), pending(OrderSide.BUY, 1, 10, "100")));
    }

    private static Position position(Instrument instrument, String marketValue) {
        return new Position(instrument, 1, null, marketValue == null ? null : new BigDecimal(marketValue),
                null, null, null);
    }

    @Test
    void totalValue_isCashBalancePlusMarketValues() {
        Portfolio portfolio = Portfolio.of(user, accountState(), List.of(
                position(instrument(1, "AAA"), "500"),
                position(instrument(2, "BBB"), "250")));

        assertThat(portfolio.totalValue()).isEqualByComparingTo("10750");  // reserved cash still counts
        assertThat(portfolio.availableCash()).isEqualByComparingTo("9000");
        assertThat(portfolio.reservedCash()).isEqualByComparingTo("1000");
        assertThat(portfolio.user()).isSameAs(user);
    }

    @Test
    void positionsWithoutMarketData_countAsZero_butAreStillListed() {
        Portfolio portfolio = Portfolio.of(user, accountState(), List.of(
                position(instrument(1, "AAA"), "500"),
                position(instrument(2, "BBB"), null)));

        assertThat(portfolio.totalValue()).isEqualByComparingTo("10500");
        assertThat(portfolio.positions()).hasSize(2);
    }

    @Test
    void negativePositions_reduceTheTotalValue() {
        Portfolio portfolio = Portfolio.of(user, accountState(), List.of(
                position(instrument(1, "AAA"), "500"),
                position(instrument(2, "BMA"), "-300")));

        assertThat(portfolio.totalValue()).isEqualByComparingTo("10200");
    }

    @Test
    void positions_areSortedByTicker_nullTickersLast_tiesById() {
        Portfolio portfolio = Portfolio.of(user, accountState(), List.of(
                position(instrument(5, null), "1"),
                position(instrument(4, "BBB"), "1"),
                position(instrument(3, "AAA"), "1"),
                position(instrument(2, "BBB"), "1")));

        assertThat(portfolio.positions())
                .extracting(p -> p.instrument().getId())
                .containsExactly(3, 2, 4, 5);
    }

    @Test
    void noPositions_totalValueIsTheCashBalance() {
        Portfolio portfolio = Portfolio.of(user, accountState(), List.of());

        assertThat(portfolio.totalValue()).isEqualByComparingTo("10000");
        assertThat(portfolio.positions()).isEmpty();
    }
}
