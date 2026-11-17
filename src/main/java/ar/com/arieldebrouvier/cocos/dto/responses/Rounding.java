package ar.com.arieldebrouvier.cocos.dto.responses;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How the API presents decimal values (money and percentages): 2 decimals, HALF_EVEN. */
final class Rounding {

    private static final int SCALE = 2;

    private Rounding() {
    }

    static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
