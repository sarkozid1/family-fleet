package hu.familyfleet.deadline;

import hu.familyfleet.common.web.PageResponse;
import hu.familyfleet.deadline.dto.CompleteDeadlineRequest;
import hu.familyfleet.deadline.dto.CompleteDeadlineResponse;
import hu.familyfleet.deadline.dto.DeadlineCreateRequest;
import hu.familyfleet.deadline.dto.DeadlineResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Határidők", description = "Határidők, közelgők, teljesítés és elvetés")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeadlineController {

    private final DeadlineService deadlineService;

    @PostMapping("/households/{householdId}/deadlines")
    public ResponseEntity<DeadlineResponse> create(
            @PathVariable Long householdId, @Valid @RequestBody DeadlineCreateRequest request) {
        DeadlineResponse created = deadlineService.create(householdId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/deadlines/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Szűrés: {@code ?status=OPEN&type=VEHICLE_TAX&from=2026-01-01&to=2026-12-31}, lapozva, alapból esedékesség szerint. */
    @GetMapping("/households/{householdId}/deadlines")
    public PageResponse<DeadlineResponse> search(
            @PathVariable Long householdId,
            @RequestParam(required = false) DeadlineStatus status,
            @RequestParam(required = false) DeadlineType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(
                            size = 20,
                            sort = {"dueDate", "id"})
                    Pageable pageable) {
        return deadlineService.search(householdId, status, type, from, to, pageable);
    }

    /** A paraméter-annotációkat (@Min, @Max) a Spring MVC beépített metódusvalidációja ellenőrzi. */
    @GetMapping("/households/{householdId}/deadlines/upcoming")
    public List<DeadlineResponse> upcoming(
            @PathVariable Long householdId, @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return deadlineService.upcoming(householdId, days);
    }

    @GetMapping("/deadlines/{id}")
    public DeadlineResponse findById(@PathVariable Long id) {
        return deadlineService.findById(id);
    }

    @PostMapping("/deadlines/{id}/complete")
    public CompleteDeadlineResponse complete(
            @PathVariable Long id, @RequestBody(required = false) CompleteDeadlineRequest request) {
        return deadlineService.complete(id, request);
    }

    @PostMapping("/deadlines/{id}/dismiss")
    public DeadlineResponse dismiss(@PathVariable Long id) {
        return deadlineService.dismiss(id);
    }
}
