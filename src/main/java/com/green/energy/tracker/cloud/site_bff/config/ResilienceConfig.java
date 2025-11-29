package com.green.energy.tracker.cloud.site_bff.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;

    @Bean("cbPubSub")
    public ReactiveCircuitBreaker circuitBreakerPubSub(){
        return circuitBreakerFactory.create("pubsub");
    }

    @Bean("cbFirestore")
    public ReactiveCircuitBreaker circuitBreakerFirestore(){
        return circuitBreakerFactory.create("firestore");
    }

    @Bean("retryPubSub")
    public Retry retryPubSub() {
        return retryRegistry.retry("pubsub");
    }

    @Bean("retryFirestore")
    public Retry retryFirestore() {
        return retryRegistry.retry("firestore");
    }

    @Bean("retryCache")
    public Retry retryCache() {
        return retryRegistry.retry("cache");
    }

}
