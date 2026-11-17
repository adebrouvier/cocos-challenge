package ar.com.arieldebrouvier.cocos.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Runs the application in Argentina time, whatever the host's time zone.
 * <p>
 * Timestamps stored without a time zone are Argentina wall-clock time: that's how the challenge data was written
 * (its times fall within BYMA trading hours, 11:00 to 17:00, only when read as Argentina time), so the application
 * writes new ones the same way.
 * <p>
 * Setting it explicitly also keeps the application independent of the host: the JDBC driver sends the JVM time zone
 * to Postgres when it connects, and Postgres rejects legacy aliases such as {@code America/Buenos_Aires}, which some
 * hosts report.
 * <p>
 * Registered in {@code META-INF/spring.factories}, so it runs at the start of every {@link SpringApplication},
 * before any connection is opened: the packaged app, {@code spring-boot:run}, and tests started from Maven or an
 * IDE alike.
 */
public class ApplicationTimeZone implements EnvironmentPostProcessor {

    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}
