package com.udemy.redis_spring.fib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FibService {

    private static final Logger log = LoggerFactory.getLogger(FibService.class);

    /**
     * 피보나치 계산 (캐싱 적용)
     *
     * @Cacheable 동작:
     * - 캐시 이름: "math:fib"
     * - 캐시 키: index 값 (예: 5 → Redis 키는 "math:fib::5")
     * - 캐시에 값이 있으면: 이 메서드 실행 안 됨 (아래 로그 안 찍힘)
     * - 캐시에 값이 없으면: 이 메서드 실행됨 (아래 로그 찍힘)
     *
     * 따라서 아래 로그가 찍히면 = Cache MISS
     */
    @Cacheable(value = "math:fib", key = "#index")
    public int getFib(int index){
        log.warn("⚠️  EXECUTING getFib({}) - This means cache MISS occurred!", index);
        log.info("💻 Starting fibonacci calculation for index {}...", index);
        long startTime = System.currentTimeMillis();

        int result = this.fib(index);

        long calculationTime = System.currentTimeMillis() - startTime;
        log.info("💻 Calculation completed: fib({}) = {} (took {}ms)", index, result, calculationTime);

        return result;
    }

    /**
     * 특정 인덱스의 캐시 삭제
     *
     * @CacheEvict: 캐시에서 특정 키 제거
     * - 다음 요청 시 cache MISS 발생 → 재계산됨
     */
    @CacheEvict(value = "math:fib", key = "#index")
    public void clearCache(int index){
        log.info("🗑️  Cache EVICTED - Removed fib({}) from cache 'math:fib'", index);
    }

    /**
     * 전체 캐시 삭제 (10초마다 자동 실행)
     *
     * @Scheduled: 스케줄러에 의해 주기적 실행
     * @CacheEvict(allEntries = true): 해당 캐시의 모든 항목 제거
     *
     * 목적:
     * - 메모리 관리 (오래된 캐시 정리)
     * - 캐시 성능 테스트 시 자동 초기화
     */
    @Scheduled(fixedRate = 10_000) // 10초
    @CacheEvict(value = "math:fib", allEntries = true)
    public void clearCache(){
        log.info("🗑️  SCHEDULED Cache CLEAR - All entries in 'math:fib' cache removed");
    }

    //intentional 2^N
    private int fib(int index) {
        if (index < 2)
            return index;
        return fib(index - 1) + fib(index - 2);
    }

}
