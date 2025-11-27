package com.green.energy.tracker.cloud.site_bff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * This test class is inspired by the user's example to create a pure unit test
 * for the main application class. It verifies that the main method correctly
 * calls SpringApplication.run() without actually starting the application context.
 */
@ExtendWith(MockitoExtension.class)
class SiteBffApplicationTest {

    @Test
    void main_shouldCallSpringApplicationRun() {
        // Arrange: We want to mock the static method SpringApplication.run()
        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            // We expect the run method to be called, and when it is, we return a mock context.
            mocked.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                  .thenReturn(mock(ConfigurableApplicationContext.class));

            // Act: Call the main method of our application
            SiteBffApplication.main(new String[]{"arg1", "arg2"});

            // Assert: Verify that SpringApplication.run was indeed called with the correct class and arguments.
            mocked.verify(() -> SpringApplication.run(SiteBffApplication.class, new String[]{"arg1", "arg2"}));
        }
    }
}
