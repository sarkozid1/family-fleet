package hu.familyfleet.household;

import hu.familyfleet.household.dto.ShareRequest;
import hu.familyfleet.household.dto.SharingEntryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Megosztás", description = "Háztartás megosztása más felhasználókkal, meghívók")
@RestController
@RequestMapping("/api/households/{householdId}/sharing")
@RequiredArgsConstructor
public class HouseholdSharingController {

    private final HouseholdSharingService sharingService;

    @GetMapping
    public List<SharingEntryResponse> list(@PathVariable Long householdId) {
        return sharingService.list(householdId);
    }

    /** 201: regisztrált címnél aktív hozzáférés, egyébként függő meghívó jön létre (a válasz status mezője mutatja). */
    @PostMapping
    public ResponseEntity<SharingEntryResponse> share(
            @PathVariable Long householdId, @Valid @RequestBody ShareRequest request) {
        SharingEntryResponse created = sharingService.share(householdId, request.email());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> revokeUser(@PathVariable Long householdId, @PathVariable Long userId) {
        sharingService.revokeUser(householdId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(@PathVariable Long householdId, @PathVariable Long invitationId) {
        sharingService.revokeInvitation(householdId, invitationId);
        return ResponseEntity.noContent().build();
    }
}
