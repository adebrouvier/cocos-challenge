package ar.com.arieldebrouvier.cocos.dto.responses;

import ar.com.arieldebrouvier.cocos.models.Position;

import java.math.BigDecimal;

import static ar.com.arieldebrouvier.cocos.dto.responses.Rounding.round;

/**
 * @param lastPrice      latest close
 * @param marketValue    quantity * lastPrice
 * @param averageCost    average cost per share held (weighted average cost); null if the position isn't long
 * @param totalReturnPct return of the position vs. its average cost, in %
 * @param dailyReturnPct latest close vs. previous close, in %
 */
public record PositionResponse(
        Integer instrumentId,
        String ticker,
        String name,
        long quantity,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal averageCost,
        BigDecimal totalReturnPct,
        BigDecimal dailyReturnPct) {

    public static PositionResponse from(Position position) {
        return new PositionResponse(
                position.instrument().getId(),
                position.instrument().getTicker(),
                position.instrument().getName(),
                position.quantity(),
                round(position.lastPrice()),
                round(position.marketValue()),
                round(position.averageCost()),
                round(position.totalReturnPct()),
                round(position.dailyReturnPct()));
    }
}
