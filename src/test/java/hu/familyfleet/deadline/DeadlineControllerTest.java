package hu.familyfleet.deadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.web.PageResponse;
import hu.familyfleet.deadline.dto.CompleteDeadlineRequest;
import hu.familyfleet.deadline.dto.CompleteDeadlineResponse;
import hu.familyfleet.deadline.dto.DeadlineResponse;
import hu.familyfleet.support.WebSecurityTestConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(WebSecurityTestConfig.class)
@WithMockUser
@WebMvcTest(DeadlineController.class)
class DeadlineControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeadlineService deadlineService;

    @Test
    void createReturns201WithDeadlineLocation() throws Exception {
        given(deadlineService.create(eq(1L), any())).willReturn(response(9L, DeadlineStatus.OPEN));

        mockMvc.perform(post("/api/households/1/deadlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "VEHICLE_TAX", "vehicleId": 3 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/deadlines/9")));
    }

    @Test
    void createWithoutTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/households/1/deadlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dueDate": "2026-12-01" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));
    }

    @Test
    void searchBindsFiltersAndDefaultsToDueDateOrder() throws Exception {
        given(deadlineService.search(eq(1L), any(), any(), any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(response(9L, DeadlineStatus.OPEN)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/households/1/deadlines")
                        .param("status", "OPEN")
                        .param("type", "VEHICLE_TAX")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deadlineService)
                .search(
                        eq(1L),
                        eq(DeadlineStatus.OPEN),
                        eq(DeadlineType.VEHICLE_TAX),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 12, 31)),
                        pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("dueDate", "id"));
    }

    @Test
    void unknownStatusValueReturns400() throws Exception {
        mockMvc.perform(get("/api/households/1/deadlines").param("status", "PENDING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upcomingDefaultsTo30Days() throws Exception {
        given(deadlineService.upcoming(1L, 30)).willReturn(List.of());

        mockMvc.perform(get("/api/households/1/deadlines/upcoming")).andExpect(status().isOk());

        verify(deadlineService).upcoming(1L, 30);
    }

    @Test
    void upcomingDaysOutOfRangeReturns400WithParameterError() throws Exception {
        mockMvc.perform(get("/api/households/1/deadlines/upcoming").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("days"));
        mockMvc.perform(get("/api/households/1/deadlines/upcoming").param("days", "366"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deadlineService);
    }

    @Test
    void completeWorksWithoutBody() throws Exception {
        given(deadlineService.complete(eq(9L), isNull()))
                .willReturn(new CompleteDeadlineResponse(
                        response(9L, DeadlineStatus.DONE), response(10L, DeadlineStatus.OPEN)));

        mockMvc.perform(post("/api/deadlines/9/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed.status").value("DONE"))
                .andExpect(jsonPath("$.next.id").value(10));
    }

    @Test
    void completeAcceptsExplicitNextDueDate() throws Exception {
        given(deadlineService.complete(9L, new CompleteDeadlineRequest(LocalDate.of(2036, 5, 1))))
                .willReturn(new CompleteDeadlineResponse(response(9L, DeadlineStatus.DONE), null));

        mockMvc.perform(post("/api/deadlines/9/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nextDueDate": "2036-05-01" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next").isEmpty());
    }

    @Test
    void dismissingClosedDeadlineReturns409() throws Exception {
        given(deadlineService.dismiss(9L)).willThrow(new ConflictException("Csak nyitott határidőt lehet elvetni."));

        mockMvc.perform(post("/api/deadlines/9/dismiss"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Csak nyitott határidőt lehet elvetni."));
    }

    private static DeadlineResponse response(Long id, DeadlineStatus status) {
        return new DeadlineResponse(
                id,
                1L,
                3L,
                null,
                null,
                DeadlineType.VEHICLE_TAX,
                "Gépjárműadó – ABC123",
                LocalDate.of(2027, 3, 15),
                status,
                138,
                status == DeadlineStatus.DONE ? NOW : null,
                null,
                0L,
                NOW,
                NOW);
    }
}
