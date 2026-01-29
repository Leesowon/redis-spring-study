# 다중 서버 통신 테스트 가이드

서로 다른 포트의 서버 인스턴스가 Redis를 통해 통신하는 것을 테스트하는 방법입니다.

## 테스트 시나리오 개요

현재 chat 패키지는 **Redis Pub/Sub**을 사용하므로, 여러 서버 인스턴스가 같은 Redis를 바라보면 **서로 다른 포트의 서버들이 메시지를 공유**합니다.

```
Client A → Server:8080 ─┐
                         ├─> Redis (Pub/Sub)
Client B → Server:8081 ─┘

Client A가 메시지 전송 → Redis Topic 발행
    → Server:8080, 8081 모두 수신
    → 각 클라이언트에게 전달
```

---

## 1. 서로 다른 포트로 서버 실행

### 터미널 1 - 8080 포트 (기본)
```bash
mvn spring-boot:run
```
또는
```bash
java -jar target/redis-spring-0.0.1-SNAPSHOT.jar
```
**기본 포트**: `8080`

### 터미널 2 - 8081 포트
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```
또는
```bash
java -jar target/redis-spring-0.0.1-SNAPSHOT.jar --server.port=8081
```

### 터미널 3 - 8082 포트
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```
또는
```bash
java -jar target/redis-spring-0.0.1-SNAPSHOT.jar --server.port=8082
```

**참고**: 각 터미널을 별도로 열어서 실행해야 합니다.

---

## 2. 브라우저에서 채팅 테스트 (chat.html)

### 클라이언트 접속

**브라우저 탭 1**: `http://localhost:8080/chat.html`
- Name: `User-A`
- Room: `game`
- Start 클릭

**브라우저 탭 2**: `http://localhost:8081/chat.html`
- Name: `User-B`
- Room: `game`
- Start 클릭

**브라우저 탭 3**: `http://localhost:8082/chat.html`
- Name: `User-C`
- Room: `game`
- Start 클릭

**중요**: 같은 `room` 이름을 입력해야 메시지가 공유됩니다!

### 예상 동작

**User-A (8080)가 메시지 전송**: "안녕하세요"

```
User-A (8080) → WebSocket → Server:8080 → Redis Topic "game" 발행
                                              ↓
                    ┌─────────────────────────┼─────────────────────────┐
                    ↓                         ↓                         ↓
            Server:8080                 Server:8081                Server:8082
            (Topic 구독)                (Topic 구독)               (Topic 구독)
                    ↓                         ↓                         ↓
               User-A 수신                User-B 수신                User-C 수신
```

**모든 클라이언트에서 메시지 표시**:
- User-A (8080): "User-A: 안녕하세요" (본인)
- User-B (8081): "User-A: 안녕하세요" (다른 서버에서도 수신!)
- User-C (8082): "User-A: 안녕하세요" (다른 서버에서도 수신!)

---

## 3. 코드 동작 원리 (ChatRoomService.java)

### Subscribe 로직 (43-49줄)
```java
webSocketSession.receive()  // 클라이언트 메시지 수신
    .flatMap(msg -> list.add(msg).then(topic.publish(msg))) // Redis Topic 발행
    .subscribe();
```
- User-A가 메시지 전송 → Server:8080이 Redis Topic에 발행
- **Redis가 중앙 메시지 브로커 역할**

### Publisher 로직 (54-58줄)
```java
Flux<WebSocketMessage> flux = topic.getMessages(String.class) // Redis Topic 구독
    .startWith(list.iterator()) // 히스토리 먼저 전송
    .map(webSocketSession::textMessage)
```
- Server:8080, 8081, 8082 모두 같은 Redis Topic "game" 구독
- 누가 발행하든 **모든 서버가 메시지 수신**
- 각 서버는 자신에게 연결된 클라이언트에게 전송

---

## 4. fib 패키지 캐시 공유 테스트

서로 다른 포트의 서버가 **같은 Redis 캐시를 공유**하는지 확인합니다.

### 터미널에서 테스트

**터미널 4 - 8080 서버에 첫 요청**:
```bash
curl http://localhost:8080/fib/10
```

**예상 로그** (8080 서버):
```
INFO  - 📥 API Request - GET /fib/10
INFO  - ❌ Cache MISS - getFib(10) not in cache 'math:fib', executing method...
WARN  - ⚠️  EXECUTING getFib(10) - This means cache MISS occurred!
INFO  - 💻 Calculation completed: fib(10) = 55 (took 15ms)
INFO  - ✅ Calculated and cached in 17ms
INFO  - 📤 API Response - fib(10) = 55
```

**터미널 5 - 8081 서버에 같은 요청**:
```bash
curl http://localhost:8081/fib/10
```

**예상 로그** (8081 서버):
```
INFO  - 📥 API Request - GET /fib/10
INFO  - 🎯 Cache HIT  - getFib(10) from cache 'math:fib'
INFO  - ✅ Returned from cache in 3ms
INFO  - 📤 API Response - fib(10) = 55
```

**핵심**:
- 8080 서버가 계산한 결과를 Redis에 저장
- 8081 서버도 **같은 Redis 캐시 사용**
- 8081에서 즉시 캐시 HIT! (재계산 안 함)
- **서버 간 캐시 공유 확인!**

### 캐시 삭제 테스트

**8082 서버에서 캐시 삭제**:
```bash
curl http://localhost:8082/fib/10/clear
```

**예상 로그** (8082 서버):
```
INFO  - 🗑️  API Request - GET /fib/10/clear (cache eviction)
INFO  - 🗑️  Cache EVICTED - Removed fib(10) from cache 'math:fib'
```

**8080 또는 8081 서버에서 다시 요청**:
```bash
curl http://localhost:8080/fib/10
```

**예상 로그** (8080 서버):
```
INFO  - ❌ Cache MISS - getFib(10) not in cache 'math:fib', executing method...
```

**결과**: 8082에서 캐시를 삭제하면 **모든 서버에서 캐시가 삭제됨** (Redis 공유)

---

## 5. 서버 로그로 통신 확인

각 터미널에서 로그를 확인하면 어떤 서버에서 메시지를 처리하는지 알 수 있습니다.

### Server:8080 로그 예시
```
INFO  - WebSocket connected: /chat?room=game (Session: abc123)
INFO  - Subscribed to Redis Topic: game
INFO  - Publishing message to Redis: {"sender":"User-A", "message":"안녕"}
INFO  - Received from Redis Topic: {"sender":"User-A", "message":"안녕"}
INFO  - Received from Redis Topic: {"sender":"User-B", "message":"반가워"} ← 8081에서 발행!
```

### Server:8081 로그 예시
```
INFO  - WebSocket connected: /chat?room=game (Session: def456)
INFO  - Subscribed to Redis Topic: game
INFO  - Received from Redis Topic: {"sender":"User-A", "message":"안녕"} ← 8080에서 발행!
INFO  - Publishing message to Redis: {"sender":"User-B", "message":"반가워"}
INFO  - Received from Redis Topic: {"sender":"User-B", "message":"반가워"}
```

**확인 포인트**:
- 각 서버가 자신이 발행한 메시지도 수신 (Redis Topic 구독)
- 다른 서버가 발행한 메시지도 수신 (서버 간 통신!)

---

## 6. 핵심 개념

### 왜 서로 다른 서버끼리 통신이 가능한가?

**Redis가 중앙 메시지 브로커 역할**:

```
Server:8080 ─┐
Server:8081 ─┼─> Redis (10.11.12.13:6379) ← 모두 같은 Redis 사용
Server:8082 ─┘
```

- **chat 패키지**: Redis Pub/Sub으로 실시간 메시지 공유
- **fib 패키지**: Redis 캐시로 계산 결과 공유

### 같은 방(room)에 있어야 통신 가능 (chat)

**ChatRoomService.java:68-74**:
```java
private String getChatRoomName(WebSocketSession socketSession){
    URI uri = socketSession.getHandshakeInfo().getUri();
    return UriComponentsBuilder.fromUri(uri)
        .build()
        .getQueryParams()
        .toSingleValueMap()
        .getOrDefault("room", "default");
}
```

- `ws://localhost:8080/chat?room=game` → Redis Topic: "game"
- `ws://localhost:8081/chat?room=game` → Redis Topic: "game" (같은 방!)
- `ws://localhost:8082/chat?room=sports` → Redis Topic: "sports" (다른 방, 메시지 안 옴)

### Redis Topic과 Room의 관계

```
Room "game"   → Redis Topic "game"   → Server:8080, 8081, 8082 구독
Room "sports" → Redis Topic "sports" → Server:8080, 8082만 구독 (8081은 안 함)
```

각 방은 별도의 Redis Topic이므로, 같은 방에 있는 사용자끼리만 메시지 공유됩니다.

---

## 7. 실전 테스트 시나리오

### 시나리오 A: 3개 서버, 5명 사용자

**서버 실행**:
- Server:8080, 8081, 8082

**사용자 접속**:
- User-A → 8080, room "game"
- User-B → 8080, room "game"
- User-C → 8081, room "game"
- User-D → 8082, room "game"
- User-E → 8082, room "sports"

**메시지 전송**:
1. User-A: "안녕하세요" → User-A, B, C, D 모두 수신 (room "game")
2. User-C: "반갑습니다" → User-A, B, C, D 모두 수신 (room "game")
3. User-E: "스포츠 좋아하세요?" → User-E만 수신 (room "sports", 혼자)

### 시나리오 B: 캐시 공유 확인

**요청 순서**:
1. `curl http://localhost:8080/fib/35` → Cache MISS, 계산 (약 500ms)
2. `curl http://localhost:8081/fib/35` → Cache HIT, 즉시 반환 (약 3ms)
3. `curl http://localhost:8082/fib/35/clear` → 8082에서 캐시 삭제
4. `curl http://localhost:8080/fib/35` → Cache MISS, 재계산 (8082가 삭제한 영향)

**결론**: 모든 서버가 같은 Redis 캐시 저장소 공유

---

## 8. 테스트 체크리스트

### 사전 준비
- [ ] Redis 실행 중 확인: `application.properties`의 `spring.redis.host` 연결 가능
- [ ] 프로젝트 빌드: `mvn clean package`

### Chat 테스트
- [ ] 3개 터미널에서 서로 다른 포트로 서버 실행 (8080, 8081, 8082)
- [ ] 3개 브라우저 탭에서 각 서버에 접속
- [ ] 같은 room 이름 입력
- [ ] 한 탭에서 메시지 전송 → 모든 탭에서 수신 확인
- [ ] 각 서버 로그에서 Redis Topic 발행/구독 확인

### Fib 캐시 테스트
- [ ] 8080 서버에서 첫 요청 → Cache MISS 확인
- [ ] 8081 서버에서 같은 요청 → Cache HIT 확인
- [ ] 8082 서버에서 캐시 삭제
- [ ] 8080 또는 8081에서 재요청 → Cache MISS 확인

### 고급 테스트
- [ ] 서로 다른 room으로 메시지 격리 확인
- [ ] 서버 하나 종료 후 나머지 서버 통신 확인
- [ ] 히스토리 기능 확인 (새 클라이언트 접속 시 과거 메시지 수신)

---

## 9. 트러블슈팅

### 문제: 다른 서버의 메시지가 안 보임

**원인**:
- Redis 연결 문제
- 서로 다른 room 이름 입력

**확인**:
```bash
# Redis 연결 확인
redis-cli -h 10.11.12.13 -p 6379 ping
# 응답: PONG

# Redis에서 Topic 목록 확인
redis-cli -h 10.11.12.13 -p 6379 PUBSUB CHANNELS
```

### 문제: 캐시가 공유 안 됨

**원인**:
- 서로 다른 Redis 인스턴스 사용
- 캐시 키 충돌

**확인**:
```bash
# Redis에서 캐시 키 확인
redis-cli -h 10.11.12.13 -p 6379 KEYS "math:fib*"

# 특정 캐시 값 확인
redis-cli -h 10.11.12.13 -p 6379 HGETALL "math:fib"
```

### 문제: 포트 충돌

**증상**: `Address already in use` 에러

**해결**:
```bash
# Windows에서 포트 사용 확인
netstat -ano | findstr :8080

# 프로세스 종료
taskkill /PID <PID번호> /F
```

---

## 10. 마이크로서비스 아키텍처 시뮬레이션

이 테스트는 실제 **마이크로서비스 환경**을 로컬에서 시뮬레이션하는 것입니다.

### 실제 프로덕션 환경

```
Load Balancer (Nginx, AWS ELB)
         ↓
    ┌────┴────┬────────┬────────┐
    ↓         ↓        ↓        ↓
Server-1  Server-2  Server-3  Server-4
(Kubernetes Pod, Docker Container, EC2 Instance)
    └────┬────┴────────┴────────┘
         ↓
   Redis Cluster
   (AWS ElastiCache, Azure Cache for Redis)
```

### 로컬 테스트 환경

```
Browser Tabs (Client Load Simulation)
         ↓
    ┌────┴────┬────────┬────────┐
    ↓         ↓        ↓        ↓
Server:8080 Server:8081 Server:8082
(Terminal 1) (Terminal 2) (Terminal 3)
    └────┬────┴────────┴────────┘
         ↓
    Redis (10.11.12.13:6379)
```

**동일한 원리**:
- 클라이언트는 어느 서버에 연결되든 같은 경험
- Redis가 상태 공유 (Stateless 서버)
- 수평 확장 가능 (서버 추가/제거 자유로움)

---

## 정리

### 핵심 포인트

1. **Redis Pub/Sub**: 서버 간 실시간 메시지 공유 (chat 패키지)
2. **Redis Cache**: 서버 간 계산 결과 공유 (fib 패키지)
3. **서로 다른 포트 = 서로 다른 서버 시뮬레이션**
4. **같은 Redis 사용 = 서버 간 통신 가능**

### 테스트 순서

1. Redis 실행 중인지 확인
2. 여러 터미널에서 다른 포트로 서버 실행
3. 브라우저 여러 탭에서 각 서버에 접속
4. 같은 room 입력 후 채팅
5. 로그 확인 - Redis Pub/Sub 동작 확인
6. fib API로 캐시 공유 확인

이렇게 하면 **마이크로서비스 환경에서 Redis를 통한 서버 간 통신**을 로컬에서 테스트할 수 있습니다!
