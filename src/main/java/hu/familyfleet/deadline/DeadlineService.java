package hu.familyfleet.deadline;

import hu.familyfleet.common.exception.BusinessRuleException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.common.web.PageResponse;
import hu.familyfleet.deadline.dto.CompleteDeadlineRequest;
import hu.familyfleet.deadline.dto.CompleteDeadlineResponse;
import hu.familyfleet.deadline.dto.DeadlineCreateRequest;
import hu.familyfleet.deadline.dto.DeadlineResponse;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdAccessGuard;
import hu.familyfleet.household.HouseholdMember;
import hu.familyfleet.household.HouseholdMemberRepository;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.InsurancePolicyRepository;
import hu.familyfleet.vehicle.Vehicle;
import hu.familyfleet.vehicle.VehicleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeadlineService {

    private final DeadlineRepository deadlineRepository;
    private final HouseholdRepository householdRepository;
    private final VehicleRepository vehicleRepository;
    private final HouseholdMemberRepository memberRepository;
    private final InsurancePolicyRepository policyRepository;
    private final DeadlineGenerator generator;
    private final Clock clock;
    private final HouseholdAccessGuard accessGuard;

    @Transactional
    public DeadlineResponse create(Long householdId, DeadlineCreateRequest request) {
        accessGuard.requireHousehold(householdId);
        Household household = householdRepository
                .findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Háztartás", householdId));
        Vehicle vehicle =
                reference(household, request.vehicleId(), vehicleRepository::findById, Vehicle::getHousehold, "jármű");
        HouseholdMember member = reference(
                household, request.memberId(), memberRepository::findById, HouseholdMember::getHousehold, "családtag");
        InsurancePolicy policy = reference(
                household,
                request.policyId(),
                policyRepository::findById,
                p -> p.getVehicle().getHousehold(),
                "kötvény");

        LocalDate dueDate = Optional.ofNullable(request.dueDate())
                .or(() -> generator.defaultDueDate(request.type(), policy))
                .orElseThrow(() -> new BusinessRuleException(
                        "A(z) %s típusú határidőnél a dueDate megadása kötelező.".formatted(request.type())));

        // A konstruktor ellenőrzi a kötelező kapcsolatot és az azonos háztartást.
        Deadline deadline = new Deadline(household, request.type(), dueDate, vehicle, member, policy);
        // A kliens címe/megjegyzése elsőbbséget élvez; ha nincs, a generált szöveget használjuk (ha van).
        Optional<DeadlineGenerator.GeneratedText> generated =
                Optional.ofNullable(generator.generatedText(request.type(), deadline.getVehicle(), policy, dueDate));
        deadline.describe(
                Optional.ofNullable(request.title())
                        .or(() -> generated.map(DeadlineGenerator.GeneratedText::title))
                        .orElse(null),
                Optional.ofNullable(request.note())
                        .or(() -> generated.map(DeadlineGenerator.GeneratedText::note))
                        .orElse(null));
        return DeadlineResponse.from(deadlineRepository.save(deadline), today());
    }

    public PageResponse<DeadlineResponse> search(
            Long householdId,
            DeadlineStatus status,
            DeadlineType type,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        ensureHouseholdExists(householdId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("A 'from' dátum nem lehet későbbi a 'to' dátumnál.");
        }
        LocalDate today = today();
        return PageResponse.from(
                deadlineRepository.findAll(
                        DeadlineSpecifications.matching(householdId, status, type, from, to), pageable),
                deadline -> DeadlineResponse.from(deadline, today));
    }

    /** Nyitott határidők a következő {@code days} napban, a már lejártakkal együtt (azok a legsürgősebbek). */
    public List<DeadlineResponse> upcoming(Long householdId, int days) {
        ensureHouseholdExists(householdId);
        LocalDate today = today();
        return deadlineRepository.findOpenDueUntil(householdId, today.plusDays(days)).stream()
                .map(deadline -> DeadlineResponse.from(deadline, today))
                .toList();
    }

    public DeadlineResponse findById(Long id) {
        return DeadlineResponse.from(getDeadline(id), today());
    }

    /**
     * Teljesítés. Ismétlődő típusnál (vagy ha a kliens megadta a következő dátumot) ugyanabban a
     * tranzakcióban létrejön a következő határidő is.
     */
    @Transactional
    public CompleteDeadlineResponse complete(Long id, CompleteDeadlineRequest request) {
        Deadline deadline = getDeadline(id);
        LocalDate today = today();
        Optional<LocalDate> requestedNext = Optional.ofNullable(request).map(CompleteDeadlineRequest::nextDueDate);
        requestedNext.ifPresent(next -> {
            if (!next.isAfter(deadline.getDueDate())) {
                throw new BusinessRuleException("A következő esedékességnek (%s) a mostani (%s) után kell lennie."
                        .formatted(next, deadline.getDueDate()));
            }
        });

        deadline.markDone(clock.instant());
        Deadline next = requestedNext
                .or(() -> DeadlineRecurrence.nextDueDate(deadline, today))
                .map(nextDue -> deadlineRepository.save(generator.nextOccurrence(deadline, nextDue)))
                .orElse(null);

        deadlineRepository.flush();
        return new CompleteDeadlineResponse(
                DeadlineResponse.from(deadline, today), next == null ? null : DeadlineResponse.from(next, today));
    }

    @Transactional
    public DeadlineResponse dismiss(Long id) {
        Deadline deadline = getDeadline(id);
        deadline.dismiss();
        deadlineRepository.flush();
        return DeadlineResponse.from(deadline, today());
    }

    private Deadline getDeadline(Long id) {
        Deadline deadline =
                deadlineRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Határidő", id));
        accessGuard.requireAccess(deadline.getHousehold().getId(), "Határidő", id);
        return deadline;
    }

    private void ensureHouseholdExists(Long householdId) {
        accessGuard.requireHousehold(householdId);
    }

    /**
     * A törzsben hivatkozott elem: nem létező vagy másik háztartáshoz tartozó → ugyanaz a 400-as
     * üzenet, hogy egy idegen azonosító létezése ne derüljön ki (ADR-0003).
     */
    private static <T> T reference(
            Household household,
            Long id,
            Function<Long, Optional<T>> finder,
            Function<T, Household> householdOf,
            String name) {
        if (id == null) {
            return null;
        }
        return finder.apply(id)
                .filter(entity -> household.isSameAs(householdOf.apply(entity)))
                .orElseThrow(() -> new BusinessRuleException("Nem létező %s (id: %d).".formatted(name, id)));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
