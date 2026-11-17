package ar.com.arieldebrouvier.cocos.controllers;

import ar.com.arieldebrouvier.cocos.dto.responses.PortfolioResponse;
import ar.com.arieldebrouvier.cocos.services.PortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/portfolio/{accountNumber}")
    public PortfolioResponse getPortfolio(@PathVariable String accountNumber) {
        return PortfolioResponse.from(portfolioService.getPortfolio(accountNumber));
    }
}
