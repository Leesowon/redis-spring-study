package com.udemy.redis_spring.fib.controller;

import com.udemy.redis_spring.fib.service.FibService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 피보나치 계산 REST API
 *
 * 엔드포인트:
 * - GET /fib/{index}: 피보나치 계산 (캐싱 적용)
 * - GET /fib/{index}/clear: 특정 인덱스 캐시 삭제
 */
@RestController
@RequestMapping("fib")
public class FibController {

    private static final Logger log = LoggerFactory.getLogger(FibController.class);

    @Autowired
    private FibService service;

    /**
     * 피보나치 값 조회
     *
     * 예: GET /fib/5
     * 응답: 5
     *
     * 캐시 동작:
     * - 첫 요청: Cache MISS → 계산 후 Redis에 저장
     * - 이후 요청: Cache HIT → Redis에서 즉시 반환
     */
    @GetMapping("{index}")
    public Mono<Integer> getFib(@PathVariable int index){
        log.info("📥 API Request - GET /fib/{}", index);

        return Mono.fromSupplier(() -> {
            int result = this.service.getFib(index);
            log.info("📤 API Response - fib({}) = {}", index, result);
            return result;
        });
    }

    /**
     * 특정 인덱스의 캐시 삭제
     *
     * 예: GET /fib/5/clear
     * 응답: 200 OK (빈 응답)
     *
     * 효과: 다음 /fib/5 요청 시 cache MISS 발생
     */
    @GetMapping("{index}/clear")
    public Mono<Void> clearCache(@PathVariable int index){
        log.info("🗑️  API Request - GET /fib/{}/clear (cache eviction)", index);

        return Mono.fromRunnable(() -> this.service.clearCache(index));
    }

}
