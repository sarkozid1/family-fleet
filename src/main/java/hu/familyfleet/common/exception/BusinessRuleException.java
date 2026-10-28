package hu.familyfleet.common.exception;

/**
 * Üzleti szabály sérült (pl. csökkenő km-óra). A kérés formailag helyes, de a tartalma nem
 * megengedett. A {@link GlobalExceptionHandler} 400-ra fordítja.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
