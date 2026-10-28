package hu.familyfleet.insurance;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.insurance.dto.PolicyCreateRequest;
import hu.familyfleet.insurance.dto.PolicyResponse;
import hu.familyfleet.insurance.dto.PolicyUpdateRequest;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final InsurancePolicyRepository policyRepository;
    private final InsurerRepository insurerRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    @Transactional
    public PolicyResponse create(Long vehicleId, PolicyCreateRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Jármű", vehicleId));
        InsurancePolicy policy = new InsurancePolicy(vehicle, findInsurer(request.insurerId()), request.policyType(),
                request.policyNumber(), request.startDate(), request.endDate(), request.annualPremium(),
                request.paymentFrequency());

        policy.ensureNoOverlappingKgfb(policyRepository.findByVehicleIdAndPolicyType(vehicleId, PolicyType.KGFB));
        return PolicyResponse.from(policyRepository.save(policy), today());
    }

    public List<PolicyResponse> findByVehicle(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Jármű", vehicleId);
        }
        LocalDate today = today();
        return policyRepository.findByVehicleIdOrderByStartDateDesc(vehicleId).stream()
                .map(policy -> PolicyResponse.from(policy, today))
                .toList();
    }

    public PolicyResponse findById(Long id) {
        return PolicyResponse.from(getPolicy(id), today());
    }

    @Transactional
    public PolicyResponse update(Long id, PolicyUpdateRequest request) {
        InsurancePolicy policy = getPolicy(id);
        if (!policy.getVersion().equals(request.version())) {
            throw new ObjectOptimisticLockingFailureException(InsurancePolicy.class, id);
        }
        // A többi kötvényt a módosítás ELŐTT kérdezzük le: a lekérdezés előtti automatikus flush különben
        // már az új dátumokat írná ki, és a domain-szabály helyett az adatbázis-megkötés jelezne.
        List<InsurancePolicy> kgfbPolicies =
                policyRepository.findByVehicleIdAndPolicyType(policy.getVehicle().getId(), PolicyType.KGFB);
        policy.changeTerms(findInsurer(request.insurerId()), request.policyNumber(), request.startDate(),
                request.endDate(), request.annualPremium(), request.paymentFrequency());
        policy.ensureNoOverlappingKgfb(kgfbPolicies);

        policyRepository.flush();
        return PolicyResponse.from(policy, today());
    }

    private InsurancePolicy getPolicy(Long id) {
        return policyRepository.findWithInsurerById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kötvény", id));
    }

    /** A kérés törzsében hivatkozott, nem létező biztosító a kérés hibája (400), nem hiányzó URL-erőforrás (404). */
    private Insurer findInsurer(Long insurerId) {
        return insurerRepository.findById(insurerId)
                .orElseThrow(() -> new BusinessRuleException("Nem létező biztosító (id: %d).".formatted(insurerId)));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
