package hu.familyfleet.insurance;

import hu.familyfleet.insurance.dto.InsurerRequest;
import hu.familyfleet.insurance.dto.InsurerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/insurers")
@RequiredArgsConstructor
public class InsurerController {

    private final InsurerService insurerService;

    @GetMapping
    public List<InsurerResponse> findAll() {
        return insurerService.findAll();
    }

    @GetMapping("/{id}")
    public InsurerResponse findById(@PathVariable Long id) {
        return insurerService.findById(id);
    }

    @PostMapping
    public ResponseEntity<InsurerResponse> create(@Valid @RequestBody InsurerRequest request) {
        InsurerResponse created = insurerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
