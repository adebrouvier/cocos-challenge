package ar.com.arieldebrouvier.cocos.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.cashIn;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.filled;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.order;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;

class AccountStateTest {

    private static final int PAMP = 47;
    private static final int METR = 54;

    @Test
    void averageCost_isWeighted_andSellsDontChangeIt() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                filled(OrderSide.BUY, PAMP, 10, "120"),
                filled(OrderSide.SELL, PAMP, 5, "130")));

        AccountState.Holding pamp = state.holdings().get(PAMP);
        assertThat(pamp.quantity()).isEqualTo(15);
        assertThat(pamp.averageCost()).isEqualByComparingTo("110"); // 2,200 / 20
    }

    @Test
    void partialSell_thenBuy_averagesOnlyTheSharesStillHeld() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                filled(OrderSide.SELL, PAMP, 9, "150"),       // 1 share left, cost 100
                filled(OrderSide.BUY, PAMP, 10, "200")));     // + 2,000

        AccountState.Holding pamp = state.holdings().get(PAMP);
        assertThat(pamp.quantity()).isEqualTo(11);
        assertThat(pamp.averageCost()).isEqualByComparingTo("190.909091"); // 2,100 / 11
    }

    @Test
    void buyCrossingFromNegative_paysOnlyForTheSharesAboveZero() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(100_000),
                filled(OrderSide.BUY, PAMP, 20, "100"),
                filled(OrderSide.SELL, PAMP, 30, "110"),      // -10
                filled(OrderSide.BUY, PAMP, 15, "100"),       // 10 cover, 5 held at 100
                filled(OrderSide.BUY, PAMP, 5, "200")));

        assertThat(state.holdings().get(PAMP).quantity()).isEqualTo(10);
        assertThat(state.holdings().get(PAMP).averageCost()).isEqualByComparingTo("150"); // (500 + 1,000) / 10
    }

    @Test
    void cashBalance_movesOnlyWithFilledOrders() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),       // -1,000
                filled(OrderSide.SELL, PAMP, 4, "150"),       // +600
                filled(OrderSide.CASH_OUT, ModelFixtures.ARS, 500, "1"),
                pending(OrderSide.BUY, METR, 10, "50"),       // reserves, doesn't move cash
                order(OrderSide.BUY, OrderType.MARKET, OrderStatus.REJECTED, METR, 1_000, "50"),
                order(OrderSide.BUY, OrderType.LIMIT, OrderStatus.CANCELLED, METR, 1_000, "50")));

        assertThat(state.cashBalance()).isEqualByComparingTo("9100");
    }

    @Test
    void pendingOrders_reserveCashAndShares() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                pending(OrderSide.BUY, METR, 20, "50"),
                pending(OrderSide.SELL, PAMP, 3, "200")));

        assertThat(state.cashBalance()).isEqualByComparingTo("9000");
        assertThat(state.reservedCash()).isEqualByComparingTo("1000");
        assertThat(state.availableCash()).isEqualByComparingTo("8000");
        assertThat(state.availableShares(PAMP)).isEqualTo(7);
        assertThat(state.availableShares(METR)).isZero();
    }

    @Test
    void openHoldings_excludeSoldOutInstruments_butKeepNegativeOnes() {
        int bma = 31;
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(100_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                filled(OrderSide.SELL, PAMP, 10, "110"),      // sold out
                filled(OrderSide.BUY, METR, 5, "50"),
                filled(OrderSide.BUY, bma, 20, "1540"),
                filled(OrderSide.SELL, bma, 30, "1530")));    // seed quirk: -10

        assertThat(state.holdings()).containsOnlyKeys(PAMP, METR, bma);
        assertThat(state.openHoldings()).containsOnlyKeys(METR, bma);
        assertThat(state.openHoldings().get(bma).quantity()).isEqualTo(-10);
    }

    @Test
    void neverBoughtInstrument_hasNoAverageCost() {
        AccountState state = AccountState.fromOrders(List.of(pending(OrderSide.SELL, PAMP, 3, "200")));

        assertThat(state.holdings().get(PAMP).averageCost()).isNull();
    }

    @Test
    void closingAPosition_resetsAverageCost_soAReopenedPositionStartsFresh() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                filled(OrderSide.SELL, PAMP, 10, "150"),      // closed
                filled(OrderSide.BUY, PAMP, 10, "200")));     // reopened

        AccountState.Holding pamp = state.holdings().get(PAMP);
        assertThat(pamp.quantity()).isEqualTo(10);
        assertThat(pamp.averageCost()).isEqualByComparingTo("200"); // not (1,000 + 2,000) / 20 = 150
    }

    @Test
    void negativePosition_hasNoAverageCost() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(100_000),
                filled(OrderSide.BUY, PAMP, 20, "100"),
                filled(OrderSide.SELL, PAMP, 30, "110")));    // seed quirk: -10

        assertThat(state.holdings().get(PAMP).quantity()).isEqualTo(-10);
        assertThat(state.holdings().get(PAMP).averageCost()).isNull();
    }

    @Test
    void buyWhileNegative_stillHasNoAverageCost() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(100_000),
                filled(OrderSide.BUY, PAMP, 20, "100"),
                filled(OrderSide.SELL, PAMP, 30, "110"),      // -10
                filled(OrderSide.BUY, PAMP, 5, "100")));      // -5

        assertThat(state.holdings().get(PAMP).quantity()).isEqualTo(-5);
        assertThat(state.holdings().get(PAMP).averageCost()).isNull();
    }

    @Test
    void sharesThatCoverANegativePosition_dontCountInTheNextAverage() {
        AccountState state = AccountState.fromOrders(List.of(
                cashIn(100_000),
                filled(OrderSide.BUY, PAMP, 20, "100"),
                filled(OrderSide.SELL, PAMP, 30, "110"),      // -10
                filled(OrderSide.BUY, PAMP, 10, "100"),       // back to 0
                filled(OrderSide.BUY, PAMP, 10, "200")));     // reopened

        assertThat(state.holdings().get(PAMP).quantity()).isEqualTo(10);
        assertThat(state.holdings().get(PAMP).averageCost()).isEqualByComparingTo("200");
    }
}
