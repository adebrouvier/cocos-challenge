package ar.com.arieldebrouvier.cocos.services;

import ar.com.arieldebrouvier.cocos.dto.requests.CreateOrderRequest;
import ar.com.arieldebrouvier.cocos.exceptions.InvalidOrderException;
import ar.com.arieldebrouvier.cocos.models.AccountState;
import ar.com.arieldebrouvier.cocos.models.Instrument;
import ar.com.arieldebrouvier.cocos.models.MarketData;
import ar.com.arieldebrouvier.cocos.models.Order;
import ar.com.arieldebrouvier.cocos.models.OrderSide;
import ar.com.arieldebrouvier.cocos.models.OrderStatus;
import ar.com.arieldebrouvier.cocos.models.OrderType;
import ar.com.arieldebrouvier.cocos.models.PlacedOrder;
import ar.com.arieldebrouvier.cocos.models.User;
import ar.com.arieldebrouvier.cocos.repositories.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Records orders and owns the account state derived from them.
 * <p>
 * The orders table is the account's ledger: every change to a user's cash and positions happens by recording an
 * order here, and {@link #getAccountState} is the only way to read the resulting state. Other services (e.g. the
 * portfolio) read the state through this class and never access the orders directly.
 */
@Service
public class OrderService {

    /** Orders that affect the account: FILLED ones move cash and shares, NEW ones reserve them. */
    private static final List<OrderStatus> ACCOUNT_STATE_STATUSES = List.of(OrderStatus.FILLED, OrderStatus.NEW);

    private final UserService userService;
    private final InstrumentService instrumentService;
    private final MarketDataService marketDataService;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderService(UserService userService, InstrumentService instrumentService,
                        MarketDataService marketDataService, OrderRepository orderRepository, Clock clock) {
        this.userService = userService;
        this.instrumentService = instrumentService;
        this.marketDataService = marketDataService;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * Returns a user's current account state: cash balance, cash and shares reserved by pending orders, and
     * holdings with their average cost.
     * <p>
     * The state is derived on every call by replaying the user's FILLED and NEW orders in chronological order, as the
     * challenge requires. This method and {@link #submit} are the only places that read and change the state, so it
     * can later be stored instead (a balances/positions projection updated by {@code submit} in the same
     * transaction, rebuildable by replaying the orders) without changing any caller.
     *
     * @param userId id of the user
     * @return the user's account state; empty (zero cash, no holdings) if the user has no relevant orders
     */
    public AccountState getAccountState(Integer userId) {
        List<Order> userOrders =
                orderRepository.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(userId, ACCOUNT_STATE_STATUSES);
        return AccountState.fromOrders(userOrders);
    }

    /**
     * Validates and records an order.
     * <ul>
     *   <li>Malformed requests throw {@link InvalidOrderException} and nothing is stored.</li>
     *   <li>Well-formed orders are always stored: REJECTED if the user lacks available cash/shares
     *       (pending orders' reservations count as used), otherwise FILLED (MARKET and cash transfers,
     *       executed at the latest close or at 1 respectively) or NEW (LIMIT, at the limit price).</li>
     * </ul>
     * When the request gives an {@code amount} instead of a {@code size}, the size is the maximum whole number
     * of shares that the amount covers at the execution price.
     * <p>
     * The user's row is locked for the duration of the transaction, so concurrent orders of the same user are
     * validated one after another.
     *
     * @param request the order to place
     * @return the stored order with its resulting status, its user and instrument, and the rejection reason
     *         if it was REJECTED
     * @throws ar.com.arieldebrouvier.cocos.exceptions.NotFoundException if the account or instrument doesn't exist
     * @throws InvalidOrderException if the request is inconsistent (e.g. both or neither of size/amount, LIMIT
     *         without price, MARKET with price, wrong instrument for the side) or a MARKET order's instrument
     *         has no market price
     */
    @Transactional
    public PlacedOrder submit(CreateOrderRequest request) {
        validate(request);

        // Lock the user row: concurrent orders of the same user are validated one after another,
        // so two orders can't both spend the same cash or shares.
        User user = userService.getByAccountNumberForUpdate(request.accountNumber());
        Instrument instrument = instrumentService.getById(request.instrumentId());
        validateInstrument(request, instrument);

        BigDecimal price = executionPrice(request, instrument);
        int size = request.size() != null ? request.size() : sizeFromAmount(request, price);

        String rejectionReason = rejectionReason(request.side(), size, price, instrument,
                getAccountState(user.getId()));
        OrderStatus status = rejectionReason != null ? OrderStatus.REJECTED
                : request.type() == OrderType.LIMIT ? OrderStatus.NEW
                : OrderStatus.FILLED;

        Order order = orderRepository.save(new Order(instrument.getId(), user.getId(), size, price,
                request.type(), request.side(), status, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)));
        return new PlacedOrder(order, user, instrument, rejectionReason);
    }

    private static void validate(CreateOrderRequest request) {
        if ((request.size() == null) == (request.amount() == null)) {
            throw new InvalidOrderException("Exactly one of 'size' or 'amount' must be provided");
        }
        if (request.type() == OrderType.LIMIT && request.price() == null) {
            throw new InvalidOrderException("LIMIT orders require a 'price'");
        }
        if (request.type() == OrderType.MARKET && request.price() != null) {
            throw new InvalidOrderException("MARKET orders must not specify a 'price'; they execute at the latest close");
        }
        if (request.side().isCashTransfer()) {
            if (request.type() != OrderType.MARKET) {
                throw new InvalidOrderException(request.side() + " orders must be of type MARKET");
            }
            if (request.amount() != null && request.amount().stripTrailingZeros().scale() > 0) {
                throw new InvalidOrderException(request.side() + " amount must be a whole number of pesos");
            }
        }
    }

    private static void validateInstrument(CreateOrderRequest request, Instrument instrument) {
        if (request.side().isCashTransfer() && !instrument.isCurrency()) {
            throw new InvalidOrderException(request.side() + " orders must use the currency instrument (ARS)");
        }
        if (!request.side().isCashTransfer() && instrument.isCurrency()) {
            throw new InvalidOrderException("Currency instruments can't be bought or sold; use CASH_IN / CASH_OUT");
        }
    }

    private BigDecimal executionPrice(CreateOrderRequest request, Instrument instrument) {
        if (request.side().isCashTransfer()) {
            return BigDecimal.ONE.setScale(2);
        }
        if (request.type() == OrderType.LIMIT) {
            return request.price().setScale(2, RoundingMode.UNNECESSARY);
        }
        // Prices are always positive (enforced by the schema), so sizing by amount can't divide by zero.
        return marketDataService.findLatest(instrument.getId())
                .map(MarketData::getClose)
                .orElseThrow(() -> new InvalidOrderException(
                        "No market price available for " + instrument.getTicker() + "; use a LIMIT order"));
    }

    /** Maximum whole number of shares that the amount can buy (or that make up the amount when selling). */
    private static int sizeFromAmount(CreateOrderRequest request, BigDecimal price) {
        BigDecimal shares = request.amount().divide(price, 0, RoundingMode.DOWN);
        try {
            return shares.intValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidOrderException("Resulting order size is too large");
        }
    }

    private static String rejectionReason(OrderSide side, int size, BigDecimal price, Instrument instrument,
                                          AccountState state) {
        if (size < 1) {
            return "Amount is not enough for a single share";
        }
        return switch (side) {
            case BUY, CASH_OUT -> {
                BigDecimal cashAmount = Order.cashAmount(price, size);
                yield cashAmount.compareTo(state.availableCash()) > 0
                        ? "Insufficient available cash: required " + cashAmount + ", available " + state.availableCash()
                        : null;
            }
            case SELL -> {
                long availableShares = state.availableShares(instrument.getId());
                yield size > availableShares
                        ? "Insufficient available shares of " + instrument.getTicker() + ": required " + size
                          + ", available " + availableShares
                        : null;
            }
            case CASH_IN -> null;
        };
    }
}
