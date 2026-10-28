package hu.familyfleet.common.exception;

/**
 * A kért erőforrás nem létezik. A {@link GlobalExceptionHandler} 404-re fordítja.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("%s nem található (id: %s)".formatted(resourceName, id));
    }
}
