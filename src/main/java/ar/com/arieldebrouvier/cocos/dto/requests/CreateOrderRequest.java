package ar.com.arieldebrouvier.cocos.dto.requests;

import ar.com.arieldebrouvier.cocos.models.OrderSide;
import ar.com.arieldebrouvier.cocos.models.OrderType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Exactly one of {@code size} (number of shares) or {@code amount} (ARS to invest/sell) must be sent.
 * {@code price} is required for LIMIT orders and must be omitted for MARKET orders.
 * For CASH_IN / CASH_OUT, the instrument must be the currency (ARS) and size/amount are pesos.
 * <p>
 * Bean validation checks each field on its own; the rules that combine fields are checked by
 * {@link ar.com.arieldebrouvier.cocos.services.OrderService#submit}.
 */
public record CreateOrderRequest(
        @NotBlank String accountNumber,
        @NotNull Integer instrumentId,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @Positive Integer size,
        @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
