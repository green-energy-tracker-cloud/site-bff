package com.green.energy.tracker.cloud.site_bff.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base class for all integration tests.
 * Uses the 'local' Spring profile to run tests with the same configuration as local development.
 * This ensures tests run against emulated services (Firestore, PubSub, Redis).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
public abstract class IntegrationTestBase {

    @Autowired
    protected WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Common setup for all integration tests can go here
    }
}
