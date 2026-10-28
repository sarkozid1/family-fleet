package hu.familyfleet.common.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Az alkalmazás egyetlen időforrása. Mindenhol ezt injektáljuk ({@code LocalDate.now(clock)}),
 * így a tesztek rögzített időponttal futhatnak, és a "mai nap" a családi (budapesti) időzóna szerint számít.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock(@Value("${app.time-zone:Europe/Budapest}") ZoneId zone) {
        return Clock.system(zone);
    }
}
