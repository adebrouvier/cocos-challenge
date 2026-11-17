package ar.com.arieldebrouvier.cocos.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builders for unit tests. Entities without a public constructor (users, instruments, market data) are mocked.
 */
public final class ModelFixtures {

    public static final int USER_ID = 1;
    public static final int ARS = 66;

    private static final LocalDateTime START = LocalDateTime.of(2023, 7, 12, 10, 0);

    private ModelFixtures() {
    }

    public static Order filled(OrderSide side, int instrumentId, int size, String price) {
        return order(side, OrderType.MARKET, OrderStatus.FILLED, instrumentId, size, price);
    }

    public static Order pending(OrderSide side, int instrumentId, int size, String price) {
        return order(side, OrderType.LIMIT, OrderStatus.NEW, instrumentId, size, price);
    }

    public static Order order(OrderSide side, OrderType type, OrderStatus status, int instrumentId, int size,
                              String price) {
        return new Order(instrumentId, USER_ID, size, new BigDecimal(price), type, side, status, START);
    }

    public static Order cashIn(int amount) {
        return filled(OrderSide.CASH_IN, ARS, amount, "1");
    }

    public static User user(int id, String accountNumber) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getAccountNumber()).thenReturn(accountNumber);
        return user;
    }

    /** A tradable instrument (not a currency). */
    public static Instrument instrument(int id, String ticker) {
        Instrument instrument = mock(Instrument.class);
        when(instrument.getId()).thenReturn(id);
        when(instrument.getTicker()).thenReturn(ticker);
        return instrument;
    }

    /** The currency instrument, used by CASH_IN / CASH_OUT. */
    public static Instrument currency(int id, String ticker) {
        Instrument instrument = instrument(id, ticker);
        when(instrument.isCurrency()).thenReturn(true);
        return instrument;
    }

    public static MarketData marketData(String close, String previousClose) {
        MarketData marketData = mock(MarketData.class);
        when(marketData.getClose()).thenReturn(close == null ? null : new BigDecimal(close));
        when(marketData.getPreviousClose()).thenReturn(previousClose == null ? null : new BigDecimal(previousClose));
        return marketData;
    }
}
