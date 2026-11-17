package ar.com.arieldebrouvier.cocos.repositories;

import ar.com.arieldebrouvier.cocos.models.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MarketDataRepository extends JpaRepository<MarketData, Integer> {

    /**
     * The market data rows with the most recent date of each of the given instruments. Instruments without market
     * data are absent from the result. An instrument can have more than one row on that date; the caller picks one.
     * <p>
     * Each requested instrument's rows are read, which is fine because the table only holds the last 2 days of
     * prices. With a long price history, a {@code LATERAL ... ORDER BY date DESC LIMIT 1} lookup per instrument
     * would keep the cost constant.
     */
    @Query("""
            select m from MarketData m
            where m.instrumentId in :instrumentIds
              and m.date = (select max(m2.date) from MarketData m2 where m2.instrumentId = m.instrumentId)
            """)
    List<MarketData> findLatestByInstrumentIds(@Param("instrumentIds") Collection<Integer> instrumentIds);
}
