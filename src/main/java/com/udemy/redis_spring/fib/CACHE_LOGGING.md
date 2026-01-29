# Cache Hit/Miss 로깅 가이드

fib 패키지에 추가된 cache hit/miss 로깅 기능 설명입니다.

## 추가된 파일

```
fib/
├── aspect/
│   └── CacheLoggingAspect.java    # AOP 기반 캐시 로깅
├── controller/
│   └── FibController.java         # API 요청/응답 로그 추가
└── service/
    └── FibService.java            # 계산 실행 로그 추가
```

## 의존성 추가

`pom.xml`에 AOP 의존성 추가:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## 로그 구조

### 1. CacheLoggingAspect (aspect/CacheLoggingAspect.java)

**역할**: @Cacheable 메서드 실행 전에 캐시 존재 여부를 확인하여 HIT/MISS 판단

**동작 원리**:
```
Client 요청
  └─> CacheLoggingAspect (@Around)
       ├─> CacheManager에서 캐시 확인
       │    ├─ 있으면: "🎯 Cache HIT" 로그
       │    └─ 없으면: "❌ Cache MISS" 로그
       └─> proceed() - Spring의 @Cacheable 로직 실행
            ├─ 캐시에 있으면: 메서드 실행 안 함
            └─ 캐시에 없으면: FibService.getFib() 실행
                 └─> "⚠️ EXECUTING getFib" 로그
```

### 2. FibService (service/FibService.java:25-38)

**역할**: 실제 계산이 실행될 때만 로그 출력

**주요 로그**:
- `⚠️ EXECUTING getFib(X)` - 이 로그가 찍히면 = Cache MISS
- `💻 Starting fibonacci calculation` - 계산 시작
- `💻 Calculation completed` - 계산 완료 (소요 시간 포함)

### 3. FibController (controller/FibController.java:31-53)

**역할**: API 요청/응답 로그

**주요 로그**:
- `📥 API Request - GET /fib/{index}` - 요청 수신
- `📤 API Response - fib({index}) = {result}` - 응답 반환

---

## 테스트 시나리오 및 예상 로그

### 시나리오 1: 첫 요청 (Cache MISS)

**요청**:
```bash
curl http://localhost:8080/fib/10
```

**예상 로그**:
```
INFO  FibController - 📥 API Request - GET /fib/10
INFO  CacheLoggingAspect - ❌ Cache MISS - getFib(10) not in cache 'math:fib', executing method...
WARN  FibService - ⚠️  EXECUTING getFib(10) - This means cache MISS occurred!
INFO  FibService - 💻 Starting fibonacci calculation for index 10...
INFO  FibService - 💻 Calculation completed: fib(10) = 55 (took 15ms)
INFO  CacheLoggingAspect - ✅ Calculated and cached in 17ms
INFO  FibController - 📤 API Response - fib(10) = 55
```

**해석**:
1. API 요청 수신
2. 캐시에 없음 (MISS) 확인
3. 실제 메서드 실행 (`getFib(10)`)
4. 피보나치 계산 수행 (15ms 소요)
5. 결과를 캐시에 저장
6. 응답 반환

---

### 시나리오 2: 동일 요청 반복 (Cache HIT)

**요청**:
```bash
curl http://localhost:8080/fib/10
```

**예상 로그**:
```
INFO  FibController - 📥 API Request - GET /fib/10
INFO  CacheLoggingAspect - 🎯 Cache HIT  - getFib(10) from cache 'math:fib'
INFO  CacheLoggingAspect - ✅ Returned from cache in 3ms
INFO  FibController - 📤 API Response - fib(10) = 55
```

**해석**:
1. API 요청 수신
2. 캐시에 있음 (HIT) 확인
3. **`getFib(10)` 메서드 실행 안 됨** (⚠️ 로그 없음!)
4. Redis에서 즉시 값 반환 (3ms)
5. 응답 반환

**핵심 차이**:
- ⚠️ `EXECUTING getFib` 로그가 **안 찍힘** = 메서드가 실행되지 않음
- 실행 시간이 **15ms → 3ms로 단축** (약 5배 빠름)

---

### 시나리오 3: 캐시 삭제 후 재요청

**요청 1 - 캐시 삭제**:
```bash
curl http://localhost:8080/fib/10/clear
```

**예상 로그**:
```
INFO  FibController - 🗑️  API Request - GET /fib/10/clear (cache eviction)
INFO  FibService - 🗑️  Cache EVICTED - Removed fib(10) from cache 'math:fib'
```

**요청 2 - 삭제 후 조회**:
```bash
curl http://localhost:8080/fib/10
```

**예상 로그**:
```
INFO  FibController - 📥 API Request - GET /fib/10
INFO  CacheLoggingAspect - ❌ Cache MISS - getFib(10) not in cache 'math:fib', executing method...
WARN  FibService - ⚠️  EXECUTING getFib(10) - This means cache MISS occurred!
INFO  FibService - 💻 Starting fibonacci calculation for index 10...
INFO  FibService - 💻 Calculation completed: fib(10) = 55 (took 14ms)
INFO  CacheLoggingAspect - ✅ Calculated and cached in 16ms
INFO  FibController - 📤 API Response - fib(10) = 55
```

**해석**:
- 캐시 삭제로 인해 다시 Cache MISS 발생
- 재계산 후 다시 캐시에 저장

---

### 시나리오 4: 스케줄러에 의한 전체 캐시 삭제

**10초 경과 후 자동 실행** (FibService:48):

**예상 로그**:
```
INFO  FibService - 🗑️  SCHEDULED Cache CLEAR - All entries in 'math:fib' cache removed
```

**효과**:
- 모든 fib 캐시 삭제
- 다음 요청 시 모두 Cache MISS 발생

---

### 시나리오 5: 큰 숫자로 성능 차이 확인

**요청 - fib(40)**:
```bash
curl http://localhost:8080/fib/40
```

**첫 요청 (Cache MISS)**:
```
INFO  CacheLoggingAspect - ❌ Cache MISS - getFib(40) not in cache 'math:fib', executing method...
WARN  FibService - ⚠️  EXECUTING getFib(40) - This means cache MISS occurred!
INFO  FibService - 💻 Starting fibonacci calculation for index 40...
INFO  FibService - 💻 Calculation completed: fib(40) = 102334155 (took 3542ms)
INFO  CacheLoggingAspect - ✅ Calculated and cached in 3544ms
```

**두 번째 요청 (Cache HIT)**:
```
INFO  CacheLoggingAspect - 🎯 Cache HIT  - getFib(40) from cache 'math:fib'
INFO  CacheLoggingAspect - ✅ Returned from cache in 4ms
```

**성능 비교**:
- Cache MISS: **3542ms** (약 3.5초)
- Cache HIT: **4ms** (약 0.004초)
- **약 885배 빠름!**

---

## 로그 레벨별 의미

### INFO 로그
- 일반적인 캐시 HIT/MISS 정보
- API 요청/응답 정보
- 정상적인 동작 흐름

### WARN 로그
- `⚠️ EXECUTING getFib(X)` - 메서드가 실제로 실행됨
- 이 로그가 자주 나오면 캐시가 제대로 작동하지 않는 것

**정상 동작**:
- 첫 요청: WARN 로그 있음 (MISS)
- 이후 요청: WARN 로그 없음 (HIT)

**비정상 동작**:
- 매 요청마다 WARN 로그 발생
- → 캐시 설정 문제 또는 Redis 연결 문제

---

## 캐시 성능 지표 확인 방법

### 1. 실행 시간으로 확인

```
Cache MISS: Calculated and cached in XXXms (수백~수천 ms)
Cache HIT:  Returned from cache in Xms    (수 ms)
```

### 2. 로그 패턴으로 확인

**Cache MISS 패턴**:
```
❌ Cache MISS
⚠️ EXECUTING getFib
💻 Starting calculation
💻 Calculation completed
✅ Calculated and cached
```

**Cache HIT 패턴**:
```
🎯 Cache HIT
✅ Returned from cache
```

### 3. Redis 직접 확인

```bash
# Redis CLI로 캐시 키 확인
redis-cli KEYS "math:fib*"

# 특정 키 값 확인
redis-cli HGETALL "math:fib"
```

---

## 주의사항

### AOP 실행 순서

```
CacheLoggingAspect (@Around)
  └─> 캐시 확인 (CacheManager 사용)
       └─> Spring @Cacheable 로직
            └─> FibService.getFib() (캐시 없을 때만)
```

- `CacheLoggingAspect`가 **먼저** 캐시를 확인
- 그 다음 Spring의 `@Cacheable`이 실행
- 두 번 캐시를 확인하는 것처럼 보이지만, AOP는 로깅 목적

### 로그 중복 가능성

드물게 AOP의 캐시 확인과 Spring의 실제 캐시 확인 사이에 타이밍 이슈 발생 가능:
- AOP에서 MISS 판단 → Spring에서 HIT (다른 요청이 캐시 저장)
- 이 경우 "Cache MISS" 로그는 있지만 "EXECUTING" 로그 없음
- 실무에서는 거의 발생하지 않음

### 스케줄러 주의

`@Scheduled(fixedRate = 10_000)` - 10초마다 전체 캐시 삭제

- 테스트 목적으로는 유용
- 실무에서는 더 긴 주기 또는 제거 권장
- 필요 없으면 주석 처리:

```java
// @Scheduled(fixedRate = 10_000)
// @CacheEvict(value = "math:fib", allEntries = true)
// public void clearCache(){ ... }
```

---

## 정리

### 캐시 동작 확인 방법

1. **첫 요청**: ❌ Cache MISS → ⚠️ EXECUTING → 💻 Calculation → ✅ Cached (느림)
2. **반복 요청**: 🎯 Cache HIT → ✅ Returned (빠름)
3. **캐시 삭제 후**: 다시 MISS 패턴

### 핵심 로그

- `❌ Cache MISS` - 캐시에 없음
- `🎯 Cache HIT` - 캐시에 있음
- `⚠️ EXECUTING getFib` - 실제 계산 실행 (이 로그가 없으면 캐시에서 가져온 것)
- 실행 시간 차이 (수백 ms vs 수 ms)

이제 Redis 캐싱의 성능 향상을 로그로 명확하게 확인할 수 있습니다!
