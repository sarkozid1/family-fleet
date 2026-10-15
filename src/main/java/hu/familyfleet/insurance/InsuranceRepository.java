package hu.familyfleet.insurance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceRepository extends JpaRepository<Insurer, Long> {
}