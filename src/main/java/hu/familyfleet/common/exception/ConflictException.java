package hu.familyfleet.common.exception;

/**
 * A kérés ütközik az erőforrás(ok) jelenlegi állapotával (pl. már létező rendszám, törlés
 * függő adatok mellett). A {@link GlobalExceptionHandler} 409-re fordítja.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
