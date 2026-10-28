package hu.familyfleet.insurance;

import hu.familyfleet.insurance.dto.PolicyCreateRequest;
import hu.familyfleet.insurance.dto.PolicyResponse;
import hu.familyfleet.insurance.dto.PolicyUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "Kötvények", description = "KGFB- és CASCO-kötvények járművenként")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping("/vehicles/{vehicleId}/policies")
    public ResponseEntity<PolicyResponse> create(
            @PathVariable Long vehicleId, @Valid @RequestBody PolicyCreateRequest request) {
        PolicyResponse created = policyService.create(vehicleId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/policies/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** A legfrissebb kötvény elöl; egy járműnek kevés kötvénye van, ezért nem lapozott. */
    @GetMapping("/vehicles/{vehicleId}/policies")
    public List<PolicyResponse> findByVehicle(@PathVariable Long vehicleId) {
        return policyService.findByVehicle(vehicleId);
    }

    @GetMapping("/policies/{id}")
    public PolicyResponse findById(@PathVariable Long id) {
        return policyService.findById(id);
    }

    @PutMapping("/policies/{id}")
    public PolicyResponse update(@PathVariable Long id, @Valid @RequestBody PolicyUpdateRequest request) {
        return policyService.update(id, request);
    }
}
