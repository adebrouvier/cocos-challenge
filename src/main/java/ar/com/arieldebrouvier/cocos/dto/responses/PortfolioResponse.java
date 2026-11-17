package ar.com.arieldebrouvier.cocos.dto.responses;

import ar.com.arieldebrouvier.cocos.models.Portfolio;

import java.math.BigDecimal;
import java.util.List;

import static ar.com.arieldebrouvier.cocos.dto.responses.Rounding.round;

/**
 * @param totalValue    cash (including reserved cash) plus the market value of all positions
 * @param availableCash cash that can be used by new orders
 * @param reservedCash  cash committed to pending (NEW) LIMIT BUY orders
 */
public record PortfolioResponse(
        String accountNumber,
        BigDecimal totalValue,
        BigDecimal availableCash,
        BigDecimal reservedCash,
        List<PositionResponse> positions) {

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.user().getAccountNumber(),
                round(portfolio.totalValue()),
                round(portfolio.availableCash()),
                round(portfolio.reservedCash()),
                portfolio.positions().stream().map(PositionResponse::from).toList());
    }
}
