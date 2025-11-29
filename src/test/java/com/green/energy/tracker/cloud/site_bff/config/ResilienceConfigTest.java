package com.green.energy.tracker.cloud.site_bff.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilienceConfigTest {

    @Mock
    private ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Mock
    private RetryRegistry retryRegistry;

    @Mock
    private ReactiveCircuitBreaker mockCircuitBreaker;

    @Mock
    private Retry mockRetry;

    private ResilienceConfig resilienceConfig;

    @BeforeEach
    void setUp() {
        resilienceConfig = new ResilienceConfig(circuitBreakerFactory, retryRegistry);
    }

    @Test
    void circuitBreakerPubSub_shouldCreateCircuitBreakerWithCorrectName() {
        when(circuitBreakerFactory.create("pubsub")).thenReturn(mockCircuitBreaker);

        ReactiveCircuitBreaker result = resilienceConfig.circuitBreakerPubSub();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockCircuitBreaker);
        verify(circuitBreakerFactory).create("pubsub");
    }

    @Test
    void circuitBreakerFirestore_shouldCreateCircuitBreakerWithCorrectName() {
        when(circuitBreakerFactory.create("firestore")).thenReturn(mockCircuitBreaker);

        ReactiveCircuitBreaker result = resilienceConfig.circuitBreakerFirestore();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockCircuitBreaker);
        verify(circuitBreakerFactory).create("firestore");
    }

    @Test
    void retryPubSub_shouldCreateRetryWithCorrectName() {
        when(retryRegistry.retry("pubsub")).thenReturn(mockRetry);

        Retry result = resilienceConfig.retryPubSub();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockRetry);
        verify(retryRegistry).retry("pubsub");
    }

    @Test
    void retryFirestore_shouldCreateRetryWithCorrectName() {
        when(retryRegistry.retry("firestore")).thenReturn(mockRetry);

        Retry result = resilienceConfig.retryFirestore();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockRetry);
        verify(retryRegistry).retry("firestore");
    }

    @Test
    void retryCache_shouldCreateRetryWithCorrectName() {
        when(retryRegistry.retry("cache")).thenReturn(mockRetry);

        Retry result = resilienceConfig.retryCache();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockRetry);
        verify(retryRegistry).retry("cache");
    }

    @Test
    void allCircuitBreakers_shouldBeCreatedWithDifferentNames() {
        ReactiveCircuitBreaker cbPubSub = mockCircuitBreaker;
        ReactiveCircuitBreaker cbFirestore = mockCircuitBreaker;

        when(circuitBreakerFactory.create("pubsub")).thenReturn(cbPubSub);
        when(circuitBreakerFactory.create("firestore")).thenReturn(cbFirestore);

        ReactiveCircuitBreaker resultPubSub = resilienceConfig.circuitBreakerPubSub();
        ReactiveCircuitBreaker resultFirestore = resilienceConfig.circuitBreakerFirestore();

        assertThat(resultPubSub).isNotNull();
        assertThat(resultFirestore).isNotNull();
        verify(circuitBreakerFactory).create("pubsub");
        verify(circuitBreakerFactory).create("firestore");
    }

    @Test
    void allRetries_shouldBeCreatedWithDifferentNames() {
        Retry retryPubSub = mockRetry;
        Retry retryFirestore = mockRetry;
        Retry retryCache = mockRetry;

        when(retryRegistry.retry("pubsub")).thenReturn(retryPubSub);
        when(retryRegistry.retry("firestore")).thenReturn(retryFirestore);
        when(retryRegistry.retry("cache")).thenReturn(retryCache);

        Retry resultPubSub = resilienceConfig.retryPubSub();
        Retry resultFirestore = resilienceConfig.retryFirestore();
        Retry resultCache = resilienceConfig.retryCache();

        assertThat(resultPubSub).isNotNull();
        assertThat(resultFirestore).isNotNull();
        assertThat(resultCache).isNotNull();
        verify(retryRegistry).retry("pubsub");
        verify(retryRegistry).retry("firestore");
        verify(retryRegistry).retry("cache");
    }
}
