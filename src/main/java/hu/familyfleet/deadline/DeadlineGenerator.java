package hu.familyfleet.deadline;

import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.InsurancePolicyRepository;
import hu.familyfleet.insurance.PolicyCreatedEvent;
import hu.familyfleet.insurance.PolicyType;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleCreatedEvent;
import hu.familyfleet.vehicle.VehicleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Határidők automatikus létrehozása (ADR-0002): jármű- és kötvényeseményekre, alapértelmezett
 * esedékesség számítására és teljesítés utáni ismétlődésre.
 *
 * <p>Az {@code @EventListener} szinkron, a kiváltó service tranzakciójában fut: a jármű/kötvény
 * és a hozzá generált határidők együtt mentődnek vagy együtt görgetődnek vissza.
 */
@Component
@RequiredArgsConstructor
public class DeadlineGenerator {

    private final DeadlineRepository deadlineRepository;
    private final VehicleRepository vehicleRepository;
    private final InsurancePolicyRepository policyRepository;
    private final Clock clock;

    @EventListener
    void onVehicleCreated(VehicleCreatedEvent event) {
        if (!event.generateTaxDeadline()) {
            return;
        }
        Vehicle vehicle = vehicleRepository
                .findById(event.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Jármű", event.vehicleId()));
        deadlineRepository.save(
                build(DeadlineType.VEHICLE_TAX, vehicle, null, DeadlineCalendar.nextVehicleTaxDate(today())));
    }

    @EventListener
    void onPolicyCreated(PolicyCreatedEvent event) {
        InsurancePolicy policy = policyRepository
                .findById(event.policyId())
                .orElseThrow(() -> new ResourceNotFoundException("Kötvény", event.policyId()));
        LocalDate today = today();
        if (policy.getPolicyType() == PolicyType.KGFB) {
            DeadlineRecurrence.withinPolicy(policy, DeadlineCalendar.nextAnniversary(policy.getStartDate(), today))
                    .ifPresent(anniversary ->
                            deadlineRepository.save(build(DeadlineType.KGFB_ANNIVERSARY, null, policy, anniversary)));
        }
        DeadlineRecurrence.withinPolicy(
                        policy,
                        DeadlineCalendar.nextPaymentDate(policy.getStartDate(), policy.getPaymentFrequency(), today))
                .ifPresent(due -> deadlineRepository.save(build(DeadlineType.INSURANCE_PAYMENT, null, policy, due)));
    }

    /** Számítható esedékesség, ha a kliens nem adott meg dátumot. Üres, ha a típusnál nincs ilyen szabály. */
    public Optional<LocalDate> defaultDueDate(DeadlineType type, InsurancePolicy policy) {
        LocalDate today = today();
        return switch (type) {
            case VEHICLE_TAX -> Optional.of(DeadlineCalendar.nextVehicleTaxDate(today));
            case MOTORWAY_VIGNETTE -> Optional.of(DeadlineCalendar.vignetteExpiry(today));
            case KGFB_ANNIVERSARY ->
                Optional.ofNullable(policy).map(p -> DeadlineCalendar.nextAnniversary(p.getStartDate(), today));
            case INSURANCE_PAYMENT ->
                Optional.ofNullable(policy)
                        .map(p -> DeadlineCalendar.nextPaymentDate(p.getStartDate(), p.getPaymentFrequency(), today));
            default -> Optional.empty();
        };
    }

    /**
     * A teljesített határidő következő előfordulása. Generált típusnál friss címet kap (pl. a KGFB
     * felmondási dátuma évente más), egyébként átveszi az előző címét és megjegyzését.
     */
    public Deadline nextOccurrence(Deadline completed, LocalDate nextDue) {
        Deadline next = new Deadline(
                completed.getHousehold(),
                completed.getType(),
                nextDue,
                completed.getVehicle(),
                completed.getMember(),
                completed.getPolicy());
        GeneratedText text = generatedText(completed.getType(), completed.getVehicle(), completed.getPolicy(), nextDue);
        if (text != null) {
            next.describe(text.title(), text.note());
        } else {
            next.describe(completed.getTitle(), completed.getNote());
        }
        return next;
    }

    /** Generált cím és megjegyzés, ha a típushoz van ilyen; különben {@code null}. */
    public GeneratedText generatedText(DeadlineType type, Vehicle vehicle, InsurancePolicy policy, LocalDate due) {
        return switch (type) {
            case VEHICLE_TAX -> new GeneratedText("Gépjárműadó – " + plate(vehicle, policy), null);
            case MOTORWAY_VIGNETTE ->
                new GeneratedText("Éves autópálya-matrica lejár – " + plate(vehicle, policy), null);
            case KGFB_ANNIVERSARY -> {
                LocalDate cancelBy = DeadlineCalendar.kgfbCancellationDeadline(due);
                yield new GeneratedText(
                        "KGFB-évforduló – felmondási határidő: " + cancelBy,
                        "A kötvény évfordulója %s. Felmondani legalább %d nappal előtte, legkésőbb %s-ig lehet; "
                                        .formatted(due, DeadlineCalendar.KGFB_CANCELLATION_DAYS, cancelBy)
                                + "felmondás nélkül a szerződés automatikusan folytatódik.");
            }
            case INSURANCE_PAYMENT ->
                new GeneratedText(
                        "Biztosítási díj – %s (%s)"
                                .formatted(
                                        policy.getPolicyType(),
                                        policy.getPaymentFrequency().displayName()),
                        null);
            default -> null;
        };
    }

    public record GeneratedText(String title, String note) {}

    private Deadline build(DeadlineType type, Vehicle vehicle, InsurancePolicy policy, LocalDate due) {
        Vehicle effectiveVehicle = vehicle != null ? vehicle : policy.getVehicle();
        Deadline deadline = new Deadline(effectiveVehicle.getHousehold(), type, due, effectiveVehicle, null, policy);
        GeneratedText text = generatedText(type, effectiveVehicle, policy, due);
        deadline.describe(text.title(), text.note());
        return deadline;
    }

    private static String plate(Vehicle vehicle, InsurancePolicy policy) {
        Vehicle v = vehicle != null ? vehicle : policy.getVehicle();
        return v.getLicensePlate();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
