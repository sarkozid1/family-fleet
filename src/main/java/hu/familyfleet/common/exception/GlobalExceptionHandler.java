package hu.familyfleet.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Egységes hibaválaszok RFC 9457 {@link ProblemDetail} formában.
 *
 * <p>Az ősosztály a Spring MVC saját kivételeit (hibás JSON, rossz típusú path-változó,
 * nem támogatott metódus stb.) már ProblemDetailre fordítja; itt a validációs választ
 * bővítjük mezőnkénti hibalistával, és a saját/adatbázis-kivételeket kezeljük.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Egy mezőhöz (vagy az egész objektumhoz) tartozó validációs hiba a válaszban. */
    public record FieldViolation(String field, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A kérés adatai érvénytelenek.");
        problem.setTitle("Validációs hiba");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, "Nem található", ex.getMessage(), request);
    }

    /**
     * Adatbázis-megkötés sérült (pl. egyedi kulcs). Az eredeti üzenet SQL-t és sémarészleteket
     * tartalmaz, ezért csak a logba kerül, a kliens általános üzenetet kap.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(ex, HttpStatus.CONFLICT, "Ütközés",
                "A művelet ütközik a meglévő adatokkal (pl. már létező érték).", request);
    }

    /** Valaki más időközben módosította ugyanazt a rekordot (optimista zárolás). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        log.info("Optimistic locking conflict: {}", ex.getMessage());
        return problem(ex, HttpStatus.CONFLICT, "Párhuzamos módosítás",
                "Az adatot időközben valaki más módosította. Töltsd le újra, és próbáld meg ismét.", request);
    }

    /** Végső védőháló: a kliens nem kap stack trace-t vagy belső üzenetet, a részletek a logba mennek. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Váratlan hiba",
                "Váratlan hiba történt. Kérjük, próbáld újra később.", request);
    }

    private ResponseEntity<Object> problem(
            Exception ex, HttpStatus status, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }
}
