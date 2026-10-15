package hu.familyfleet.insurance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurerRepository extends JpaRepository<Insurer, Long> {
}