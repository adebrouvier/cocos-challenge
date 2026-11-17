package ar.com.arieldebrouvier.cocos.dto.responses;

import ar.com.arieldebrouvier.cocos.models.Order;
import ar.com.arieldebrouvier.cocos.models.OrderSide;
import ar.com.arieldebrouvier.cocos.models.OrderStatus;
import ar.com.arieldebrouvier.cocos.models.OrderType;
import ar.com.arieldebrouvier.cocos.models.PlacedOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** @param rejectionReason why the order was REJECTED; null otherwise (not persisted) */
public record OrderResponse(
        Integer id,
        String accountNumber,
        Integer instrumentId,
        String ticker,
        OrderSide side,
        OrderType type,
        Integer size,
        BigDecimal price,
        OrderStatus status,
        LocalDateTime datetime,
        String rejectionReason) {

    public static OrderResponse from(PlacedOrder placed) {
        Order order = placed.order();
        return new OrderResponse(order.getId(), placed.user().getAccountNumber(), order.getInstrumentId(),
                placed.instrument().getTicker(), order.getSide(), order.getType(), order.getSize(),
                order.getPrice(), order.getStatus(), order.getDatetime(), placed.rejectionReason());
    }
}
