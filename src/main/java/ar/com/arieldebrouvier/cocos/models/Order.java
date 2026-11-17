package ar.com.arieldebrouvier.cocos.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    @Column(name = "userid")
    private Integer userId;

    private Integer size;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime datetime;

    protected Order() {
    }

    public Order(Integer instrumentId, Integer userId, Integer size, BigDecimal price, OrderType type,
                 OrderSide side, OrderStatus status, LocalDateTime datetime) {
        this.instrumentId = instrumentId;
        this.userId = userId;
        this.size = size;
        this.price = price;
        this.type = type;
        this.side = side;
        this.status = status;
        this.datetime = datetime;
    }

    public Integer getId() {
        return id;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getSize() {
        return size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OrderType getType() {
        return type;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getDatetime() {
        return datetime;
    }

    /**
     * {@return the cash this order moves: size * price} Paid on a BUY, received on a SELL, transferred on
     * CASH_IN / CASH_OUT.
     */
    public BigDecimal cashAmount() {
        return cashAmount(price, size);
    }

    /** {@return the cash an order of {@code size} at {@code price} moves: size * price} */
    public static BigDecimal cashAmount(BigDecimal price, int size) {
        return price.multiply(BigDecimal.valueOf(size));
    }
}
