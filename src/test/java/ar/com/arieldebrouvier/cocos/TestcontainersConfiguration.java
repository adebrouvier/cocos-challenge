package ar.com.arieldebrouvier.cocos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts a throwaway Postgres in Docker for the tests and points the datasource at it ({@link ServiceConnection}).
 * Same image as docker-compose.yaml. Flyway migrates it (schema + challenge seed data) on
 * startup, and the container is removed when the tests finish.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6");
    }
}
