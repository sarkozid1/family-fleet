package hu.familyfleet.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @ParameterizedTest
    @ValueSource(strings = {"abc-123", "6f1c2a0e-7b0d-4c55-9f1e-2a3b4c5d6e7f", "gateway.req_42"})
    void keepsSafeIncomingId(String incoming) {
        assertThat(RequestIdFilter.resolveRequestId(incoming)).isEqualTo(incoming);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "has space",
                "line\nbreak",
                "<script>",
                "a{65 chars}aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    void replacesMissingOrUnsafeIdWithUuid(String incoming) {
        String resolved = RequestIdFilter.resolveRequestId(incoming);

        assertThat(resolved).isNotEqualTo(incoming).matches("[0-9a-f-]{36}");
    }

    @Test
    void putsIdIntoMdcDuringRequestAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/households");
        request.addHeader(RequestIdFilter.HEADER, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });

        assertThat(seenInChain).hasValue("req-1");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("req-1");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
