package ar.com.arieldebrouvier.cocos.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioController {

    @GetMapping("/portfolio/{accountNumber}")
    String getPortfolio(@PathVariable(name = "accountNumber") String accountNumber) {

        return "Hello World! " + accountNumber;
    }
}
