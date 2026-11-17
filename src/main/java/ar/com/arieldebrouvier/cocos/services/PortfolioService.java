package ar.com.arieldebrouvier.cocos.services;

import ar.com.arieldebrouvier.cocos.models.AccountState;
import ar.com.arieldebrouvier.cocos.models.AccountState.Holding;
import ar.com.arieldebrouvier.cocos.models.Instrument;
import ar.com.arieldebrouvier.cocos.models.MarketData;
import ar.com.arieldebrouvier.cocos.models.Portfolio;
import ar.com.arieldebrouvier.cocos.models.Position;
import ar.com.arieldebrouvier.cocos.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Portfolio view of a user's account: the account state (what the user has, from {@link OrderService})
 * valued at the latest market prices (what it's worth).
 */
@Service
public class PortfolioService {

    private final UserService userService;
    private final InstrumentService instrumentService;
    private final MarketDataService marketDataService;
    private final OrderService orderService;

    public PortfolioService(UserService userService, InstrumentService instrumentService,
                            MarketDataService marketDataService, OrderService orderService) {
        this.userService = userService;
        this.instrumentService = instrumentService;
        this.marketDataService = marketDataService;
        this.orderService = orderService;
    }

    /**
     * Builds a user's portfolio, valued at the latest market prices.
     * <ul>
     *   <li>Holdings come from FILLED orders. Positions back at zero shares are omitted.</li>
     *   <li>Available cash excludes cash reserved by pending (NEW) LIMIT BUY orders; total value includes it.</li>
     *   <li>Total return compares the latest close with the weighted average cost of the shares held (null for a
     *       flat or negative position); daily return compares the latest close with the previous close.</li>
     * </ul>
     * Values are not rounded. Price-dependent values of a position are null if its instrument has no market data.
     *
     * @param accountNumber the account number
     * @return the portfolio, with positions ordered by ticker
     * @throws ar.com.arieldebrouvier.cocos.exceptions.NotFoundException if the account doesn't exist
     */
    @Transactional(readOnly = true)
    public Portfolio getPortfolio(String accountNumber) {
        User user = userService.getByAccountNumber(accountNumber);
        AccountState accountState = orderService.getAccountState(user.getId());
        Map<Integer, Holding> holdingsByInstrumentId = accountState.openHoldings();
        Set<Integer> instrumentIds = holdingsByInstrumentId.keySet();

        Map<Integer, Instrument> instrumentsById = instrumentService.getByIds(instrumentIds);
        Map<Integer, MarketData> latestMarketDataByInstrumentId = marketDataService.findLatest(instrumentIds);

        List<Position> positions = holdingsByInstrumentId.entrySet().stream()
                .map(entry -> {
                    Integer instrumentId = entry.getKey();
                    return Position.of(instrumentsById.get(instrumentId), entry.getValue(),
                            latestMarketDataByInstrumentId.get(instrumentId));
                })
                .toList();

        return Portfolio.of(user, accountState, positions);
    }
}
