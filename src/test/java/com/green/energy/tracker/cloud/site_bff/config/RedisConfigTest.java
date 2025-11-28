package com.green.energy.tracker.cloud.site_bff.config;

import com.green.energy.tracker.cloud.sitebff.web.model.GeoLocationDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private ReactiveRedisConnectionFactory connectionFactory;

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
    }

    @Test
    void redisTemplate_shouldCreateValidTemplate() {
        ReactiveRedisTemplate<String, SiteResponseDto> template = redisConfig.redisSiteResponseDtoTemplate(connectionFactory);

        assertNotNull(template);
        assertNotNull(template.getSerializationContext());
    }

    @Test
    void redisTemplate_shouldSerializeAndDeserializeSiteResponseDto() {
        ReactiveRedisTemplate<String, SiteResponseDto> template = redisConfig.redisSiteResponseDtoTemplate(connectionFactory);

        var siteResponseDto = createTestSiteResponseDto();
        var valueSerializer = template.getSerializationContext().getValueSerializationPair();

        byte[] serialized = valueSerializer.write(siteResponseDto).array();
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        SiteResponseDto deserialized = valueSerializer.read(ByteBuffer.wrap(serialized));
        assertNotNull(deserialized);
        assertEquals(siteResponseDto.getId(), deserialized.getId());
        assertEquals(siteResponseDto.getName(), deserialized.getName());
        assertEquals(siteResponseDto.getAddress(), deserialized.getAddress());
        assertEquals(siteResponseDto.getUserId(), deserialized.getUserId());
    }

    @Test
    void redisTemplate_shouldHandleJavaTimeTypes() {
        ReactiveRedisTemplate<String, SiteResponseDto> template = redisConfig.redisSiteResponseDtoTemplate(connectionFactory);

        var siteResponseDto = createTestSiteResponseDto();
        siteResponseDto.setCreatedAt(OffsetDateTime.now());
        siteResponseDto.setUpdatedAt(OffsetDateTime.now());

        var valueSerializer = template.getSerializationContext().getValueSerializationPair();

        byte[] serialized = valueSerializer.write(siteResponseDto).array();
        assertNotNull(serialized);

        SiteResponseDto deserialized = valueSerializer.read(ByteBuffer.wrap(serialized));
        assertNotNull(deserialized);
        assertNotNull(deserialized.getCreatedAt());
        assertNotNull(deserialized.getUpdatedAt());
    }

    @Test
    void redisTemplate_shouldSerializeStringKeys() {
        ReactiveRedisTemplate<String, SiteResponseDto> template = redisConfig.redisSiteResponseDtoTemplate(connectionFactory);

        String testKey = "site:" + UUID.randomUUID();
        var keySerializer = template.getSerializationContext().getKeySerializationPair();

        byte[] serialized = keySerializer.write(testKey).array();
        assertNotNull(serialized);

        String deserialized = keySerializer.read(ByteBuffer.wrap(serialized));
        assertEquals(testKey, deserialized);
    }

    @Test
    void redisTemplate_shouldUseProvidedConnectionFactory() {
        ReactiveRedisTemplate<String, SiteResponseDto> template = redisConfig.redisSiteResponseDtoTemplate(connectionFactory);

        assertNotNull(template);
        // Verify that the template uses the provided connection factory
        assertSame(connectionFactory, template.getConnectionFactory());
    }

    private SiteResponseDto createTestSiteResponseDto() {
        var siteResponseDto = new SiteResponseDto();
        siteResponseDto.setId(UUID.randomUUID());
        siteResponseDto.setName("Test Site");
        siteResponseDto.setAddress("123 Test Street");
        siteResponseDto.setUserId(UUID.randomUUID());

        var location = new GeoLocationDto();
        location.setLatitude(45.0);
        location.setLongitude(9.0);
        siteResponseDto.setLocation(location);

        return siteResponseDto;
    }
}
