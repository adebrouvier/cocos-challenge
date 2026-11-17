package ar.com.arieldebrouvier.cocos;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for integration tests: full application + MockMvc against a Postgres started by Testcontainers
 * (see {@link TestcontainersConfiguration}).
 * <p>
 * The database is created and migrated (schema + challenge seed data) once, when the Spring context starts, and
 * every test runs in a transaction that is rolled back, so each test sees the original seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {
}
