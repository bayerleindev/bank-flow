package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PixKeyCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PixKeyCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public PixKeyCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.pix-key-cache.ttl}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public void store(PixKeyResponse response) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(response.endToEndId(), objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "pix key response cache serialization failed endToEndId={}",
                    response.endToEndId(),
                    exception);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "pix key response cache write failed endToEndId={}",
                    response.endToEndId(),
                    exception);
        }
    }

    public Optional<PixKeyResponse> findByEndToEndId(String endToEndId) {
        try {
            String value = redisTemplate.opsForValue().get(endToEndId);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, PixKeyResponse.class));
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "pix key response cache deserialization failed endToEndId={}",
                    endToEndId,
                    exception);
            throw new PixKeyValidationException("pix_key_cache_invalid", exception);
        } catch (DataAccessException exception) {
            LOGGER.warn("pix key response cache read failed endToEndId={}", endToEndId, exception);
            throw new PixKeyValidationException("pix_key_cache_unavailable", exception);
        }
    }
}
