package ar.com.arieldebrouvier.cocos.models;

public enum OrderSide {
    BUY,
    SELL,
    CASH_IN,
    CASH_OUT;

    public boolean isCashTransfer() {
        return this == CASH_IN || this == CASH_OUT;
    }
}
