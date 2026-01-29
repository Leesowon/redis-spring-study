package com.udemy.redis_spring.fib.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Cache Hit/Miss 로깅을 위한 AOP Aspect
 *
 * === 동작 원리 ===
 *
 * Spring의 @Cacheable은 내부적으로 CacheInterceptor를 사용하여 동작:
 * 1. 메서드 호출 전: 캐시에서 값 조회 시도
 * 2. 캐시에 값이 있으면 (HIT): 메서드 실행 안 하고 캐시 값 반환
 * 3. 캐시에 값이 없으면 (MISS): 메서드 실행 후 결과를 캐시에 저장
 *
 * 이 Aspect는 @Cacheable 메서드 실행 "전에" 캐시를 먼저 확인하여
 * HIT/MISS를 판단하고 로그를 출력합니다.
 *
 * === AOP 실행 순서 ===
 *
 * Client 호출
 *   └─> @Around Advice 시작 (이 Aspect)
 *        ├─> CacheManager에서 캐시 확인
 *        │    ├─ 있으면: "Cache HIT" 로그
 *        │    └─ 없으면: "Cache MISS" 로그
 *        └─> proceedingJoinPoint.proceed() - 원래 메서드 실행
 *             └─> @Cacheable 로직 실행 (Spring CacheInterceptor)
 *                  ├─ 캐시 확인
 *                  ├─ 없으면 메서드 실행 (FibService.getFib)
 *                  │    └─> "calculating fib for X" 로그 (FibService:15)
 *                  └─> 결과 캐시 저장
 */
@Aspect
@Component
public class CacheLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheLoggingAspect.class);

    @Autowired
    private CacheManager cacheManager; // RedissonSpringCacheManager (RedissonCacheConfig에서 Bean 등록)

    /**
     * @Cacheable 메서드 실행 전후를 가로채서 캐시 hit/miss 로깅
     *
     * Pointcut 설명:
     * - @annotation(org.springframework.cache.annotation.Cacheable):
     *   @Cacheable 어노테이션이 붙은 메서드만 대상
     * - && @annotation(cacheable):
     *   어노테이션 정보를 cacheable 변수로 받음
     *
     * 실행 시점:
     * - @Around: 메서드 실행 전후를 모두 제어 가능
     * - proceed() 호출 전: 메서드 실행 전
     * - proceed() 호출: 실제 메서드 실행 (또는 Spring이 캐시에서 값 반환)
     * - proceed() 호출 후: 메서드 실행 후
     */
    @Around("@annotation(cacheable)")
    public Object logCacheAccess(
            ProceedingJoinPoint joinPoint,
            org.springframework.cache.annotation.Cacheable cacheable
    ) throws Throwable {

        // 1. 메서드 정보 추출
        String methodName = joinPoint.getSignature().getName(); // "getFib"
        Object[] args = joinPoint.getArgs(); // [5] - 메서드 파라미터

        // 2. @Cacheable 어노테이션에서 캐시 이름과 키 추출
        String cacheName = cacheable.value()[0]; // "math:fib"
        Object cacheKey = args[0]; // index 값 (첫 번째 파라미터)
        // 참고: 실제로는 SpEL "#index"를 평가해야 하지만, 단순히 첫 번째 파라미터 사용

        // 3. 캐시에서 값 조회 시도
        Cache cache = cacheManager.getCache(cacheName);
        boolean isCacheHit = false;

        if (cache != null) {
            Cache.ValueWrapper valueWrapper = cache.get(cacheKey);
            isCacheHit = (valueWrapper != null);
        }

        // 4. Hit/Miss 로그 출력
        long startTime = System.currentTimeMillis();

        if (isCacheHit) {
            log.info("🎯 Cache HIT  - {}({}) from cache '{}'", methodName, cacheKey, cacheName);
        } else {
            log.info("❌ Cache MISS - {}({}) not in cache '{}', executing method...",
                    methodName, cacheKey, cacheName);
        }

        // 5. 실제 메서드 실행 (또는 Spring이 캐시에서 반환)
        Object result = joinPoint.proceed();

        // 6. 실행 시간 측정 및 로그
        long executionTime = System.currentTimeMillis() - startTime;

        if (isCacheHit) {
            log.info("✅ Returned from cache in {}ms", executionTime);
        } else {
            log.info("✅ Calculated and cached in {}ms", executionTime);
        }

        return result;
    }

    /*
     * === 로그 예시 ===
     *
     * [첫 번째 요청 - Cache MISS]
     * INFO  - ❌ Cache MISS - getFib(5) not in cache 'math:fib', executing method...
     * calculating fib for 5          <-- FibService:15의 로그
     * INFO  - ✅ Calculated and cached in 342ms
     *
     * [두 번째 요청 - Cache HIT]
     * INFO  - 🎯 Cache HIT  - getFib(5) from cache 'math:fib'
     * INFO  - ✅ Returned from cache in 2ms
     *                                 <-- FibService:15의 로그 안 찍힘 (메서드 실행 안 됨)
     *
     * [캐시 삭제 후 - Cache MISS]
     * INFO  - clearing hash key      <-- FibService:22
     * INFO  - ❌ Cache MISS - getFib(5) not in cache 'math:fib', executing method...
     * calculating fib for 5
     * INFO  - ✅ Calculated and cached in 298ms
     *
     *
     * === 성능 차이 확인 ===
     *
     * - Cache MISS: 수백 ms (피보나치 계산 시간)
     * - Cache HIT: 수 ms (Redis에서 조회만)
     *
     * 예: fib(40)의 경우
     * - MISS: 3000ms+ (2^40 복잡도로 계산)
     * - HIT: 5ms (Redis에서 즉시 반환)
     *
     * 이렇게 실행 시간 차이로도 cache hit/miss를 간접적으로 확인 가능
     */
}
