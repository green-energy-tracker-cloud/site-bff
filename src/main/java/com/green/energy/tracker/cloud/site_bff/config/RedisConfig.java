package com.green.energy.tracker.cloud.site_bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, SiteResponseDto> redisSiteResponseDtoTemplate(ReactiveRedisConnectionFactory factory) {
        var keySerializer = new StringRedisSerializer();
        var objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        var valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, SiteResponseDto.class);
        var builder = RedisSerializationContext.<String, SiteResponseDto>newSerializationContext(keySerializer);
        var context = builder.value(valueSerializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, ListSitesResponseDto> redisListSitesResponseDtoTemplate(ReactiveRedisConnectionFactory factory) {
        var keySerializer = new StringRedisSerializer();
        var objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        var valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, ListSitesResponseDto.class);
        var builder = RedisSerializationContext.<String, ListSitesResponseDto>newSerializationContext(keySerializer);
        var context = builder.value(valueSerializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}
