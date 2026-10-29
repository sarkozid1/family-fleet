package hu.familyfleet.security;

import hu.familyfleet.common.exception.ConflictException;
import hu.familyfleet.common.exception.ForbiddenException;
import hu.familyfleet.common.exception.ResourceNotFoundException;
import hu.familyfleet.security.dto.LoginRequest;
import hu.familyfleet.security.dto.RegisterRequest;
import hu.familyfleet.security.dto.TokenResponse;
import hu.familyfleet.security.dto.UserResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final CurrentUser currentUser;
    private final RegistrationPolicy registrationPolicy;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Önregisztráció mindig USER szerepkörrel; adminná csak adatbázisból lehet válni (ADR-0003).
     * A szabályt a foglalt e-mail ellenőrzése ELŐTT nézzük: egy kívülálló a 403-ból nem tudja meg,
     * hogy egy cím regisztrálva van-e.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        if (!registrationPolicy.allows(email)) {
            throw new ForbiddenException(
                    "Ezzel az e-mail-címmel nem lehet regisztrálni. Kérj meghívót egy családtagtól.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Ezzel az e-mail-címmel már regisztráltak.");
        }
        AppUser user = userRepository.save(
                new AppUser(email, passwordEncoder.encode(request.password()), request.displayName(), Role.USER));
        // A household csomag ugyanebben a tranzakcióban hozzáféréssé alakítja a címre szóló meghívókat.
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));
        return UserResponse.from(user);
    }

    /**
     * Hibás e-mailnél és hibás jelszónál ugyanaz a {@code BadCredentialsException} jön (401),
     * így a válaszból nem derül ki, hogy a cím regisztrálva van-e.
     */
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        AppUser user = userRepository
                .findByEmail(AppUser.normalizeEmail(request.email()))
                .orElseThrow();
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        // A kibocsátás pillanatából számolunk: egy második órahívás közben eltelt idő miatt 3599 jöhetne ki.
        long expiresIn = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds();
        return TokenResponse.bearer(token.value(), expiresIn);
    }

    public UserResponse me() {
        Long id = currentUser.id();
        return userRepository
                .findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Felhasználó", id));
    }
}
