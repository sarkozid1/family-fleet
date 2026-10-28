package hu.familyfleet.security.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    /** A record alapértelmezett {@code toString}-je kiírná a jelszót (pl. egy debug logba). */
    @Override
    public String toString() {
        return "LoginRequest[email=%s]".formatted(email);
    }
}
