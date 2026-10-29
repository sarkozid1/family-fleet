package hu.familyfleet.common.exception;

import hu.familyfleet.common.logging.RequestIdFilter;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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

    private static final String DEFAULT_CONFLICT_MESSAGE =
            "A művelet ütközik a meglévő adatokkal (pl. már létező érték).";

    /**
     * Az elnevezett megkötésekhez (lásd a Flyway-migrációkat) emberi üzenet tartozik. Ez csak a
     * versenyhelyzetek végső védővonala: normál esetben a service már előbb, barátságosan jelez.
     */
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
            "uq_vehicle_household_plate", "Ez a rendszám már szerepel ebben a háztartásban.",
            "uq_insurer_name", "Ilyen nevű biztosító már létezik.",
            "ex_policy_kgfb_no_overlap", "A járműnek erre az időszakra már van érvényes KGFB-kötvénye.",
            "uq_household_access_user_household", "Ez a felhasználó már hozzáfér a háztartáshoz.",
            "uq_household_invitation_household_email", "Erre az e-mail-címre már van meghívó ebbe a háztartásba.");

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

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A kérés adatai érvénytelenek.");
        problem.setTitle("Validációs hiba");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Path- és query-paraméterek validációja (pl. {@code ?days=0} a {@code @Min(1)} ellen): ugyanaz a forma. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A kérés paraméterei érvénytelenek.");
        problem.setTitle("Validációs hiba");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 401: hiányzó, lejárt vagy hibás token, illetve rossz e-mail/jelszó. A részletes ok (pl. "lejárt
     * aláírás") csak a logba kerül; a {@code WWW-Authenticate} fejléc jelzi a kliensnek a sémát.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        log.debug("Authentication failed: {}", ex.getMessage());
        String detail = ex instanceof BadCredentialsException
                ? "Hibás e-mail-cím vagy jelszó."
                : "A kéréshez érvényes hozzáférési token szükséges (Authorization: Bearer ...).";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Hitelesítés szükséges");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.UNAUTHORIZED, request);
    }

    /** 403: be van jelentkezve, de a művelethez nincs szerepköre (pl. biztosító felvétele USER-ként). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return problem(
                ex, HttpStatus.FORBIDDEN, "Nincs jogosultság", "Ehhez a művelethez nincs jogosultságod.", request);
    }

    /** 403 saját, a kliensnek szóló üzenettel (pl. nem engedélyezett regisztráció). */
    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Object> handleForbidden(ForbiddenException ex, WebRequest request) {
        return problem(ex, HttpStatus.FORBIDDEN, "Nincs jogosultság", ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, "Nem található", ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<Object> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, "Üzleti szabály megsértése", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> handleConflict(ConflictException ex, WebRequest request) {
        return problem(ex, HttpStatus.CONFLICT, "Ütközés", ex.getMessage(), request);
    }

    /** Ismeretlen rendezési mező a lapozásnál ({@code ?sort=nincsIlyen}): a kliens hibája, nem 500. */
    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<Object> handleUnknownProperty(PropertyReferenceException ex, WebRequest request) {
        return problem(
                ex,
                HttpStatus.BAD_REQUEST,
                "Érvénytelen paraméter",
                "Ismeretlen mező: " + ex.getPropertyName(),
                request);
    }

    /**
     * Adatbázis-megkötés sérült (pl. egyedi kulcs). Az eredeti üzenet SQL-t és sémarészleteket
     * tartalmaz, ezért csak a logba kerül, a kliens általános üzenetet kap.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String detail = ConstraintNames.extract(ex)
                .map(name -> CONSTRAINT_MESSAGES.getOrDefault(name, DEFAULT_CONFLICT_MESSAGE))
                .orElse(DEFAULT_CONFLICT_MESSAGE);
        return problem(ex, HttpStatus.CONFLICT, "Ütközés", detail, request);
    }

    /** Valaki más időközben módosította ugyanazt a rekordot (optimista zárolás). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        log.info("Optimistic locking conflict: {}", ex.getMessage());
        return problem(
                ex,
                HttpStatus.CONFLICT,
                "Párhuzamos módosítás",
                "Az adatot időközben valaki más módosította. Töltsd le újra, és próbáld meg ismét.",
                request);
    }

    /** Végső védőháló: a kliens nem kap stack trace-t vagy belső üzenetet, a részletek a logba mennek. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return problem(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Váratlan hiba",
                "Váratlan hiba történt. Kérjük, próbáld újra később.",
                request);
    }

    /**
     * Minden ProblemDetail-be bekerül a kérésazonosító: a felhasználó ezzel jelentheti a hibát,
     * mi pedig ezzel keressük meg a hozzá tartozó logsorokat.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (response != null && response.getBody() instanceof ProblemDetail problem && requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return response;
    }

    private ResponseEntity<Object> problem(
            Exception ex, HttpStatus status, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }
}
