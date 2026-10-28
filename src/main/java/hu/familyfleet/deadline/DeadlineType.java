package hu.familyfleet.deadline;

/** A határidő típusa és az, hogy mihez kell kapcsolódnia (lásd ADR-0001, ADR-0002). */
public enum DeadlineType {
    TECHNICAL_INSPECTION(RequiredLink.VEHICLE),
    INSURANCE_PAYMENT(RequiredLink.POLICY),
    KGFB_ANNIVERSARY(RequiredLink.POLICY),
    VEHICLE_TAX(RequiredLink.VEHICLE),
    MOTORWAY_VIGNETTE(RequiredLink.VEHICLE),
    OIL_CHANGE(RequiredLink.VEHICLE),
    TIRE_CHANGE(RequiredLink.VEHICLE),
    DRIVING_LICENCE(RequiredLink.MEMBER),
    OTHER(RequiredLink.NONE);

    public enum RequiredLink {
        VEHICLE,
        MEMBER,
        POLICY,
        NONE
    }

    private final RequiredLink requiredLink;

    DeadlineType(RequiredLink requiredLink) {
        this.requiredLink = requiredLink;
    }

    public RequiredLink requiredLink() {
        return requiredLink;
    }
}
