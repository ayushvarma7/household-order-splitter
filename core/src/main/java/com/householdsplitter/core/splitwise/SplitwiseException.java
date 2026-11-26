package com.householdsplitter.core.splitwise;

/** Anything that stopped a Splitwise call from succeeding, with a message fit to show. */
public class SplitwiseException extends Exception {

    private final int status;

    public SplitwiseException(String message) {
        this(message, 0);
    }

    public SplitwiseException(String message, int status) {
        super(message);
        this.status = status;
    }

    /** The HTTP status, or 0 when the failure was not an HTTP one. */
    public int status() {
        return status;
    }

    /** A 401 means the key was wrong or has been revoked, so the user must reconnect. */
    public boolean isUnauthorised() {
        return status == 401;
    }
}
