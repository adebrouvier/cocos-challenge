package ar.com.arieldebrouvier.cocos.repositories;

import ar.com.arieldebrouvier.cocos.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The schema rejects market data the application can't use, so the code doesn't have to work around it.
 */
class MarketDataConstraintsTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "NULL", value = {
            "no date,                   47, 2023-07-20, 930, 925",
            "no close,                  47, 2023-07-20, NULL, 925",
            "zero close,                47, 2023-07-20, 0, 925",
            "negative previous close,   47, 2023-07-20, 930, -1",
            "no instrument,             NULL, 2023-07-20, 930, 925"})
    void invalidRows_areRejected(String rule, Integer instrumentId, String date, String close, String previousClose) {
        String rowDate = rule.equals("no date") ? null : date;
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO marketdata (instrumentId, high, low, open, close, previousClose, date)
                VALUES (?, 1, 1, 1, ?::numeric, ?::numeric, ?::date)
                """, instrumentId, close, previousClose, rowDate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** An instrument has no previous close on its first trading day; its daily return is then unknown. */
    @Test
    void rowWithoutPreviousClose_isAccepted() {
        int inserted = jdbc.update("""
                INSERT INTO marketdata (instrumentId, high, low, open, close, previousClose, date)
                VALUES (47, 1, 1, 1, 930, NULL, '2023-07-20')
                """);

        assertThat(inserted).isEqualTo(1);
    }
}
