package ar.com.arieldebrouvier.cocos.controllers;

import ar.com.arieldebrouvier.cocos.IntegrationTest;
import ar.com.arieldebrouvier.cocos.config.ApplicationTimeZone;
import ar.com.arieldebrouvier.cocos.models.OrderStatus;
import ar.com.arieldebrouvier.cocos.repositories.OrderRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Functional tests for order submission, on top of the challenge seed data. Relevant seed facts for
 * account 10001 (user 1): cash balance 753,000; 125,500 reserved by two NEW LIMIT BUYs, so 627,500 available;
 * 40 PAMP (id 47, latest close 925.85). Account 10002 has no orders. ARS is instrument 66.
 */
class OrderControllerTest extends IntegrationTest {

    private static final String ACCOUNT = "10001";
    private static final int PAMP = 47;
    private static final int ARS = 66;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void marketBuyBySize_isFilledAtLatestClose_andReducesAvailableCash() throws Exception {
        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.price").value(925.85))
                .andExpect(jsonPath("$.ticker").value("PAMP"));

        // 627,500 - 10 * 925.85
        mvc.perform(get("/portfolio/" + ACCOUNT))
                .andExpect(jsonPath("$.availableCash").value(618241.50))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].quantity").value(50));
    }

    @Test
    void newOrders_areStampedInArgentinaTime_likeTheExistingData() throws Exception {
        LocalDateTime before = LocalDateTime.now(ApplicationTimeZone.ZONE).truncatedTo(ChronoUnit.SECONDS);
        String body = submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        LocalDateTime after = LocalDateTime.now(ApplicationTimeZone.ZONE);

        LocalDateTime stamped = LocalDateTime.parse(JsonPath.read(body, "$.datetime"));
        assertThat(stamped).isBetween(before, after);
    }

    @Test
    void buyByAmount_buysTheMaximumWholeNumberOfShares() throws Exception {
        // 5000 / 925.85 = 5.4 -> 5 shares
        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"amount\": 5000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void buyByAmount_notEnoughForOneShare_isRejected() throws Exception {
        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"amount\": 100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.size").value(0));
    }

    @Test
    void limitBuy_staysNew_andReservesCash() throws Exception {
        submit(order(ACCOUNT, PAMP, "BUY", "LIMIT", "\"size\": 10, \"price\": 900"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.price").value(900.00));

        mvc.perform(get("/portfolio/" + ACCOUNT))
                .andExpect(jsonPath("$.reservedCash").value(134500.00))
                .andExpect(jsonPath("$.availableCash").value(618500.00))
                // Not executed: holdings unchanged.
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].quantity").value(40));
    }

    @Test
    void buyExceedingAvailableCash_isRejectedAndPersisted_evenIfCashBalanceWouldCoverIt() throws Exception {
        // 700,000 is below cash balance (753,000) but above available cash (627,500) because of reservations.
        String body = submit(order(ACCOUNT, PAMP, "BUY", "LIMIT", "\"size\": 700, \"price\": 1000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason", containsString("Insufficient available cash")))
                .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(body, "$.id");
        assertThat(orderRepository.findById(id)).hasValueSatisfying(
                o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.REJECTED));
    }

    @Test
    void sellOfSharesReservedByAPendingSell_isRejected() throws Exception {
        // 40 PAMP held: a pending LIMIT SELL of 30 leaves 10 available.
        submit(order(ACCOUNT, PAMP, "SELL", "LIMIT", "\"size\": 30, \"price\": 1000"))
                .andExpect(jsonPath("$.status").value("NEW"));

        submit(order(ACCOUNT, PAMP, "SELL", "MARKET", "\"size\": 20"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason", containsString("available 10")));

        submit(order(ACCOUNT, PAMP, "SELL", "MARKET", "\"size\": 10"))
                .andExpect(jsonPath("$.status").value("FILLED"));
    }

    @Test
    void cashInAndCashOut_moveCash_andCashOutCannotOverdraw() throws Exception {
        submit(order("10002", ARS, "CASH_IN", "MARKET", "\"amount\": 1000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.size").value(1000));

        submit(order("10002", ARS, "CASH_OUT", "MARKET", "\"amount\": 1500"))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        submit(order("10002", ARS, "CASH_OUT", "MARKET", "\"amount\": 400"))
                .andExpect(jsonPath("$.status").value("FILLED"));

        mvc.perform(get("/portfolio/10002"))
                .andExpect(jsonPath("$.availableCash").value(600.00))
                .andExpect(jsonPath("$.totalValue").value(600.00));
    }

    @Test
    void malformedOrders_areBadRequests_andNotPersisted() throws Exception {
        long before = orderRepository.count();

        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 1, \"amount\": 1000"))
                .andExpect(status().isBadRequest());
        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 1, \"price\": 900"))
                .andExpect(status().isBadRequest());
        submit(order(ACCOUNT, PAMP, "BUY", "LIMIT", "\"size\": 1"))
                .andExpect(status().isBadRequest());
        submit(order(ACCOUNT, ARS, "BUY", "MARKET", "\"size\": 1"))
                .andExpect(status().isBadRequest());
        submit(order(ACCOUNT, PAMP, "CASH_IN", "MARKET", "\"size\": 1"))
                .andExpect(status().isBadRequest());
        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": -5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
        submit(order(ACCOUNT, PAMP, "HOLD", "MARKET", "\"size\": 1"))
                .andExpect(status().isBadRequest());
        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 1.5"))     // no fractional shares
                .andExpect(status().isBadRequest());

        assertThat(orderRepository.count()).isEqualTo(before);
    }

    @Test
    void marketOrder_andPortfolio_useTheNewestRow() throws Exception {
        insertMarketData(PAMP, "940", "2023-07-15");

        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 1"))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.price").value(940.00));

        mvc.perform(get("/portfolio/" + ACCOUNT))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].lastPrice").value(940.00));
    }

    @Test
    void severalRowsOnTheLatestDay_theLastInsertedOneIsUsed() throws Exception {
        insertMarketData(PAMP, "935", "2023-07-14");   // PAMP already has a row for that day (close 925.85)
        insertMarketData(PAMP, "945", "2023-07-14");

        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 1"))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.price").value(945.00));

        mvc.perform(get("/portfolio/" + ACCOUNT))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].lastPrice").value(945.00));
    }

    @Test
    void instrumentWithoutMarketData_cantBeTradedAtMarket_andHasNoPriceInThePortfolio() throws Exception {
        jdbc.update("DELETE FROM marketdata WHERE instrumentId = ?", PAMP);
        long before = orderRepository.count();

        submit(order(ACCOUNT, PAMP, "BUY", "MARKET", "\"size\": 10"))
                .andExpect(status().isBadRequest());
        assertThat(orderRepository.count()).isEqualTo(before);

        mvc.perform(get("/portfolio/" + ACCOUNT))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].quantity").value(40))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].lastPrice").value((Object) null));
    }

    @Test
    void unknownAccountOrInstrument_isNotFound() throws Exception {
        submit(order("99999", PAMP, "BUY", "MARKET", "\"size\": 1"))
                .andExpect(status().isNotFound());
        submit(order(ACCOUNT, 9999, "BUY", "MARKET", "\"size\": 1"))
                .andExpect(status().isNotFound());
    }

    private void insertMarketData(int instrumentId, String close, String date) {
        jdbc.update("""
                INSERT INTO marketdata (instrumentId, high, low, open, close, previousClose, date)
                VALUES (?, ?::numeric, ?::numeric, ?::numeric, ?::numeric, 925.85, ?::date)
                """, instrumentId, close, close, close, close, date);
    }

    private ResultActions submit(String json) throws Exception {
        return mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String order(String account, int instrumentId, String side, String type, String quantityFields) {
        return """
                {"accountNumber": "%s", "instrumentId": %d, "side": "%s", "type": "%s", %s}
                """.formatted(account, instrumentId, side, type, quantityFields);
    }
}
