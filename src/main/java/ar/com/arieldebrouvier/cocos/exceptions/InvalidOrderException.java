package ar.com.arieldebrouvier.cocos.exceptions;

/**
 * The order is inconsistent or can't be priced (e.g. both size and amount given, LIMIT without price,
 * no market price). Such orders are refused outright and not stored, unlike orders that are well-formed
 * but unaffordable, which are stored as REJECTED.
 */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
