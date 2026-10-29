package hu.familyfleet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistrationPolicyTest {

    private static final InvitationLookup INVITED_ONLY_GUEST =
            email -> Set.of("guest@example.com").contains(email);

    @Test
    void openRegistrationAllowsAnyone() {
        RegistrationPolicy policy = new RegistrationPolicy(new RegistrationProperties(true, List.of()), email -> false);

        assertThat(policy.allows("anyone@example.com")).isTrue();
    }

    @Test
    void closedRegistrationAllowsListedEmailsCaseInsensitively() {
        RegistrationPolicy policy = new RegistrationPolicy(
                new RegistrationProperties(false, List.of(" Anna@Example.com ", "")), INVITED_ONLY_GUEST);

        assertThat(policy.allows("anna@example.com")).isTrue();
        assertThat(policy.allows("guest@example.com")).as("meghívott").isTrue();
        assertThat(policy.allows("stranger@example.com")).isFalse();
    }

    @Test
    void closedRegistrationWithoutListAllowsOnlyInvited() {
        RegistrationPolicy policy = new RegistrationPolicy(new RegistrationProperties(false, null), INVITED_ONLY_GUEST);

        assertThat(policy.allows("guest@example.com")).isTrue();
        assertThat(policy.allows("anna@example.com")).isFalse();
    }
}
