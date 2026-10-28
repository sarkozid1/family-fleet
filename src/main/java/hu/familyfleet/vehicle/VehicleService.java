package hu.familyfleet.vehicle;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.common.web.PageResponse;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdAccessGuard;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.household.HouseholdMemberRepository;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.vehicle.dto.VehicleCreateRequest;
import hu.familyfleet.vehicle.dto.VehicleResponse;
import hu.familyfleet.vehicle.dto.VehicleUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final HouseholdAccessGuard accessGuard;

    @Transactional
    public VehicleResponse create(Long householdId, VehicleCreateRequest request) {
        accessGuard.requireHousehold(householdId);
        Household household = householdRepository
                .findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Háztartás", householdId));
        ensurePlateIsFree(householdId, request.licensePlate(), null);

        Vehicle vehicle =
                new Vehicle(household, request.type(), request.brand(), request.model(), request.licensePlate());
        vehicle.updateDetails(
                request.type(), request.brand(), request.model(), request.vin(), request.productionYear());
        if (request.currentMileage() != null) {
            vehicle.updateMileage(request.currentMileage());
        }
        vehicle.assignOwner(findOwner(householdId, request.ownerId()));
        Vehicle saved = vehicleRepository.save(vehicle);
        // Szinkron esemény ugyanabban a tranzakcióban: ha a határidő-generálás elbukik, a jármű sem jön létre.
        eventPublisher.publishEvent(new VehicleCreatedEvent(saved.getId(), request.shouldGenerateTaxDeadline()));
        return VehicleResponse.from(saved);
    }

    public PageResponse<VehicleResponse> findByHousehold(Long householdId, Pageable pageable) {
        accessGuard.requireHousehold(householdId);
        return PageResponse.from(vehicleRepository.findByHouseholdId(householdId, pageable), VehicleResponse::from);
    }

    public VehicleResponse findById(Long id) {
        return VehicleResponse.from(getVehicle(id));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleUpdateRequest request) {
        Vehicle vehicle = getVehicle(id);
        // A kliens a nála lévő verziót küldi vissza. Ha eltér, valaki közben módosította a járművet:
        // nem írjuk felül csendben a másik változtatását ("lost update").
        if (!vehicle.getVersion().equals(request.version())) {
            throw new ObjectOptimisticLockingFailureException(Vehicle.class, id);
        }
        Long householdId = vehicle.getHousehold().getId();
        ensurePlateIsFree(householdId, request.licensePlate(), id);

        vehicle.updateDetails(
                request.type(), request.brand(), request.model(), request.vin(), request.productionYear());
        vehicle.changeLicensePlate(request.licensePlate());
        vehicle.updateMileage(request.currentMileage());
        vehicle.assignOwner(findOwner(householdId, request.ownerId()));

        // A flush kiírja a változást és megnöveli a verziót, így a válasz már az új verziót tartalmazza.
        vehicleRepository.flush();
        return VehicleResponse.from(vehicle);
    }

    /** Nincs CASCADE: a kötvények és határidők a jármű történetének részei, nem törlődhetnek észrevétlenül. */
    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = getVehicle(id);
        if (vehicleRepository.hasPolicies(id)) {
            throw new ConflictException("A jármű nem törölhető, mert biztosítási kötvény tartozik hozzá.");
        }
        if (vehicleRepository.hasDeadlines(id)) {
            throw new ConflictException("A jármű nem törölhető, mert határidő tartozik hozzá.");
        }
        vehicleRepository.delete(vehicle);
    }

    private Vehicle getVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Jármű", id));
        // Idegen háztartás járműve ugyanúgy "nem található", mint a nem létező (ADR-0003).
        accessGuard.requireAccess(vehicle.getHousehold().getId(), "Jármű", id);
        return vehicle;
    }

    /**
     * A barátságos 409-es üzenethez előre ellenőrzünk. Párhuzamos kéréseknél a két ellenőrzés
     * egyszerre is átmehet: ott az adatbázis {@code uq_vehicle_household_plate} megkötése a végső védelem.
     */
    private void ensurePlateIsFree(Long householdId, String licensePlate, Long excludedVehicleId) {
        String plate = Vehicle.normalizePlate(licensePlate);
        boolean taken = excludedVehicleId == null
                ? vehicleRepository.existsByHouseholdIdAndLicensePlate(householdId, plate)
                : vehicleRepository.existsByHouseholdIdAndLicensePlateAndIdNot(householdId, plate, excludedVehicleId);
        if (taken) {
            throw new ConflictException("A(z) %s rendszámú jármű már szerepel ebben a háztartásban.".formatted(plate));
        }
    }

    /** Más háztartás tagjára ugyanaz az üzenet, mint a nem létezőre: ne derüljön ki, hogy az azonosító létezik. */
    private HouseholdMember findOwner(Long householdId, Long ownerId) {
        if (ownerId == null) {
            return null;
        }
        return memberRepository
                .findByIdAndHouseholdId(ownerId, householdId)
                .orElseThrow(() -> new BusinessRuleException(
                        "A tulajdonos (id: %d) nem tagja a jármű háztartásának.".formatted(ownerId)));
    }
}
