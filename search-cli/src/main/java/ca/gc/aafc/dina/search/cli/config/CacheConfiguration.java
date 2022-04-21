package ca.gc.aafc.dina.search.cli.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@Log4j2
public class CacheConfiguration extends CachingConfigurerSupport {

  /**
   * Move to dina-base
   */
  public static class MethodBasedKeyGenerator implements KeyGenerator {
    public static final String GENERATOR_NAME = "methodBasedKeyGenerator";
    @Override
    public Object generate(Object target, Method method, Object... params) {
      final String key = target.getClass().getSimpleName() + "_" + method.getName() + "_"
          + StringUtils.arrayToDelimitedString(params, "_");

      log.debug("generated cache key: " + key);

      return key;
    }
  }

  private static final long CACHE_TIMEOUT_IN_MINUTE = 1;

  @Bean
  public Caffeine<Object, Object> caffeineConfig() {
    log.debug("cache timeout: " + CACHE_TIMEOUT_IN_MINUTE);
    return Caffeine
        .newBuilder()
        .expireAfterAccess(CACHE_TIMEOUT_IN_MINUTE, TimeUnit.MINUTES);
  }

  @Bean
  public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
    CaffeineCacheManager ccm = new CaffeineCacheManager();
    ccm.setCaffeine(caffeine);
    return ccm;
  }

  @Bean(name = MethodBasedKeyGenerator.GENERATOR_NAME)
  public KeyGenerator keyGenerator() {
    return new MethodBasedKeyGenerator();
  }

}
