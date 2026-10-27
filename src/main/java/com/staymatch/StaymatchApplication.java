package com.staymatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StaymatchApplication is the entry point of the Spring Boot application.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   - @Configuration       : marks this class as a source of bean definitions
 *   - @EnableAutoConfiguration : tells Spring Boot to automatically configure
 *                               beans based on the dependencies on the classpath
 *                               (e.g., sees PostgreSQL driver → auto-creates DataSource)
 *   - @ComponentScan       : scans this package and all sub-packages for
 *                               @Component, @Service, @Repository, @Controller, etc.
 *
 * SpringApplication.run() bootstraps the application:
 *   1. Creates the ApplicationContext (Spring's IoC container)
 *   2. Registers all beans
 *   3. Starts the embedded Tomcat server on port 8080
 *   4. Triggers Flyway to run pending DB migrations
 */
@SpringBootApplication
public class StaymatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaymatchApplication.class, args);
    }
}
