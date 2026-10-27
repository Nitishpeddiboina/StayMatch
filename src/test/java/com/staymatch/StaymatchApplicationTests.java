package com.staymatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * StaymatchApplicationTests is a Spring Boot integration test.
 *
 * @SpringBootTest tells Spring to load the full ApplicationContext
 * (all beans, configuration, DB connection) for this test.
 *
 * The contextLoads() test verifies that:
 *   - The application context starts without errors
 *   - application.yml is valid and readable
 *   - The DB connection is established
 *   - Flyway migrations run successfully
 *
 * If any of these fail, this test fails and mvn test reports an error.
 * This is the fastest way to confirm your configuration is correct.
 */
@SpringBootTest
class StaymatchApplicationTests {

    @Test
    void contextLoads() {
        // If the application context loads without throwing an exception,
        // this test passes. No assertion needed — the test itself is the assertion.
    }
}
