package hu.familyfleet.common.exception;

/**
 * A művelet a kérés tartalma miatt nem engedélyezett (pl. regisztráció egy nem engedélyezett
 * e-mail-címmel). A {@link GlobalExceptionHandler} 403-ra fordítja, az üzenettel együtt.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
