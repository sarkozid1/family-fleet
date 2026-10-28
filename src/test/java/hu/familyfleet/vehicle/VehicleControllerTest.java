package hu.familyfleet.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.web.PageResponse;
import hu.familyfleet.support.WebSecurityTestConfig;
import hu.familyfleet.vehicle.dto.VehicleResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(WebSecurityTestConfig.class)
@WithMockUser
@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final VehicleResponse SKODA = new VehicleResponse(
            5L, 1L, null, VehicleType.CAR, "Skoda", "Octavia", "ABC123", null, (short) 2018, 120_000, 0L, NOW, NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService vehicleService;

    @Test
    void createReturns201WithVehicleLocation() throws Exception {
        given(vehicleService.create(eq(1L), any())).willReturn(SKODA);

        mockMvc.perform(post("/api/households/1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CAR",
                                  "brand": "Skoda",
                                  "model": "Octavia",
                                  "licensePlate": "abc-123",
                                  "productionYear": 2018,
                                  "currentMileage": 120000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/vehicles/5")))
                .andExpect(jsonPath("$.licensePlate").value("ABC123"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC123", "abc-123", "ABC 123", "AABC123", "AA-BC-123", "aa bc 123"})
    void acceptsHungarianPlateFormats(String plate) throws Exception {
        given(vehicleService.create(eq(1L), any())).willReturn(SKODA);

        mockMvc.perform(post("/api/households/1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(plate)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB123", "ABCD1234", "123ABC", "ABC-12", "AA-BC-123-X", "ÁBC123"})
    void rejectsInvalidPlates(String plate) throws Exception {
        mockMvc.perform(post("/api/households/1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(plate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("licensePlate")));
    }

    @Test
    void missingRequiredFieldsAreListed() throws Exception {
        mockMvc.perform(post("/api/households/1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "licensePlate": "ABC123", "productionYear": 1850, "currentMileage": -5 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("type")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("brand")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("model")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("productionYear")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("currentMileage")));
    }

    @Test
    void listUsesPlateSortByDefault() throws Exception {
        given(vehicleService.findByHousehold(eq(1L), any()))
                .willReturn(new PageResponse<>(List.of(SKODA), 0, 20, 1, 1));

        mockMvc.perform(get("/api/households/1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].licensePlate").value("ABC123"))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(vehicleService).findByHousehold(eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("licensePlate"));
    }

    @Test
    void updateWithoutVersionReturns400() throws Exception {
        mockMvc.perform(put("/api/vehicles/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CAR", "brand": "Skoda", "model": "Octavia",
                                  "licensePlate": "ABC123", "currentMileage": 130000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
    }

    @Test
    void staleVersionReturns409() throws Exception {
        given(vehicleService.update(eq(5L), any()))
                .willThrow(new ObjectOptimisticLockingFailureException(Vehicle.class, 5L));

        mockMvc.perform(put("/api/vehicles/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CAR", "brand": "Skoda", "model": "Octavia",
                                  "licensePlate": "ABC123", "currentMileage": 130000, "version": 3
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Párhuzamos módosítás"));
    }

    @Test
    void businessRuleViolationReturns400WithMessage() throws Exception {
        given(vehicleService.update(eq(5L), any()))
                .willThrow(new BusinessRuleException("A km-óra állása nem csökkenhet."));

        mockMvc.perform(put("/api/vehicles/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CAR", "brand": "Skoda", "model": "Octavia",
                                  "licensePlate": "ABC123", "currentMileage": 1, "version": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A km-óra állása nem csökkenhet."));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/vehicles/5")).andExpect(status().isNoContent());

        verify(vehicleService).delete(5L);
    }

    @Test
    void deleteWithDependentsReturns409() throws Exception {
        willThrow(new ConflictException("A jármű nem törölhető, mert biztosítási kötvény tartozik hozzá."))
                .given(vehicleService)
                .delete(5L);

        mockMvc.perform(delete("/api/vehicles/5"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail").value("A jármű nem törölhető, mert biztosítási kötvény tartozik hozzá."));
    }

    private static String vehicleJson(String plate) {
        return """
                { "type": "CAR", "brand": "Skoda", "model": "Octavia", "licensePlate": "%s" }
                """.formatted(plate);
    }
}
