package hu.familyfleet.security;

/** Domain-esemény: új felhasználó regisztrált (a household csomag ekkor váltja be a meghívóit). */
public record UserRegisteredEvent(Long userId, String email) {}
