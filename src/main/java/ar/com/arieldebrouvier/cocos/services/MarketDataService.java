package ar.com.arieldebrouvier.cocos.services;

import ar.com.arieldebrouvier.cocos.models.MarketData;
import ar.com.arieldebrouvier.cocos.repositories.MarketDataRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Latest market prices, shared by order execution and portfolio valuation so both always see the same price.
 * An instrument's latest price is its {@code marketdata} row with the most recent date; if that date has several
 * rows, the one inserted last (highest id). The schema guarantees a date and positive prices on every row.
 */
@Service
public class MarketDataService {

    private final MarketDataRepository marketDataRepository;

    public MarketDataService(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    /**
     * Finds the latest market data of an instrument.
     *
     * @param instrumentId id of the instrument
     * @return the latest market data, or empty if the instrument has none
     */
    public Optional<MarketData> findLatest(Integer instrumentId) {
        return Optional.ofNullable(findLatest(List.of(instrumentId)).get(instrumentId));
    }

    /**
     * Finds the latest market data of several instruments in a single query.
     *
     * @param instrumentIds ids of the instruments; may be empty
     * @return latest market data keyed by instrument id. Instruments without market data are absent from the map
     */
    public Map<Integer, MarketData> findLatest(Collection<Integer> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }
        return marketDataRepository.findLatestByInstrumentIds(instrumentIds).stream()
                .collect(Collectors.toMap(MarketData::getInstrumentId, Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b));
    }
}
