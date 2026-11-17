package ar.com.arieldebrouvier.cocos.exceptions;

/** A referenced domain object (account, instrument, ...) doesn't exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
