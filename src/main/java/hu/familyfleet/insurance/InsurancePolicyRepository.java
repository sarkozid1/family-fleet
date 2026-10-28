package hu.familyfleet.insurance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    /** Az {@code @EntityGraph} egy JOIN-nal hozza a biztosítót is, így a lista nem N+1 lekérdezés. */
    @EntityGraph(attributePaths = "insurer")
    List<InsurancePolicy> findByVehicleIdOrderByStartDateDesc(Long vehicleId);

    @EntityGraph(attributePaths = "insurer")
    Optional<InsurancePolicy> findWithInsurerById(Long id);

    List<InsurancePolicy> findByVehicleIdAndPolicyType(Long vehicleId, PolicyType policyType);
}
