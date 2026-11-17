package ar.com.arieldebrouvier.cocos.models;

/**
 * Outcome of placing an order: the stored order together with its user and instrument.
 *
 * @param rejectionReason why the order was REJECTED; null otherwise. Not persisted: the orders table has no column for it.
 */
public record PlacedOrder(Order order, User user, Instrument instrument, String rejectionReason) {
}
