package hu.familyfleet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Bejelentkezés e-mail + jelszó párossal (csak a {@code /api/auth/login} használja). */
@Configuration(proxyBeanMethods = false)
class AuthConfig {

    /**
     * {@code DelegatingPasswordEncoder}: alapból BCrypt, a hash elé írja az algoritmust ({@code {bcrypt}...}),
     * így később algoritmust lehet váltani a régi jelszavak érvénytelenítése nélkül.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return email -> users.findByEmail(AppUser.normalizeEmail(email))
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    /**
     * A {@code DaoAuthenticationProvider} nem létező felhasználónál is lefuttat egy jelszó-ellenőrzést,
     * így a válaszidőből nem derül ki, hogy az e-mail regisztrálva van-e; és mindkét esetben ugyanazt
     * a {@code BadCredentialsException}-t dobja.
     */
    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
