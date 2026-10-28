package hu.familyfleet.vehicle;

/**
 * Domain-esemény: új jármű került a nyilvántartásba. A határidő-modul erre iratkozik fel,
 * így a vehicle csomagnak nem kell tudnia a határidőkről.
 */
public record VehicleCreatedEvent(Long vehicleId, boolean generateTaxDeadline) {}
