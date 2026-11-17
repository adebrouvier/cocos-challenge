package ar.com.arieldebrouvier.cocos.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.cashIn;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.filled;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.instrument;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.marketData;
import static org.assertj.core.api.Assertions.assertThat;

class PositionTest {

    private static final int PAMP = 47;

    /** 10 @ 100 + 10 @ 120: quantity 20, average cost 110. */
    private static AccountState.Holding holding() {
        return AccountState.fromOrders(List.of(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                filled(OrderSide.BUY, PAMP, 10, "120"))).holdings().get(PAMP);
    }

    @Test
    void valuesTheHoldingAtTheLatestClose() {
        Position position = Position.of(instrument(PAMP, "PAMP"), holding(), marketData("121", "100"));

        assertThat(position.quantity()).isEqualTo(20);
        assertThat(position.lastPrice()).isEqualByComparingTo("121");
        assertThat(position.marketValue()).isEqualByComparingTo("2420");
        assertThat(position.averageCost()).isEqualByComparingTo("110");
        assertThat(position.totalReturnPct()).isEqualByComparingTo("10");   // (121 - 110) / 110
        assertThat(position.dailyReturnPct()).isEqualByComparingTo("21");   // (121 - 100) / 100
    }

    @Test
    void withoutMarketData_priceDependentValuesAreNull() {
        Position position = Position.of(instrument(PAMP, "PAMP"), holding(), null);

        assertThat(position.quantity()).isEqualTo(20);
        assertThat(position.averageCost()).isEqualByComparingTo("110");
        assertThat(position.lastPrice()).isNull();
        assertThat(position.marketValue()).isNull();
        assertThat(position.totalReturnPct()).isNull();
        assertThat(position.dailyReturnPct()).isNull();
    }

    @Test
    void withoutPreviousClose_dailyReturnIsNull() {
        Position position = Position.of(instrument(PAMP, "PAMP"), holding(), marketData("121", null));

        assertThat(position.totalReturnPct()).isEqualByComparingTo("10");
        assertThat(position.dailyReturnPct()).isNull();
    }
}
