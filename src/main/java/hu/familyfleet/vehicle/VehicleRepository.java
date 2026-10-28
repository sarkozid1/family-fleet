package hu.familyfleet.vehicle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Page<Vehicle> findByHouseholdId(Long householdId, Pageable pageable);

    boolean existsByHouseholdIdAndLicensePlate(Long householdId, String licensePlate);

    boolean existsByHouseholdIdAndLicensePlateAndIdNot(Long householdId, String licensePlate, Long id);

    /*
     * A függő adatok ellenőrzése JPQL-lel, hogy a vehicle csomag ne importálja az insurance és
     * deadline csomagot (azok függnek a vehicle-től, így elkerüljük a csomagszintű körkörös függést).
     */
    @Query("select count(p) > 0 from InsurancePolicy p where p.vehicle.id = :vehicleId")
    boolean hasPolicies(Long vehicleId);

    @Query("select count(d) > 0 from Deadline d where d.vehicle.id = :vehicleId")
    boolean hasDeadlines(Long vehicleId);
}
