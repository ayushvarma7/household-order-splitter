package com.householdsplitter.core.splitwise;

/** A person on Splitwise. Only the fields this app uses. */
public final class SplitwiseUser {

    private final long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String defaultCurrency;

    public SplitwiseUser(long id, String firstName, String lastName, String email,
                         String defaultCurrency) {
        this.id = id;
        this.firstName = firstName == null ? "" : firstName;
        this.lastName = lastName == null ? "" : lastName;
        this.email = email == null ? "" : email;
        this.defaultCurrency = defaultCurrency == null ? "" : defaultCurrency;
    }

    public long id() {
        return id;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String email() {
        return email;
    }

    /** Empty when the service did not say; only the signed-in user carries one. */
    public String defaultCurrency() {
        return defaultCurrency;
    }

    public String displayName() {
        String full = (firstName + " " + lastName).trim();
        return full.isEmpty() ? email : full;
    }
}
