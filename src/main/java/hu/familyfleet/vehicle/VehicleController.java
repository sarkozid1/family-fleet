package hu.familyfleet.vehicle;

import hu.familyfleet.common.web.PageResponse;
import hu.familyfleet.vehicle.dto.VehicleCreateRequest;
import hu.familyfleet.vehicle.dto.VehicleResponse;
import hu.familyfleet.vehicle.dto.VehicleUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Járművek", description = "Járművek háztartásonként, optimista zárolással")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping("/households/{householdId}/vehicles")
    public ResponseEntity<VehicleResponse> create(
            @PathVariable Long householdId, @Valid @RequestBody VehicleCreateRequest request) {
        VehicleResponse created = vehicleService.create(householdId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/vehicles/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Alapból 20 elem oldalanként, rendszám szerint; felülírható pl. {@code ?page=1&size=5&sort=brand,desc}. */
    @GetMapping("/households/{householdId}/vehicles")
    public PageResponse<VehicleResponse> findByHousehold(
            @PathVariable Long householdId, @PageableDefault(size = 20, sort = "licensePlate") Pageable pageable) {
        return vehicleService.findByHousehold(householdId, pageable);
    }

    @GetMapping("/vehicles/{id}")
    public VehicleResponse findById(@PathVariable Long id) {
        return vehicleService.findById(id);
    }

    @PutMapping("/vehicles/{id}")
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleUpdateRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
