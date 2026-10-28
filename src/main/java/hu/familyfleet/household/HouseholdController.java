package hu.familyfleet.household;

import hu.familyfleet.household.dto.HouseholdRequest;
import hu.familyfleet.household.dto.HouseholdResponse;
import hu.familyfleet.household.dto.MemberRequest;
import hu.familyfleet.household.dto.MemberResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "Háztartások", description = "Háztartások és családtagok")
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @PostMapping
    public ResponseEntity<HouseholdResponse> create(@Valid @RequestBody HouseholdRequest request) {
        HouseholdResponse created = householdService.create(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @GetMapping
    public List<HouseholdResponse> findAll() {
        return householdService.findAll();
    }

    @GetMapping("/{id}")
    public HouseholdResponse findById(@PathVariable Long id) {
        return householdService.findById(id);
    }

    @PutMapping("/{id}")
    public HouseholdResponse update(@PathVariable Long id, @Valid @RequestBody HouseholdRequest request) {
        return householdService.update(id, request);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable Long id, @Valid @RequestBody MemberRequest request) {
        MemberResponse created = householdService.addMember(id, request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> findMembers(@PathVariable Long id) {
        return householdService.findMembers(id);
    }

    @GetMapping("/{id}/members/{memberId}")
    public MemberResponse findMember(@PathVariable Long id, @PathVariable Long memberId) {
        return householdService.findMember(id, memberId);
    }

    /** Az aktuális kérés URL-jéhez fűzi az új azonosítót: POST /api/households -> /api/households/42. */
    private static URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
