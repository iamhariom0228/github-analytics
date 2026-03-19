package com.gitanalytics.shared.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Generic cache-aside repository.
 * <p>
 * All operations are fail-open on read (cache miss → call loader) and
 * fail-silent on write (log warning, return result anyway). This prevents
 * a Redis outage from taking down analytics endpoints.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Returns the cached value for {@code key}, or computes it via {@code loader},
     * stores it with the given TTL, and returns it.
     */
    @SuppressWarnings("unchecked")
    public <T> T cached(String key, long ttlSeconds, Supplier<T> loader) {
        try {
            Object v = redisTemplate.opsForValue().get(key);
            if (v != null) return (T) v;
        } catch (Exception e) {
            log.warn("Cache read failed [{}]: {}", key, e.getMessage());
        }

        T result = loader.get();

        try {
            if (result != null) {
                redisTemplate.opsForValue().set(key, result, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Cache write failed [{}]: {}", key, e.getMessage());
        }

        return result;
    }

    /** Removes a single cache entry. */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Cache evict failed [{}]: {}", key, e.getMessage());
        }
    }

    /** Removes all keys matching a glob pattern (e.g. {@code "ga:a:overview:*"}). */
    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted {} cache key(s) matching '{}'", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Cache evictByPattern failed [{}]: {}", pattern, e.getMessage());
        }
    }

    /**
     * Builds a namespaced cache key from parts, joining them with {@code :}.
     * Null parts are rendered as the string {@code "null"}.
     *
     * <pre>
     *   CacheRepository.key("overview", userId, login, from, to)
     *   // → "ga:a:overview:&lt;userId&gt;:&lt;login&gt;:&lt;from&gt;:&lt;to&gt;"
     * </pre>
     */
    public static String key(Object... parts) {
        StringBuilder sb = new StringBuilder("ga:a:");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(parts[i] == null ? "null" : parts[i]);
        }
        return sb.toString();
    }
}
