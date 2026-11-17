package ar.com.arieldebrouvier.cocos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** The application's clock, in its time zone ({@link ApplicationTimeZone}). Inject it instead of calling now(). */
    @Bean
    public Clock clock() {
        return Clock.system(ApplicationTimeZone.ZONE);
    }
}
