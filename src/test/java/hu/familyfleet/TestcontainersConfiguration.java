package hu.familyfleet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Egyetlen, a teljes tesztfutás alatt közös PostgreSQL-konténer.
 *
 * <p>A Spring minden eltérő konfigurációjú tesztkontextusra ({@code @DataJpaTest}, {@code @SpringBootTest},
 * egyedi {@code @TestConfiguration}) új kontextust épít. Ha a konténer egy sima bean lenne, minden
 * kontextus saját konténert indítana. A statikus mező miatt a JVM-ben csak egy példány van, ezt
 * kapja meg minden kontextus. A konténert a Testcontainers "Ryuk" segédkonténere takarítja el a
 * JVM leállásakor; a Spring nem állítja le ({@code destroyMethod = ""}), különben az első lezárt
 * kontextus a többitől is elvenné.
 *
 * <p>Ára: a tesztosztályok közös adatbázist látnak. Ezért minden teszt saját adatot hoz létre
 * (új háztartás, egyedi rendszám, e-mail) és nem számol a tábla teljes tartalmára.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    static {
        POSTGRES.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
