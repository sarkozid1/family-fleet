package hu.familyfleet.insurance;

import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.insurance.dto.InsurerRequest;
import hu.familyfleet.insurance.dto.InsurerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsurerService {

    private final InsurerRepository insurerRepository;

    public List<InsurerResponse> findAll() {
        return insurerRepository.findAll(Sort.by("name")).stream().map(InsurerResponse::from).toList();
    }

    public InsurerResponse findById(Long id) {
        return insurerRepository.findById(id)
                .map(InsurerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Biztosító", id));
    }

    /** Kis- és nagybetűtől függetlenül egyedi név; versenyhelyzetben az {@code uq_insurer_name} véd. */
    @Transactional
    public InsurerResponse create(InsurerRequest request) {
        if (insurerRepository.existsByNameIgnoreCase(request.name().strip())) {
            throw new ConflictException("Ilyen nevű biztosító már létezik: " + request.name().strip());
        }
        Insurer insurer = insurerRepository.save(new Insurer(request.name(), request.phone(), request.website()));
        return InsurerResponse.from(insurer);
    }
}
