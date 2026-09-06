package com.householdsplitter.core.calc;

/** SPEC 6.4.1: an order with nobody in on it cannot be split. */
public class NoParticipantsException extends RuntimeException {

    public NoParticipantsException() {
        super("This order has no participants");
    }
}
