package hu.familyfleet.security.dto;

/** OAuth2-szerű válasz: a kliens a tokent {@code Authorization: Bearer <accessToken>} fejlécben küldi. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
