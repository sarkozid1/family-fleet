package hu.familyfleet.insurance;

/** Domain-esemény: új kötvény jött létre (a határidő-modul generálja hozzá a határidőket). */
public record PolicyCreatedEvent(Long policyId) {}
