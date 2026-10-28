package hu.familyfleet.insurance;

public enum PaymentFrequency {
    ANNUAL(12, "éves"),
    SEMI_ANNUAL(6, "féléves"),
    QUARTERLY(3, "negyedéves"),
    MONTHLY(1, "havi");

    private final int monthsBetweenPayments;
    private final String displayName;

    PaymentFrequency(int monthsBetweenPayments, String displayName) {
        this.monthsBetweenPayments = monthsBetweenPayments;
        this.displayName = displayName;
    }

    public int monthsBetweenPayments() {
        return monthsBetweenPayments;
    }

    public String displayName() {
        return displayName;
    }
}
