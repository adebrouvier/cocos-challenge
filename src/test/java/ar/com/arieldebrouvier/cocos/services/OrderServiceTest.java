package ar.com.arieldebrouvier.cocos.services;

import ar.com.arieldebrouvier.cocos.dto.requests.CreateOrderRequest;
import ar.com.arieldebrouvier.cocos.exceptions.InvalidOrderException;
import ar.com.arieldebrouvier.cocos.exceptions.NotFoundException;
import ar.com.arieldebrouvier.cocos.models.Instrument;
import ar.com.arieldebrouvier.cocos.models.MarketData;
import ar.com.arieldebrouvier.cocos.models.Order;
import ar.com.arieldebrouvier.cocos.models.OrderSide;
import ar.com.arieldebrouvier.cocos.models.OrderStatus;
import ar.com.arieldebrouvier.cocos.models.OrderType;
import ar.com.arieldebrouvier.cocos.models.PlacedOrder;
import ar.com.arieldebrouvier.cocos.models.User;
import ar.com.arieldebrouvier.cocos.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.ARS;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.USER_ID;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.cashIn;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.currency;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.filled;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.instrument;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.marketData;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.pending;
import static ar.com.arieldebrouvier.cocos.models.ModelFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Order submission rules, without a database: collaborators are mocked and the clock is fixed.
 * <p>
 * Unless a test says otherwise, the account has a cash balance of 9,000 and 10 PAMP shares, and PAMP's latest
 * close is 100.
 */
class OrderServiceTest {

    private static final String ACCOUNT = "10001";
    private static final int PAMP = 47;
    private static final int METR = 54;

    /** 15:30:45.678 UTC = 12:30:45.678 in Buenos Aires. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2023-07-14T15:30:45.678Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private final UserService userService = mock(UserService.class);
    private final InstrumentService instrumentService = mock(InstrumentService.class);
    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);

    private final OrderService orderService =
            new OrderService(userService, instrumentService, marketDataService, orderRepository, CLOCK);

    @BeforeEach
    void setUp() {
        // Fixtures are mocks themselves, so they're built before being passed to when(...).
        User user = user(USER_ID, ACCOUNT);
        Instrument pamp = instrument(PAMP, "PAMP");
        Instrument metr = instrument(METR, "METR");
        Instrument ars = currency(ARS, "ARS");
        MarketData pampPrice = marketData("100", "90");

        when(userService.getByAccountNumberForUpdate(ACCOUNT)).thenReturn(user);
        when(instrumentService.getById(PAMP)).thenReturn(pamp);
        when(instrumentService.getById(METR)).thenReturn(metr);
        when(instrumentService.getById(ARS)).thenReturn(ars);
        when(marketDataService.findLatest(PAMP)).thenReturn(Optional.of(pampPrice));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        givenAccountOrders(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"));
    }

    // --- Accepted orders: status, price and size ---

    @Test
    void marketBuy_isFilledAtTheLatestClose() {
        PlacedOrder placed = orderService.submit(bySize(OrderSide.BUY, OrderType.MARKET, PAMP, 5, null));

        Order order = placed.order();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getPrice()).isEqualByComparingTo("100");
        assertThat(order.getSize()).isEqualTo(5);
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getInstrumentId()).isEqualTo(PAMP);
        assertThat(placed.rejectionReason()).isNull();
        verify(orderRepository).save(order);
    }

    @Test
    void limitBuy_isNew_atTheLimitPrice_withoutLookingUpTheMarketPrice() {
        Order order = orderService.submit(bySize(OrderSide.BUY, OrderType.LIMIT, PAMP, 5, "95.5")).order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getPrice()).isEqualTo(new BigDecimal("95.50"));
        verifyNoInteractions(marketDataService);
    }

    @Test
    void limitSell_isNew() {
        Order order = orderService.submit(bySize(OrderSide.SELL, OrderType.LIMIT, PAMP, 4, "120")).order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void marketSell_isFilled() {
        Order order = orderService.submit(bySize(OrderSide.SELL, OrderType.MARKET, PAMP, 10, null)).order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getPrice()).isEqualByComparingTo("100");
    }

    @Test
    void cashTransfers_areFilledAtOnePeso() {
        Order cashIn = orderService.submit(bySize(OrderSide.CASH_IN, OrderType.MARKET, ARS, 500, null)).order();
        Order cashOut = orderService.submit(bySize(OrderSide.CASH_OUT, OrderType.MARKET, ARS, 500, null)).order();

        assertThat(cashIn.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(cashIn.getPrice()).isEqualTo(new BigDecimal("1.00"));
        assertThat(cashOut.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(cashOut.getPrice()).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void orders_areStampedWithTheClock_truncatedToSeconds() {
        Order order = orderService.submit(bySize(OrderSide.BUY, OrderType.MARKET, PAMP, 1, null)).order();

        assertThat(order.getDatetime()).isEqualTo(LocalDateTime.of(2023, 7, 14, 12, 30, 45));
    }

    // --- Size from amount ---

    @Test
    void buyByAmount_buysTheMaximumWholeNumberOfShares() {
        Order order = orderService.submit(byAmount(OrderSide.BUY, OrderType.MARKET, PAMP, "1050", null)).order();

        assertThat(order.getSize()).isEqualTo(10); // 10.5 shares, rounded down
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void sellByAmount_sellsTheWholeNumberOfSharesThatMakeUpTheAmount() {
        Order order = orderService.submit(byAmount(OrderSide.SELL, OrderType.MARKET, PAMP, "450", null)).order();

        assertThat(order.getSize()).isEqualTo(4);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void limitBuyByAmount_isSizedAtTheLimitPrice() {
        Order order = orderService.submit(byAmount(OrderSide.BUY, OrderType.LIMIT, PAMP, "1000", "80")).order();

        assertThat(order.getSize()).isEqualTo(12); // 12.5 shares at 80
    }

    @Test
    void cashTransferByAmount_movesThatManyPesos() {
        Order order = orderService.submit(byAmount(OrderSide.CASH_IN, OrderType.MARKET, ARS, "500.00", null)).order();

        assertThat(order.getSize()).isEqualTo(500);
    }

    @Test
    void amountNotEnoughForOneShare_isRejected_andStored() {
        PlacedOrder placed = orderService.submit(byAmount(OrderSide.BUY, OrderType.MARKET, PAMP, "99.99", null));

        assertThat(placed.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(placed.order().getSize()).isZero();
        assertThat(placed.rejectionReason()).isEqualTo("Amount is not enough for a single share");
        verify(orderRepository).save(placed.order());
    }

    @Test
    void amountThatOverflowsTheSize_isInvalid() {
        CreateOrderRequest request = byAmount(OrderSide.BUY, OrderType.LIMIT, PAMP, "100000000000", "0.01");

        assertThatThrownBy(() -> orderService.submit(request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Resulting order size is too large");
        verify(orderRepository, never()).save(any());
    }

    // --- Rejections: available cash and shares ---

    @Test
    void buyCostingExactlyTheAvailableCash_isFilled() {
        Order order = orderService.submit(bySize(OrderSide.BUY, OrderType.MARKET, PAMP, 90, null)).order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED); // 90 × 100 = 9,000
    }

    @Test
    void buyExceedingAvailableCash_isRejected_evenIfTheCashBalanceCoversIt() {
        givenAccountOrders(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),       // balance 9,000
                pending(OrderSide.BUY, METR, 10, "100"));     // reserves 1,000 → available 8,000

        PlacedOrder placed = orderService.submit(bySize(OrderSide.BUY, OrderType.MARKET, PAMP, 85, null));

        assertThat(placed.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(placed.rejectionReason()).isEqualTo("Insufficient available cash: required 8500, available 8000");
        verify(orderRepository).save(placed.order());
    }

    @Test
    void limitBuyExceedingAvailableCash_isRejected_notNew() {
        Order order = orderService.submit(bySize(OrderSide.BUY, OrderType.LIMIT, PAMP, 100, "95")).order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED); // 9,500 > 9,000
    }

    @Test
    void cashOutExceedingAvailableCash_isRejected() {
        PlacedOrder placed = orderService.submit(bySize(OrderSide.CASH_OUT, OrderType.MARKET, ARS, 9_001, null));

        assertThat(placed.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(placed.rejectionReason()).startsWith("Insufficient available cash");
    }

    @Test
    void sellOfSharesReservedByAPendingSell_isRejected() {
        givenAccountOrders(
                cashIn(10_000),
                filled(OrderSide.BUY, PAMP, 10, "100"),
                pending(OrderSide.SELL, PAMP, 3, "150"));     // 7 shares available

        PlacedOrder placed = orderService.submit(bySize(OrderSide.SELL, OrderType.MARKET, PAMP, 8, null));

        assertThat(placed.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(placed.rejectionReason()).isEqualTo("Insufficient available shares of PAMP: required 8, available 7");
    }

    @Test
    void sellOfAnInstrumentNeverBought_isRejected() {
        MarketData metrPrice = marketData("50", "50");
        when(marketDataService.findLatest(METR)).thenReturn(Optional.of(metrPrice));

        PlacedOrder placed = orderService.submit(bySize(OrderSide.SELL, OrderType.MARKET, METR, 1, null));

        assertThat(placed.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(placed.rejectionReason()).isEqualTo("Insufficient available shares of METR: required 1, available 0");
    }

    @Test
    void cashIn_isNeverRejected_evenOnAnEmptyAccount() {
        givenAccountOrders();

        Order order = orderService.submit(bySize(OrderSide.CASH_IN, OrderType.MARKET, ARS, 1_000_000, null)).order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    // --- Invalid requests: exception, nothing stored ---

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                argumentSet("both size and amount",
                        request(OrderSide.BUY, OrderType.MARKET, PAMP, 1, "100", null),
                        "Exactly one of 'size' or 'amount' must be provided"),
                argumentSet("neither size nor amount",
                        request(OrderSide.BUY, OrderType.MARKET, PAMP, null, null, null),
                        "Exactly one of 'size' or 'amount' must be provided"),
                argumentSet("LIMIT without price",
                        bySize(OrderSide.BUY, OrderType.LIMIT, PAMP, 1, null),
                        "LIMIT orders require a 'price'"),
                argumentSet("MARKET with price",
                        bySize(OrderSide.BUY, OrderType.MARKET, PAMP, 1, "100"),
                        "MARKET orders must not specify a 'price'"),
                argumentSet("LIMIT cash transfer",
                        bySize(OrderSide.CASH_IN, OrderType.LIMIT, ARS, 100, "1"),
                        "CASH_IN orders must be of type MARKET"),
                argumentSet("fractional cash amount",
                        byAmount(OrderSide.CASH_OUT, OrderType.MARKET, ARS, "100.50", null),
                        "CASH_OUT amount must be a whole number of pesos"),
                argumentSet("cash transfer of a stock",
                        bySize(OrderSide.CASH_IN, OrderType.MARKET, PAMP, 100, null),
                        "CASH_IN orders must use the currency instrument (ARS)"),
                argumentSet("buy of the currency",
                        bySize(OrderSide.BUY, OrderType.MARKET, ARS, 100, null),
                        "Currency instruments can't be bought or sold"),
                argumentSet("MARKET order without market data",
                        bySize(OrderSide.BUY, OrderType.MARKET, METR, 1, null),
                        "No market price available for METR; use a LIMIT order"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidRequests_throw_andStoreNothing(CreateOrderRequest request, String message) {
        when(marketDataService.findLatest(METR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.submit(request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageStartingWith(message);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unknownAccount_isNotFound_andStoresNothing() {
        when(userService.getByAccountNumberForUpdate("99999")).thenThrow(new NotFoundException("Account not found"));
        CreateOrderRequest request = new CreateOrderRequest("99999", PAMP, OrderSide.BUY, OrderType.MARKET, 1,
                null, null);

        assertThatThrownBy(() -> orderService.submit(request)).isInstanceOf(NotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    // --- Account state ---

    @Test
    void accountState_isBuiltFromFilledAndNewOrdersOnly() {
        orderService.getAccountState(USER_ID);

        verify(orderRepository).findByUserIdAndStatusInOrderByDatetimeAscIdAsc(
                USER_ID, List.of(OrderStatus.FILLED, OrderStatus.NEW));
    }

    // --- Helpers ---

    private void givenAccountOrders(Order... orders) {
        when(orderRepository.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(USER_ID), anyList()))
                .thenReturn(List.of(orders));
    }

    private static CreateOrderRequest bySize(OrderSide side, OrderType type, int instrumentId, int size,
                                             String price) {
        return request(side, type, instrumentId, size, null, price);
    }

    private static CreateOrderRequest byAmount(OrderSide side, OrderType type, int instrumentId, String amount,
                                               String price) {
        return request(side, type, instrumentId, null, amount, price);
    }

    private static CreateOrderRequest request(OrderSide side, OrderType type, int instrumentId, Integer size,
                                              String amount, String price) {
        return new CreateOrderRequest(ACCOUNT, instrumentId, side, type, size,
                amount == null ? null : new BigDecimal(amount), price == null ? null : new BigDecimal(price));
    }
}
