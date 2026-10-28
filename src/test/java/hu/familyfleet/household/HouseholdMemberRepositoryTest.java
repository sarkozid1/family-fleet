package hu.familyfleet.household;

import static org.assertj.core.api.Assertions.assertThat;

import hu.familyfleet.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class HouseholdMemberRepositoryTest {

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private HouseholdMemberRepository memberRepository;

    @Test
    void findsOnlyMembersOfGivenHouseholdSortedByName() {
        Household kovacs = householdRepository.save(new Household("Kovács család"));
        Household nagy = householdRepository.save(new Household("Nagy család"));
        memberRepository.save(new HouseholdMember(kovacs, "Zoltán", null));
        memberRepository.save(new HouseholdMember(kovacs, "Anna", "anna@example.com"));
        memberRepository.save(new HouseholdMember(nagy, "Béla", null));

        assertThat(memberRepository.findByHouseholdIdOrderByNameAsc(kovacs.getId()))
                .extracting(HouseholdMember::getName)
                .containsExactly("Anna", "Zoltán");
    }

    @Test
    void memberIsNotFoundThroughAnotherHousehold() {
        Household kovacs = householdRepository.save(new Household("Kovács család"));
        Household nagy = householdRepository.save(new Household("Nagy család"));
        HouseholdMember anna = memberRepository.save(new HouseholdMember(kovacs, "Anna", null));

        assertThat(memberRepository.findByIdAndHouseholdId(anna.getId(), kovacs.getId()))
                .isPresent();
        assertThat(memberRepository.findByIdAndHouseholdId(anna.getId(), nagy.getId()))
                .isEmpty();
    }
}
