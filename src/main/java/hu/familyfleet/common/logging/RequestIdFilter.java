package hu.familyfleet.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minden kéréshez azonosítót rendel, és az MDC-be teszi, így a kérés összes logsora
 * összekereshető. A kliens (vagy egy előtte álló proxy / API gateway) küldhet saját
 * {@code X-Request-Id}-t; ha nincs vagy gyanús, újat generálunk. A válaszban visszaküldjük,
 * hogy a felhasználó hibabejelentésében hivatkozni lehessen rá.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Csak rövid, biztonságos karakterek: a logba kerülő kliensadat ne törhesse meg a logsort (log injection). */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // A szálak újrahasznosulnak (szálkészlet): az MDC-t mindig takarítani kell.
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveRequestId(String incoming) {
        return incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }
}
