package ar.com.arieldebrouvier.cocos.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "marketdata")
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    private BigDecimal high;

    private BigDecimal low;

    private BigDecimal open;

    private BigDecimal close;

    @Column(name = "previousclose")
    private BigDecimal previousClose;

    private LocalDate date;

    protected MarketData() {
    }

    public Integer getId() {
        return id;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public LocalDate getDate() {
        return date;
    }
}
