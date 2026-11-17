package ar.com.arieldebrouvier.cocos.controllers;

import ar.com.arieldebrouvier.cocos.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void portfolioOfSeedUser_matchesHandCalculatedValues() throws Exception {
        // Cash balance: 1,000,000 - 46,500 + 9,400 - 100,000 - 30,800 - 125,000 + 45,900 = 753,000
        // Reserved by NEW LIMIT BUYs: 50 * 710 + 60 * 1500 = 125,500
        // Positions: PAMP 40 * 925.85 + METR 500 * 229.50 + BMA -10 * 1502.80 = 136,756
        mvc.perform(get("/portfolio/10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("10001"))
                .andExpect(jsonPath("$.availableCash").value(627500.00))
                .andExpect(jsonPath("$.reservedCash").value(125500.00))
                .andExpect(jsonPath("$.totalValue").value(889756.00))
                .andExpect(jsonPath("$.positions", hasSize(3)))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].quantity").value(40))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].marketValue").value(37034.00))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].averageCost").value(930.00))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].totalReturnPct").value(-0.45))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].dailyReturnPct").value(0.44))
                .andExpect(jsonPath("$.positions[?(@.ticker == 'METR')].totalReturnPct").value(-8.20));
    }

    @Test
    void userWithoutOrders_hasEmptyPortfolio() throws Exception {
        mvc.perform(get("/portfolio/10002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(0))
                .andExpect(jsonPath("$.positions", hasSize(0)));
    }

    @Test
    void unknownAccount_isNotFound() throws Exception {
        mvc.perform(get("/portfolio/99999")).andExpect(status().isNotFound());
    }
}
