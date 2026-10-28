package hu.familyfleet.household;

import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.household.dto.HouseholdRequest;
import hu.familyfleet.household.dto.HouseholdResponse;
import hu.familyfleet.household.dto.MemberRequest;
import hu.familyfleet.household.dto.MemberResponse;
import hu.familyfleet.security.AppUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Háztartások és családtagok kezelése. Alapból csak olvasó tranzakció; az író metódusok
 * felülírják ({@code readOnly = true} mellett a Hibernate kihagyja a dirty checkinget).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final HouseholdAccessRepository accessRepository;
    private final AppUserRepository userRepository;
    private final HouseholdAccessGuard accessGuard;

    /** A létrehozó automatikusan hozzáférést kap az új háztartáshoz. */
    @Transactional
    public HouseholdResponse create(HouseholdRequest request) {
        Household household = householdRepository.save(new Household(request.name()));
        // getReferenceById: nem tölti be a felhasználót, csak az idegen kulcshoz kell a proxy.
        accessRepository.save(
                new HouseholdAccess(userRepository.getReferenceById(accessGuard.currentUserId()), household));
        return HouseholdResponse.from(household);
    }

    /** Csak azok a háztartások, amelyekhez a bejelentkezett felhasználó hozzáfér. */
    public List<HouseholdResponse> findAll() {
        return householdRepository.findAccessibleBy(accessGuard.currentUserId(), Sort.by("name", "id")).stream()
                .map(HouseholdResponse::from)
                .toList();
    }

    public HouseholdResponse findById(Long id) {
        return HouseholdResponse.from(getHousehold(id));
    }

    /** Nincs szükség {@code save()}-re: a betöltött entitás managed, a változás commitkor kiíródik. */
    @Transactional
    public HouseholdResponse update(Long id, HouseholdRequest request) {
        Household household = getHousehold(id);
        household.rename(request.name());
        return HouseholdResponse.from(household);
    }

    @Transactional
    public MemberResponse addMember(Long householdId, MemberRequest request) {
        Household household = getHousehold(householdId);
        HouseholdMember member = memberRepository.save(new HouseholdMember(household, request.name(), request.email()));
        return MemberResponse.from(member);
    }

    /** Nem létező (vagy idegen) háztartásnál 404, nem üres lista: a kliens így meg tudja különböztetni a két esetet. */
    public List<MemberResponse> findMembers(Long householdId) {
        accessGuard.requireHousehold(householdId);
        return memberRepository.findByHouseholdIdOrderByNameAsc(householdId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    public MemberResponse findMember(Long householdId, Long memberId) {
        accessGuard.requireHousehold(householdId);
        return memberRepository
                .findByIdAndHouseholdId(memberId, householdId)
                .map(MemberResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Családtag", memberId));
    }

    private Household getHousehold(Long id) {
        accessGuard.requireHousehold(id);
        return householdRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Háztartás", id));
    }
}
