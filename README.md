# Redis caching, chat application with WebSocket study

## Commit 1: sect 5: spring webflux caching
커밋 날짜: 2026-01-27

### 생성된 파일

#### 애플리케이션 소스 코드
- `src/main/java/com/udemy/redis_spring/RedisSpringApplication.java`
  - Spring Boot 메인 애플리케이션 클래스

#### Fibonacci 캐싱 기능
- `src/main/java/com/udemy/redis_spring/fib/config/RedissonCacheConfig.java`
  - Redisson 캐시 설정 클래스
- `src/main/java/com/udemy/redis_spring/fib/controller/FibController.java`
  - Fibonacci API 컨트롤러
- `src/main/java/com/udemy/redis_spring/fib/service/FibService.java`
  - Fibonacci 계산 및 캐싱 서비스

#### 설정 파일
- `src/main/resources/application.properties`
  - Spring Boot 애플리케이션 설정 (Redis 연결 정보 등)
- `src/main/resources/redisson.yml`
  - Redisson 클라이언트 설정

#### 테스트 파일
- `src/test/java/com/udemy/redis_spring/RedisSpringApplicationTests.java`
  - Spring Boot 애플리케이션 테스트 클래스

### 기능 요약
- Spring WebFlux 기반 프로젝트 초기 설정
- Redis를 활용한 캐싱 기능 구현
- Fibonacci 계산 결과를 Redis에 캐싱하여 성능 최적화

## Commit 2: sect 8: Chat Application With WebSocket

커밋 날짜 : 2026-01-28

### 생성된 파일

#### WebSocket 설정
- `src/main/java/com/udemy/redis_spring/chat/config/ChatRoomSocketConfig.java`
  - WebSocket 엔드포인트 설정 클래스
  - `/chat` 경로를 ChatRoomService와 매핑

#### 채팅 서비스
- `src/main/java/com/udemy/redis_spring/chat/service/ChatRoomService.java`
  - WebSocket 기반 채팅방 서비스
  - Redis Pub/Sub을 활용한 실시간 메시지 브로드캐스트
  - Redis List를 활용한 채팅 히스토리 저장
  - Subscribe 로직: 클라이언트 메시지 → Redis 저장 및 발행
  - Publisher 로직: Redis 메시지 → 클라이언트 전달

#### 프론트엔드
- `src/main/resources/static/chat.html`
  - 채팅 UI (Bootstrap 사용)
  - WebSocket 클라이언트 구현
  - 채팅방 입장 및 실시간 메시지 송수신 기능

### 기능 요약
- WebSocket 기반 실시간 채팅 애플리케이션 구현
- Redis Pub/Sub을 통한 멀티 서버 환경 지원
- 채팅 히스토리 기능 (나중에 입장한 사용자도 이전 메시지 확인 가능)
- 채팅방별 격리 (쿼리 파라미터로 방 선택)
