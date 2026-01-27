package com.udemy.redis_spring;

import org.junit.jupiter.api.RepeatedTest;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
class RedisSpringApplicationTests {

	@Autowired
	// Redis와 리액티브(비동기/논블로킹) 방식으로 통신하는 템플릿
	private ReactiveStringRedisTemplate template;

	@Autowired
	private RedissonReactiveClient client;

	/**
	 * Redis 성능 측정을 위한 통합 테스트
	 * 50만번의 increment 연산을 수행하여 처리 시간 측정
	 *
	 */
	@RepeatedTest(3) // @RepeatedTest(3)으로 3번 반복하면서 워밍업 효과를 확인
	void springDataRedisTest() {
		ReactiveValueOperations<String, String> valueOperations = this.template.opsForValue();

		long before = System.currentTimeMillis();

		Mono<Void> mono = Flux.range(1, 500_000)
				.flatMap(i -> valueOperations.increment("user:1:visit")) // incr
				.then();

		StepVerifier.create(mono)
				.verifyComplete();

		long after = System.currentTimeMillis();

		System.out.println((after - before) + " ms");

	}

	@RepeatedTest(3)
	void redissonTest() {
		RAtomicLongReactive atomicLong = this.client.getAtomicLong("user:2:visit");
		long before = System.currentTimeMillis();
		Mono<Void> mono = Flux.range(1, 500_000)
				.flatMap(i -> atomicLong.incrementAndGet()) // incr
				.then();
		StepVerifier.create(mono)
				.verifyComplete();
		long after = System.currentTimeMillis();
		System.out.println((after - before) + " ms");
	}
}
