package hu.familyfleet.reminder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * {@code app.reminder.*} beállítások, típusosan és induláskor validálva: egy elgépelt érték
 * már az alkalmazás indításakor kiderül, nem reggel 7-kor.
 *
 * @param enabled    a napi job be van-e kapcsolva
 * @param cron       Spring cron kifejezés (mp perc óra nap hónap hét napja)
 * @param daysBefore hány nappal az esedékesség előtt emlékeztessünk (pl. 30, 7, 1)
 * @param from       a feladó címe
 */
@Validated
@ConfigurationProperties(prefix = "app.reminder")
public record ReminderProperties(
        boolean enabled,
        @NotBlank String cron,
        @NotEmpty List<@Positive Integer> daysBefore,
        @NotBlank String from) {}
