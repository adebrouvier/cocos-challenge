package ar.com.arieldebrouvier.cocos.controllers;

import ar.com.arieldebrouvier.cocos.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstrumentControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void searchByTicker_isCaseInsensitive() throws Exception {
        mvc.perform(get("/instruments").param("q", "pamp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticker").value("PAMP"))
                .andExpect(jsonPath("$[0].name").value("Pampa Holding S.A."));
    }

    @Test
    void searchByName_matchesAnyPartOfTheName() throws Exception {
        mvc.perform(get("/instruments").param("q", "macro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].ticker", hasItem("BMA")));
    }

    @Test
    void likeWildcardsAreTreatedLiterally() throws Exception {
        mvc.perform(get("/instruments").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void blankQuery_isBadRequest() throws Exception {
        mvc.perform(get("/instruments").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/instruments")).andExpect(status().isBadRequest());
    }
}
